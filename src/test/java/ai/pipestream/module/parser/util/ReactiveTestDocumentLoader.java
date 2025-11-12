package ai.pipestream.module.parser.util;

import com.google.protobuf.ByteString;
import ai.pipestream.data.v1.Blob;
import ai.pipestream.data.v1.BlobBag;
import ai.pipestream.data.v1.PipeDoc;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Reactive test document loader that properly handles both filesystem and JAR resources.
 * 
 * Key design principles:
 * 1. Emit resource REFERENCES (paths/names) first - lightweight objects
 * 2. Load actual content ONLY when the downstream subscriber requests it  
 * 3. Handle both filesystem (dev) and JAR (test/prod) contexts
 * 4. Provide backpressure support through reactive streams
 */
public class ReactiveTestDocumentLoader {
    private static final Logger LOG = Logger.getLogger(ReactiveTestDocumentLoader.class);
    
    /**
     * Represents a reference to a test resource without loading its content.
     * This lightweight object is what we emit in the stream.
     */
    public static class ResourceReference {
        public final String resourcePath;
        public final String filename;
        public final String category;
        public final Path filePath; // non-null when using external filesystem
        public final boolean external;
        
        public ResourceReference(String resourcePath, String filename, String category) {
            this(resourcePath, filename, category, null, false);
        }

        public ResourceReference(String resourcePath, String filename, String category, Path filePath, boolean external) {
            this.resourcePath = resourcePath;
            this.filename = filename;
            this.category = category;
            this.filePath = filePath;
            this.external = external;
        }
    }
    
    /**
     * Stream test documents from a resource directory.
     * This is the main entry point that:
     * 1. Emits resource references (lightweight)
     * 2. Transforms each reference to a PipeDoc ONLY when consumed
     * 
     * @param resourceDir The directory under test/resources to load from
     * @return A reactive Multi stream of PipeDoc instances
     */
    public static Multi<PipeDoc> streamTestDocuments(String resourceDir) {
        return streamResourceReferences(resourceDir)
                .onItem().transformToUniAndConcatenate(ref ->
                        loadDocumentFromReference(ref)
                                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                );
    }

    /**
     * Stream test documents from a resource directory, filtering by filename extension.
     * The extension comparison is case-insensitive and should include the leading dot (e.g., ".epub").
     */
    public static Multi<PipeDoc> streamTestDocumentsWithExtension(String resourceDir, String extension) {
        final String wanted = extension == null ? "" : extension.toLowerCase();
        return streamResourceReferences(resourceDir)
                .select().where(ref -> ref.filename != null && ref.filename.toLowerCase().endsWith(wanted))
                .onItem().transformToUniAndConcatenate(ref ->
                        loadDocumentFromReference(ref)
                                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                );
    }
    
    /**
     * Stream test documents with a limit for quick testing.
     */
    public static Multi<PipeDoc> streamTestDocuments(String resourceDir, int limit) {
        return streamTestDocuments(resourceDir)
                .select().first(limit);
    }
    
    /**
     * Stream test documents from a specific category.
     */
    public static Multi<PipeDoc> streamTestDocumentsByCategory(String category) {
        String path = "test-documents/" + category;
        return streamTestDocuments(path);
    }
    
    /**
     * Count available test documents without loading them.
     */
    public static Uni<Long> countTestDocuments(String resourceDir) {
        return streamResourceReferences(resourceDir)
                .collect().asList()
                .map(list -> (long) list.size());
    }
    
    /**
     * Emit resource references without loading content.
     * This is where we handle the filesystem vs JAR distinction.
     */
    private static Multi<ResourceReference> streamResourceReferences(String resourceDir) {
        return Multi.createFrom().<ResourceReference>emitter(emitter -> {
            // Run the blocking I/O operations on a worker thread
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // Prefer external directories if configured via env var or system property
                    Path external = resolveExternalResourceDir(resourceDir);
                    if (external != null) {
                        LOG.debugf("Resolved external path candidate for '%s': %s (exists: %s)",
                                resourceDir, external, Files.exists(external));
                    } else {
                        LOG.debugf("No external path candidate resolved for '%s' (env TEST_DOCUMENTS=%s, SAMPLE_DOC_TYPES=%s)",
                                resourceDir,
                                System.getenv("TEST_DOCUMENTS"),
                                System.getenv("SAMPLE_DOC_TYPES"));
                    }
                    if (external != null && Files.isDirectory(external)) {
                        LOG.infof("Using external test resources for '%s' at: %s", resourceDir, external);
                        emitExternalResourceReferences(external, resourceDir, emitter);
                        return;
                    }

                    URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource(resourceDir);
                    if (resourceUrl == null) {
                        LOG.warnf("Resource directory not found: %s", resourceDir);
                        emitter.complete();
                        return;
                    }
                    
                    URI uri = resourceUrl.toURI();
                    LOG.debugf("Loading resources from URI: %s (scheme: %s)", uri, uri.getScheme());
                    
                    if ("jar".equals(uri.getScheme())) {
                        // Handle JAR resources
                        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                            Path resourcePath = fileSystem.getPath(resourceDir);
                            emitResourceReferences(resourcePath, resourceDir, emitter);
                        }
                    } else {
                        // Handle filesystem resources
                        Path resourcePath = Paths.get(uri);
                        emitResourceReferences(resourcePath, resourceDir, emitter);
                    }
                    
                } catch (IOException | URISyntaxException e) {
                    LOG.errorf(e, "Failed to stream resources from %s", resourceDir);
                    emitter.fail(e);
                }
            });
        });
    }
    
    /**
     * Walk the path and emit ResourceReference objects (not the actual content).
     */
    private static void emitResourceReferences(Path basePath, String resourceDir,
                                              io.smallrye.mutiny.subscription.MultiEmitter<? super ResourceReference> emitter) {
        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(Files::isRegularFile)
                .sorted() // Ensure consistent ordering
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    
                    // Determine category from path structure
                    String category = "unknown";
                    Path parent = path.getParent();
                    if (parent != null && !parent.equals(basePath)) {
                        category = parent.getFileName().toString();
                    }
                    
                    // Build the full resource path for loading
                    Path relativePath = basePath.relativize(path);
                    String resourcePath = resourceDir + "/" + relativePath.toString().replace('\\', '/');
                    
                    // Emit the reference (lightweight object)
                    ResourceReference ref = new ResourceReference(resourcePath, filename, category);
                    emitter.emit(ref);
                    
                    LOG.tracef("Emitted reference: %s (category: %s)", resourcePath, category);
                });
            
            emitter.complete();
        } catch (IOException e) {
            emitter.fail(e);
        }
    }

    /**
     * Emit references for external filesystem resources.
     */
    private static void emitExternalResourceReferences(Path basePath, String logicalResourceDir,
                                              io.smallrye.mutiny.subscription.MultiEmitter<? super ResourceReference> emitter) {
        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(Files::isRegularFile)
                .sorted()
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    String category = "unknown";
                    Path parent = path.getParent();
                    if (parent != null && !parent.equals(basePath)) {
                        category = parent.getFileName().toString();
                    }
                    Path relativePath = basePath.relativize(path);
                    String displayPath = logicalResourceDir + "/" + relativePath.toString().replace('\\', '/');
                    ResourceReference ref = new ResourceReference(displayPath, filename, category, path, true);
                    emitter.emit(ref);
                    LOG.tracef("Emitted external reference: %s -> %s", displayPath, path);
                });
            emitter.complete();
        } catch (IOException e) {
            emitter.fail(e);
        }
    }
    
    /**
     * Load a PipeDoc from a ResourceReference.
     * This is called ONLY when the downstream subscriber is ready to process this item.
     */
    private static Uni<PipeDoc> loadDocumentFromReference(ResourceReference ref) {
        return Uni.createFrom().item(() -> {
            try {
                byte[] content;
                if (ref.external && ref.filePath != null) {
                    content = Files.readAllBytes(ref.filePath);
                } else {
                    // Load the resource content using the classloader
                    InputStream inputStream = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream(ref.resourcePath);
                    if (inputStream == null) {
                        throw new IOException("Could not load resource: " + ref.resourcePath);
                    }
                    try (inputStream) {
                        content = inputStream.readAllBytes();
                    }
                }
                
                // Determine MIME type from filename
                // Don't use Files.probeContentType() as it can cause SIS library issues
                String mimeType = guessMimeType(ref.filename);
                
                // Create a Blob with the new structure
                String docId = UUID.randomUUID().toString();
                Blob blob = Blob.newBuilder()
                        .setBlobId(docId + "-blob")
                        .setDriveId("test-drive")
                        .setData(ByteString.copyFrom(content))
                        .setMimeType(mimeType)
                        .setFilename(ref.filename)
                        .setSizeBytes(content.length)
                        .build();
                
                // Create PipeDoc with blob
                PipeDoc doc = PipeDoc.newBuilder()
                        .setDocId(docId)
                        .setBlobBag(BlobBag.newBuilder()
                                .setBlob(blob)
                                .build())
                        .build();
                
                LOG.debugf("Loaded document: %s (category: %s, mime: %s, size: %d)", 
                        ref.filename, ref.category, mimeType, content.length);
                
                return doc;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load document: " + ref.resourcePath, e);
            }
        });
    }
    
    /**
     * Simple MIME type guesser based on file extension.
     */
    private static String guessMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".tar")) return "application/x-tar";
        if (lower.endsWith(".gz")) return "application/gzip";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".java")) return "text/x-java-source";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        return "application/octet-stream";
    }
    
    /**
     * Progress tracker for processing documents.
     */
    public static class ProgressTracker {
        private final AtomicInteger processed = new AtomicInteger(0);
        private final AtomicInteger successful = new AtomicInteger(0);
        private final AtomicInteger failed = new AtomicInteger(0);
        private final int logInterval;
        private final String operation;
        
        public ProgressTracker(String operation, int logInterval) {
            this.operation = operation;
            this.logInterval = logInterval;
        }
        
        public void recordSuccess() {
            successful.incrementAndGet();
            int total = processed.incrementAndGet();
            if (total % logInterval == 0) {
                LOG.infof("%s progress: %d processed, %d successful, %d failed", 
                        operation, total, successful.get(), failed.get());
            }
        }
        
        public void recordFailure() {
            failed.incrementAndGet();
            int total = processed.incrementAndGet();
            if (total % logInterval == 0) {
                LOG.infof("%s progress: %d processed, %d successful, %d failed", 
                        operation, total, successful.get(), failed.get());
            }
        }
        
        public void logFinal() {
            LOG.infof("%s complete: %d processed, %d successful, %d failed", 
                    operation, processed.get(), successful.get(), failed.get());
        }
        
        public int getProcessed() { return processed.get(); }
        public int getSuccessful() { return successful.get(); }
        public int getFailed() { return failed.get(); }
    }

    /**
     * Resolve a resource directory name (e.g., "test-documents" or "sample_doc_types")
     * to an external filesystem path when configured. Two mechanisms supported:
     * - Environment variables: TEST_DOCUMENTS, SAMPLE_DOC_TYPES
     * - System properties: test.documents, sample.doc.types
     * If resourceDir starts with one of the known logical names, return the resolved base path
     * joined with the remaining subpath. Otherwise return null.
     */
    private static Path resolveExternalResourceDir(String resourceDir) {
        String normalized = resourceDir == null ? "" : resourceDir.replace('\\', '/');

        java.util.function.Function<String, String> prop = System::getProperty;
        java.util.function.Function<String, String> env = System::getenv;

        String testDocsRoot = firstNonBlank(
                env.apply("TEST_DOCUMENTS"),
                prop.apply("test.documents")
        );
        String sampleTypesRoot = firstNonBlank(
                env.apply("SAMPLE_DOC_TYPES"),
                prop.apply("sample.doc.types")
        );

        // Helper to infer default base directory without cloning
        java.util.function.Supplier<Path> inferDefaultBase = () -> {
            try {
                Path sibling = Paths.get("..", "sample-documents").normalize().toAbsolutePath();
                if (Files.isDirectory(sibling)) return sibling;
                Path tmp = Paths.get("/tmp", "sample-documents");
                if (Files.isDirectory(tmp)) return tmp.toAbsolutePath();
            } catch (Exception ignored) {
            }
            return null;
        };

        if (normalized.startsWith("test-documents")) {
            Path base = null;
            if (!isBlank(testDocsRoot)) {
                base = Paths.get(testDocsRoot);
            } else {
                Path inferred = inferDefaultBase.get();
                if (inferred != null && Files.isDirectory(inferred.resolve("test-documents"))) {
                    base = inferred.resolve("test-documents");
                    LOG.infof("Using inferred default TEST_DOCUMENTS at: %s", base);
                }
            }
            if (base == null) {
                LOG.debugf("TEST_DOCUMENTS not set and no inferred default found. Env TEST_DOCUMENTS=%s, system -Dtest.documents=%s",
                        testDocsRoot, System.getProperty("test.documents"));
            }
            if (base == null) return null;
            String remainder = normalized.length() == "test-documents".length() ? "" : normalized.substring("test-documents".length());
            remainder = remainder.startsWith("/") ? remainder.substring(1) : remainder;
            return remainder.isEmpty() ? base : base.resolve(remainder);
        }

        if (normalized.startsWith("sample_doc_types")) {
            Path base = null;
            if (!isBlank(sampleTypesRoot)) {
                base = Paths.get(sampleTypesRoot);
            } else {
                Path inferred = inferDefaultBase.get();
                if (inferred != null && Files.isDirectory(inferred.resolve("sample_doc_types"))) {
                    base = inferred.resolve("sample_doc_types");
                    LOG.infof("Using inferred default SAMPLE_DOC_TYPES at: %s", base);
                }
            }
            if (base == null) {
                LOG.debugf("SAMPLE_DOC_TYPES not set and no inferred default found. Env SAMPLE_DOC_TYPES=%s, system -Dsample.doc.types=%s",
                        sampleTypesRoot, System.getProperty("sample.doc.types"));
            }
            if (base == null) return null;
            String remainder = normalized.length() == "sample_doc_types".length() ? "" : normalized.substring("sample_doc_types".length());
            remainder = remainder.startsWith("/") ? remainder.substring(1) : remainder;
            return remainder.isEmpty() ? base : base.resolve(remainder);
        }

        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (!isBlank(v)) return v;
        }
        return null;
    }
}