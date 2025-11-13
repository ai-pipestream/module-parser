package ai.pipestream.module.parser.util;

import com.google.protobuf.ByteString;
import ai.pipestream.data.v1.Blob;
import ai.pipestream.data.v1.BlobBag;
import ai.pipestream.data.v1.PipeDoc;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
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
 * Reactive test document loader that streams files one at a time.
 * This avoids loading all test data into memory at once.
 * Supports loading from both JAR resources (default) and external filesystem (override).
 */
public class TestDocumentLoader {
    private static final Logger LOG = Logger.getLogger(TestDocumentLoader.class);

    /**
     * Represents a reference to a test resource without loading its content.
     */
    public static class ResourceReference {
        public final String resourcePath;
        public final String filename;
        public final Path filePath; // non-null when using external filesystem
        public final boolean external;

        public ResourceReference(String resourcePath, String filename) {
            this(resourcePath, filename, null, false);
        }

        public ResourceReference(String resourcePath, String filename, Path filePath, boolean external) {
            this.resourcePath = resourcePath;
            this.filename = filename;
            this.filePath = filePath;
            this.external = external;
        }
    }

    /**
     * Load a single document from a resource reference.
     */
    public static Uni<PipeDoc> loadDocument(ResourceReference ref) {
        return Uni.createFrom().item(() -> {
            try {
                byte[] content;
                if (ref.external && ref.filePath != null) {
                    // Load from external filesystem
                    content = Files.readAllBytes(ref.filePath);
                } else {
                    // Load from JAR resource using classloader
                    InputStream inputStream = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream(ref.resourcePath);
                    if (inputStream == null) {
                        throw new IOException("Could not load resource: " + ref.resourcePath);
                    }
                    try (inputStream) {
                        content = inputStream.readAllBytes();
                    }
                }

                // Don't use Files.probeContentType() as it can cause SIS library issues in tests
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

                LOG.debugf("Loaded document: %s (mime: %s, size: %d)",
                        ref.filename, mimeType, content.length);

                return doc;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load document: " + ref.resourcePath, e);
            }
        });
    }
    
    /**
     * Stream all test documents from a resource directory.
     * Documents are loaded one at a time as the stream is consumed.
     * Works with both filesystem resources (dev mode) and JAR resources (test/prod mode).
     *
     * @param resourceDir The directory under test/resources to load from
     * @return A reactive Multi stream of PipeDoc instances
     */
    public static Multi<PipeDoc> streamTestDocuments(String resourceDir) {
        // First stream the resource references, then transform each to a document
        return streamResourceReferences(resourceDir)
                .onItem().transformToUniAndConcatenate(TestDocumentLoader::loadDocument);
    }

    /**
     * Reactively streams resource references within a given directory.
     * Handles resources in both filesystem (dev mode) and JAR (test/prod mode).
     *
     * @param resourceDir The resource directory path
     * @return A Multi<ResourceReference> that emits each resource reference
     */
    private static Multi<ResourceReference> streamResourceReferences(String resourceDir) {
        return Multi.createFrom().emitter(emitter -> {
            try {
                // Prefer external directories if configured via env var or system property
                Path external = resolveExternalResourceDir(resourceDir);
                if (external != null && Files.isDirectory(external)) {
                    LOG.infof("Using external test resources for '%s' at: %s", resourceDir, external);
                    walkAndEmitExternal(external, resourceDir, emitter);
                    return;
                }

                URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource(resourceDir);
                if (resourceUrl == null) {
                    LOG.warnf("Resource directory not found (classpath): %s", resourceDir);
                    emitter.complete();
                    return;
                }

                URI uri = resourceUrl.toURI();

                // Handle JAR vs filesystem resources
                if ("jar".equals(uri.getScheme())) {
                    // For JARs, we need to open a FileSystem to walk the contents
                    try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                        Path resourcePath = fileSystem.getPath(resourceDir);
                        walkAndEmitJar(resourcePath, resourceDir, emitter);
                    }
                } else {
                    // For regular filesystem in classpath, treat as JAR (use resource paths)
                    Path resourcePath = Paths.get(uri);
                    walkAndEmitJar(resourcePath, resourceDir, emitter);
                }

            } catch (IOException | URISyntaxException e) {
                LOG.errorf(e, "Failed to stream resources from %s", resourceDir);
                emitter.fail(e);
            }
        });
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

        // Helpers to read env or property
        java.util.function.Function<String, String> prop = System::getProperty;
        java.util.function.Function<String, String> env = System::getenv;

        // Known roots and their env/prop mapping
        String testDocsRoot = firstNonBlank(
                env.apply("TEST_DOCUMENTS"),
                prop.apply("test.documents")
        );
        String sampleTypesRoot = firstNonBlank(
                env.apply("SAMPLE_DOC_TYPES"),
                prop.apply("sample.doc.types")
        );

        if (normalized.startsWith("test-documents")) {
            if (isBlank(testDocsRoot)) return null;
            Path base = Paths.get(testDocsRoot);
            String remainder = normalized.length() == "test-documents".length() ? "" : normalized.substring("test-documents".length());
            remainder = remainder.startsWith("/") ? remainder.substring(1) : remainder;
            return remainder.isEmpty() ? base : base.resolve(remainder);
        }

        if (normalized.startsWith("sample_doc_types")) {
            if (isBlank(sampleTypesRoot)) return null;
            Path base = Paths.get(sampleTypesRoot);
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
    
    /**
     * Walk external filesystem path and emit ResourceReference objects for external files.
     */
    private static void walkAndEmitExternal(Path basePath, String logicalResourceDir,
                                            io.smallrye.mutiny.subscription.MultiEmitter<? super ResourceReference> emitter) {
        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(Files::isRegularFile)
                .sorted() // Ensure consistent ordering
                .forEach(filePath -> {
                    String filename = filePath.getFileName().toString();
                    Path relativePath = basePath.relativize(filePath);
                    String displayPath = logicalResourceDir + "/" + relativePath.toString().replace('\\', '/');
                    ResourceReference ref = new ResourceReference(displayPath, filename, filePath, true);
                    emitter.emit(ref);
                    LOG.tracef("Emitted external reference: %s -> %s", displayPath, filePath);
                });
            emitter.complete();
        } catch (IOException e) {
            emitter.fail(e);
        }
    }

    /**
     * Walk JAR/classpath path and emit ResourceReference objects for JAR resources.
     */
    private static void walkAndEmitJar(Path basePath, String resourceDir,
                                       io.smallrye.mutiny.subscription.MultiEmitter<? super ResourceReference> emitter) {
        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(Files::isRegularFile)
                .sorted() // Ensure consistent ordering
                .forEach(filePath -> {
                    String filename = filePath.getFileName().toString();
                    // Build the full resource path for loading via classloader
                    Path relativePath = basePath.relativize(filePath);
                    String resourcePath = resourceDir + "/" + relativePath.toString().replace('\\', '/');
                    ResourceReference ref = new ResourceReference(resourcePath, filename);
                    emitter.emit(ref);
                    LOG.tracef("Emitted JAR reference: %s", resourcePath);
                });
            emitter.complete();
        } catch (IOException e) {
            emitter.fail(e);
        }
    }
    
    /**
     * Stream test documents from a specific category.
     * Categories correspond to directories at the root of the test-documents JAR.
     *
     * @param category The category directory (e.g., "sample_text", "sample_office_files")
     * @return A reactive Multi stream of PipeDoc instances
     */
    public static Multi<PipeDoc> streamTestDocumentsByCategory(String category) {
        // Resources are at root level in the JAR, not under "test-documents/" prefix
        return streamTestDocuments(category);
    }
    
    /**
     * Stream a limited number of test documents for quick testing.
     * 
     * @param resourceDir The directory to load from
     * @param limit Maximum number of documents to load
     * @return A reactive Multi stream of PipeDoc instances
     */
    public static Multi<PipeDoc> streamTestDocuments(String resourceDir, int limit) {
        return streamTestDocuments(resourceDir)
                .select().first(limit);
    }
    
    /**
     * Get a count of available test documents without loading them.
     *
     * @param resourceDir The directory to count files in
     * @return Uni with the count of files
     */
    public static Uni<Long> countTestDocuments(String resourceDir) {
        return streamResourceReferences(resourceDir)
                .collect().asList()
                .map(list -> (long) list.size());
    }
    
    /**
     * Simple MIME type guesser based on file extension.
     * Avoids using Files.probeContentType() which can cause SIS library issues.
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
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
    
    /**
     * Create a progress tracker for processing documents.
     * Logs progress at specified intervals.
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
}