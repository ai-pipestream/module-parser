package ai.pipestream.module.parser;

import ai.pipestream.common.service.SchemaExtractorService;
import ai.pipestream.common.util.ProcessingBuffer;
import ai.pipestream.data.module.*;
import ai.pipestream.data.v1.Blob;
import ai.pipestream.data.v1.DocOutline;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.SearchMetadata;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import ai.pipestream.module.parser.config.ParserConfig;
import ai.pipestream.module.parser.util.DocumentParser;
import ai.pipestream.module.parser.tika.TikaMetadataExtractor;
import com.google.protobuf.Any;
import ai.pipestream.module.parser.schema.SchemaEnhancer;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import ai.pipestream.shaded.tika.metadata.Metadata;
import ai.pipestream.shaded.tika.parser.AutoDetectParser;
import ai.pipestream.shaded.tika.parser.ParseContext;
import ai.pipestream.shaded.tika.parser.Parser;

import static ai.pipestream.data.v1.Blob.ContentCase.CONTENT_NOT_SET;
import static ai.pipestream.data.v1.Blob.ContentCase.STORAGE_REF;

@GrpcService // Marks this as a gRPC service that Quarkus will expose
@Singleton // Ensures only one instance is created
public class ParserServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(ParserServiceImpl.class);

    @Inject
    @Named("parserOutputBuffer")
    ProcessingBuffer<PipeDoc> outputBuffer;
    
    @Inject
    ObjectMapper objectMapper;

    @Inject
    SchemaExtractorService schemaExtractorService;
    
    @Inject
    SchemaEnhancer schemaEnhancer;

    @Inject
    DocumentParser documentParser;

    @Override
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        LOG.debugf("Parser service received document: %s", 
                 request.hasDocument() ? request.getDocument().getDocId() : "no document");

        return Uni.createFrom().item(() -> {
            try {
                ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder()
                        .setSuccess(true);

                if (request.hasDocument()) {
                    // Extract configuration from request
                    ParserConfig config = extractConfiguration(request);

                    // Check if document has blob data to parse
                    if (request.getDocument().hasBlobBag() && request.getDocument().getBlobBag().hasBlob()) {
                        Blob blob = request.getDocument().getBlobBag().getBlob();
                        
                        // Extract data based on content type
                        com.google.protobuf.ByteString blobData = null;
                        switch (blob.getContentCase()) {
                            case DATA:
                                blobData = blob.getData();
                                break;
                            case STORAGE_REF:
                                // TODO: Implement S3 fetching
                                LOG.warn("S3 storage references not yet implemented in parser");
                                responseBuilder.setOutputDoc(request.getDocument())
                                        .addProcessorLogs("Parser service cannot yet handle S3 storage references");
                                return responseBuilder.build();
                            case CONTENT_NOT_SET:
                                LOG.debug("Blob has no content set - passing through");
                                responseBuilder.setOutputDoc(request.getDocument())
                                        .addProcessorLogs("Parser service received blob with no content - passing through unchanged");
                                return responseBuilder.build();
                        }
                        
                        if (blobData != null && !blobData.isEmpty()) {
                            // Get filename from document blob metadata if available
                            String filename = null;
                            if (blob.hasFilename()) {
                                filename = blob.getFilename();
                            }

                            LOG.debugf("Processing document with filename: %s, config ID: %s", 
                                     filename, config.configId());

                            // If font, bypass DocumentParser to avoid archive detection; build minimal PipeDoc
                            boolean isFontFile = (filename != null && filename.toLowerCase().matches(".*\\.(ttf|ttc|otf|woff2?|pfa|pfb)$"))
                                    || (blob.hasMimeType() && blob.getMimeType().toLowerCase().startsWith("font/"));
                            PipeDoc parsedDoc;
                            LOG.debugf("FONT BYPASS CHECK: filename=%s, mime=%s, isFontFile=%s", filename, blob.hasMimeType() ? blob.getMimeType() : "", isFontFile);
                            if (isFontFile) {
                                String title = filename;
                                int dotIdx = filename.lastIndexOf('.');
                                if (dotIdx > 0) {
                                    title = filename.substring(0, dotIdx);
                                }
                                SearchMetadata sm = SearchMetadata.newBuilder()
                                        .setTitle(title)
                                        .setBody("")
                                        .build();
                                parsedDoc = PipeDoc.newBuilder()
                                        .setSearchMetadata(sm)
                                        .build();
                            } else {
                                // Parse the document using Tika
                                parsedDoc = documentParser.parseDocument(
                                    blobData,
                                    config,
                                    filename
                                );
                            }

                            // Optionally build comprehensive TikaResponse and pack into structured_data
                            PipeDoc.Builder outputDocBuilder = parsedDoc.toBuilder()
                                    .setDocId(request.getDocument().getDocId());

                            if (shouldExtractComprehensiveMetadata(config)) {
                                try {
                                    TikaResponse tikaResponse = extractTikaResponse(blobData, filename,
                                            parsedDoc.getSearchMetadata().getBody(), request.getDocument().getDocId());
                                    Any tikaAny = Any.pack(tikaResponse);
                                    outputDocBuilder.setStructuredData(tikaAny);

                                    // If EPUB, populate SearchMetadata.doc_outline from EPUB TOC
                                    try {
                                        if (tikaResponse.hasEpub() && tikaResponse.getEpub().getTableOfContentsCount() > 0) {
                                            ai.pipestream.data.v1.DocOutline outline = ai.pipestream.module.parser.tika.builders.EpubStructureExtractor
                                                    .buildDocOutlineFromToc(tikaResponse.getEpub().getTableOfContentsList());
                                            ai.pipestream.data.v1.SearchMetadata sm = parsedDoc.getSearchMetadata().toBuilder()
                                                    .setDocOutline(outline)
                                                    .build();
                                            outputDocBuilder.setSearchMetadata(sm);
                                        }
                                    } catch (Exception ignored) {}

                                    // If PDF, try to extract bookmarks into doc_outline
                                    try {
                                        if (tikaResponse.hasPdf() && (filename != null && filename.toLowerCase().endsWith(".pdf"))) {
                                            byte[] bytes = blobData.toByteArray();
                                            ai.pipestream.data.v1.DocOutline outline = ai.pipestream.module.parser.tika.builders.PdfOutlineExtractor
                                                    .buildDocOutlineFromPdf(bytes);
                                            if (outline.getSectionsCount() > 0) {
                                                ai.pipestream.data.v1.SearchMetadata sm = parsedDoc.getSearchMetadata().toBuilder()
                                                        .setDocOutline(outline)
                                                        .build();
                                                outputDocBuilder.setSearchMetadata(sm);
                                            }
                                        }
                                    } catch (Exception ignored) {}

                                    // If Markdown, build outline and links via CommonMark
                                    try {
                                        boolean mdEnabled = config.outlineExtraction() == null || Boolean.TRUE.equals(config.outlineExtraction().enableMarkdownOutline());
                                        if (mdEnabled && filename != null && filename.toLowerCase().endsWith(".md")) {
                                            byte[] bytes = parsedDoc.getSearchMetadata().getBody().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                            DocOutline outline = ai.pipestream.module.parser.tika.builders.MarkdownExtractor
                                                    .buildDocOutlineFromMarkdown(bytes, 1, 6, true);
                                            java.util.List<ai.pipestream.data.v1.LinkReference> links = ai.pipestream.module.parser.tika.builders.MarkdownExtractor
                                                    .extractLinks(bytes, parsedDoc.getSearchMetadata().getSourceUri());
                                            // Mark is_external relative to source_uri
                                            try {
                                                String src = parsedDoc.getSearchMetadata().getSourceUri();
                                                java.net.URI base = (src != null && !src.isEmpty()) ? new java.net.URI(src) : null;
                                                if (base != null && base.getHost() != null) {
                                                    String host = base.getHost();
                                                    java.util.List<ai.pipestream.data.v1.LinkReference> adjusted = new java.util.ArrayList<>(links.size());
                                                    for (ai.pipestream.data.v1.LinkReference lr : links) {
                                                        boolean external = false;
                                                        try {
                                                            java.net.URI u = new java.net.URI(lr.getUrl());
                                                            String h = u.getHost();
                                                            external = (h != null && !h.equalsIgnoreCase(host));
                                                        } catch (Exception ignored3) {}
                                                        adjusted.add(lr.toBuilder().setIsExternal(external).build());
                                                    }
                                                    links = adjusted;
                                                }
                                            } catch (Exception ignored3) {}

                                            ai.pipestream.data.v1.SearchMetadata.Builder smb = outputDocBuilder.hasSearchMetadata()
                                                    ? outputDocBuilder.getSearchMetadata().toBuilder()
                                                    : parsedDoc.getSearchMetadata().toBuilder();
                                            if (outline.getSectionsCount() > 0) smb.setDocOutline(outline);
                                            if (!links.isEmpty()) {
                                                smb.clearDiscoveredLinks();
                                                smb.addAllDiscoveredLinks(links);
                                            }
                                            outputDocBuilder.setSearchMetadata(smb.build());
                                        }
                                    } catch (Exception ignored) {}

                                    // If HTML, optionally populate SearchMetadata.doc_outline from headings
                                    try {
                                        if (tikaResponse.hasHtml()) {
                                            byte[] htmlBytes = blobData != null ? blobData.toByteArray() : null;
                                            if (htmlBytes != null && htmlBytes.length > 0) {
                                                ai.pipestream.module.parser.config.OutlineExtractionOptions ox = config.outlineExtraction();
                                                boolean enabled = (ox == null) || Boolean.TRUE.equals(ox.enableHtmlOutline());
                                                if (enabled) {
                                                    ai.pipestream.data.v1.DocOutline outline = ai.pipestream.module.parser.tika.builders.HtmlOutlineExtractor
                                                            .buildDocOutlineFromHtml(
                                                                    htmlBytes,
                                                                    ox != null ? ox.htmlIncludeCss() : null,
                                                                    ox != null ? ox.htmlExcludeCss() : null,
                                                                    ox == null || Boolean.TRUE.equals(ox.htmlStripScripts()),
                                                                    ox != null ? ox.htmlMinHeadingLevel() : 1,
                                                                    ox != null ? ox.htmlMaxHeadingLevel() : 6,
                                                                    ox == null || Boolean.TRUE.equals(ox.htmlGenerateIds())
                                                            );
                                                    if (outline.getSectionsCount() > 0) {
                                                        ai.pipestream.data.v1.SearchMetadata sm = parsedDoc.getSearchMetadata().toBuilder()
                                                                .setDocOutline(outline)
                                                                .build();
                                                        outputDocBuilder.setSearchMetadata(sm);
                                                    }
                                                }

                                                // Extract links and enrich SearchMetadata.discovered_links
                                                try {
                                                    java.util.List<ai.pipestream.data.v1.LinkReference> links = ai.pipestream.module.parser.tika.builders.HtmlOutlineExtractor
                                                            .extractLinks(
                                                                    htmlBytes,
                                                                    parsedDoc.getSearchMetadata().getSourceUri(),
                                                                    ox == null || Boolean.TRUE.equals(ox.htmlStripScripts()),
                                                                    ox != null ? ox.htmlIncludeCss() : null,
                                                                    ox != null ? ox.htmlExcludeCss() : null
                                                            );
                                                    if (!links.isEmpty()) {
                                                        // Mark is_external relative to source_uri
                                                        try {
                                                            String src = parsedDoc.getSearchMetadata().getSourceUri();
                                                            java.net.URI base = (src != null && !src.isEmpty()) ? new java.net.URI(src) : null;
                                                            if (base != null && base.getHost() != null) {
                                                                String host = base.getHost();
                                                                java.util.List<ai.pipestream.data.v1.LinkReference> adjusted = new java.util.ArrayList<>(links.size());
                                                                for (ai.pipestream.data.v1.LinkReference lr : links) {
                                                                    boolean external = false;
                                                                    try {
                                                                        java.net.URI u = new java.net.URI(lr.getUrl());
                                                                        String h = u.getHost();
                                                                        external = (h != null && !h.equalsIgnoreCase(host));
                                                                    } catch (Exception ignored3) {}
                                                                    adjusted.add(lr.toBuilder().setIsExternal(external).build());
                                                                }
                                                                links = adjusted;
                                                            }
                                                        } catch (Exception ignored3) {}
                                                        ai.pipestream.data.v1.SearchMetadata.Builder smb = outputDocBuilder.hasSearchMetadata()
                                                                ? outputDocBuilder.getSearchMetadata().toBuilder()
                                                                : parsedDoc.getSearchMetadata().toBuilder();
                                                        smb.clearDiscoveredLinks();
                                                        smb.addAllDiscoveredLinks(links);
                                                        // Derive path fields from source_uri
                                                        String src = parsedDoc.getSearchMetadata().getSourceUri();
                                                        if (src != null && !src.isEmpty()) {
                                                            try {
                                                                java.net.URI u = new java.net.URI(src);
                                                                String path = u.getPath();
                                                                if (path != null) {
                                                                    smb.setSourcePath(path);
                                                                    String[] parts = path.split("/");
                                                                    java.util.List<String> segs = new java.util.ArrayList<>();
                                                                    for (String p : parts) { if (!p.isEmpty()) segs.add(p); }
                                                                    smb.clearSourcePathSegments();
                                                                    smb.addAllSourcePathSegments(segs);
                                                                    if (!segs.isEmpty()) smb.setSourceSlug(segs.get(segs.size()-1));
                                                                }
                                                            } catch (Exception ignored2) {}
                                                        }
                                                        outputDocBuilder.setSearchMetadata(smb.build());
                                                    }
                                                } catch (Exception ignored2) {}
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                } catch (Exception e) {
                                    LOG.warnf(e, "Failed to build TikaResponse; leaving existing structured_data if any");
                                }
                            }

                            PipeDoc outputDoc = outputDocBuilder.build();

                            // Add the document to the processing buffer for test data generation
                            outputBuffer.add(outputDoc);
                            LOG.debugf("Added document to processing buffer: %s", outputDoc.getDocId());

                            responseBuilder.setOutputDoc(outputDoc)
                                    .addProcessorLogs("Parser service successfully processed document using Tika")
                                    .addProcessorLogs(String.format("Extracted title: '%s'", 
                                            outputDoc.getSearchMetadata().getTitle().isEmpty() ? "none" : outputDoc.getSearchMetadata().getTitle()))
                                    .addProcessorLogs(String.format("Extracted body length: %d characters", 
                                            outputDoc.getSearchMetadata().getBody().length()))
                                    .addProcessorLogs(String.format("Extracted custom data fields: %d", 
                                            outputDoc.hasStructuredData() ? 1 : 0));

                            LOG.debugf("Successfully parsed document - title: '%s', body length: %d, custom data fields: %d",
                                     outputDoc.getSearchMetadata().getTitle(), outputDoc.getSearchMetadata().getBody().length(), 
                                     outputDoc.hasStructuredData() ? 1 : 0);
                        } else {
                            // Blob data is empty - pass through unchanged
                            responseBuilder.setOutputDoc(request.getDocument())
                                    .addProcessorLogs("Parser service received empty blob data - passing through unchanged");
                            LOG.debug("Blob data is empty - passing through");
                        }

                    } else {
                        // Document has no blob data to parse - just pass it through
                        responseBuilder.setOutputDoc(request.getDocument())
                                .addProcessorLogs("Parser service received document with no blob data - passing through unchanged");
                        LOG.debug("Document has no blob data to parse - passing through");
                    }

                } else {
                    responseBuilder.addProcessorLogs("Parser service received request with no document");
                    LOG.debug("No document in request to parse");
                }

                return responseBuilder.build();

            } catch (Exception e) {
                LOG.error("Error parsing document: " + e.getMessage(), e);

                return ModuleProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("Parser service failed to process document: " + e.getMessage())
                        .addProcessorLogs("Error type: " + e.getClass().getSimpleName())
                        .build();
            } catch (AssertionError e) {
                LOG.error("Assertion error parsing document: " + e.getMessage(), e);

                return ModuleProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("Parser service failed with assertion error: " + e.getMessage())
                        .addProcessorLogs("This may be a Tika internal issue with the document format")
                        .build();
            } catch (Throwable t) {
                LOG.error("Unexpected error parsing document: " + t.getMessage(), t);

                return ModuleProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("Parser service failed with unexpected error: " + t.getMessage())
                        .addProcessorLogs("Error type: " + t.getClass().getSimpleName())
                        .build();
            }
        });
    }

    @Override
    public Uni<ServiceRegistrationMetadata> getServiceRegistration(RegistrationRequest request) {
        LOG.debug("Parser service registration requested");

        ServiceRegistrationMetadata.Builder responseBuilder = ServiceRegistrationMetadata.newBuilder()
                .setModuleName("parser")
                .setVersion("1.0.0-SNAPSHOT")
                .setCapabilities(Capabilities.newBuilder().addTypes(CapabilityType.PARSER).build());

        // Use SchemaExtractorService to get a JSONForms-ready ParserConfig schema (refs resolved)
        Optional<String> schemaOptional = schemaExtractorService.extractParserConfigSchemaResolvedForJsonForms();
        
        if (schemaOptional.isPresent()) {
            String jsonSchema = schemaOptional.get();
            // Enhance the schema (non-breaking for JSONForms; unknown keys are ignored)
            String enhancedSchema = schemaEnhancer.enhanceSchema(jsonSchema);
            responseBuilder.setJsonConfigSchema(enhancedSchema);
            LOG.debugf("Successfully extracted and enhanced JSONForms-ready schema (%d characters)", enhancedSchema.length());
            LOG.info("Returning enhanced JSON schema for parser module (refs resolved, suggestions added).");
        } else {
            responseBuilder.setHealthCheckPassed(false);
            responseBuilder.setHealthCheckMessage("Failed to resolve ParserConfig schema for JSONForms");
            LOG.error("SchemaExtractorService could not resolve ParserConfig schema for JSONForms");
            return Uni.createFrom().item(responseBuilder.build());
        }

        // If test request is provided, perform health check
        if (request.hasTestRequest()) {
            LOG.debug("Performing health check with test request");
            return processData(request.getTestRequest())
                .map(processResponse -> {
                    if (processResponse.getSuccess()) {
                        responseBuilder
                            .setHealthCheckPassed(true)
                            .setHealthCheckMessage("Parser module is healthy - successfully processed test document");
                    } else {
                        responseBuilder
                            .setHealthCheckPassed(false)
                            .setHealthCheckMessage("Parser module health check failed: " + 
                                String.join("; ", processResponse.getProcessorLogsList()));
                    }
                    return responseBuilder.build();
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.error("Health check failed with exception", error);
                    return responseBuilder
                        .setHealthCheckPassed(false)
                        .setHealthCheckMessage("Health check failed with exception: " + error.getMessage())
                        .build();
                });
        } else {
            // No test request provided, assume healthy
            responseBuilder
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("No health check performed - module assumed healthy");
            return Uni.createFrom().item(responseBuilder.build());
        }
    }
    
    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        LOG.debug("TestProcessData called - proxying to processData");
        return processData(request);
    }


    /**
     * Extracts configuration parameters from the process request.
     */
    private ParserConfig extractConfiguration(ModuleProcessRequest request) {
        // Try to extract from custom JSON config first
        if (request.hasConfig() && request.getConfig().hasCustomJsonConfig()) {
            try {
                Struct jsonConfig = request.getConfig().getCustomJsonConfig();
                String jsonString = structToJsonString(jsonConfig);
                LOG.debugf("Parsing ParserConfig from JSON: %s", jsonString);
                
                return objectMapper.readValue(jsonString, ParserConfig.class);
            } catch (Exception e) {
                LOG.warnf("Failed to parse ParserConfig from JSON, using fallback: %s", e.getMessage());
            }
        }
        
        // Fallback to config params with defaults
        Map<String, String> configParams = new TreeMap<>();
        if (request.hasConfig()) {
            configParams.putAll(request.getConfig().getConfigParamsMap());
        }
        
        LOG.debugf("Using default ParserConfig with config params: %s", configParams.keySet());
        return ParserConfig.defaultConfig();
    }
    
    /**
     * Converts a Protobuf Struct to JSON string using Google's JsonFormat utility.
     */
    private String structToJsonString(Struct struct) throws Exception {
        return JsonFormat.printer().print(struct);
    }

    /**
     * Determines whether to build the comprehensive TikaResponse.
     */
    private boolean shouldExtractComprehensiveMetadata(ParserConfig config) {
        // For now, follow the same flag as legacy metadata extraction
        return true; // config typically defaults to extract metadata; keep always-on for service tests
    }

    /**
     * Builds a TikaResponse by running a lightweight metadata parse and using TikaMetadataExtractor.
     */
    private TikaResponse extractTikaResponse(com.google.protobuf.ByteString content,
                                             String filename,
                                             String extractedText,
                                             String docId) throws Exception {
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isEmpty()) {
            metadata.set("resourceName", filename);
            String lowerName = filename.toLowerCase();
            if (lowerName.endsWith(".ttf")) {
                metadata.set("Content-Type", "font/ttf");
            } else if (lowerName.endsWith(".otf")) {
                metadata.set("Content-Type", "font/otf");
            } else if (lowerName.endsWith(".woff")) {
                metadata.set("Content-Type", "font/woff");
            } else if (lowerName.endsWith(".woff2")) {
                metadata.set("Content-Type", "font/woff2");
            }
        }

        boolean isFont = (filename != null && filename.toLowerCase().matches(".*\\.(ttf|ttc|otf|woff2?|pfa|pfb)$"))
                || (metadata.get("Content-Type") != null && metadata.get("Content-Type").toLowerCase().startsWith("font/"));
        if (isFont) {
            metadata.add("tika:parsing-warning", "font-bypass:no-content-parse");
            return TikaMetadataExtractor.extractComprehensiveMetadata(metadata, "font-bypass", extractedText, docId);
        }

        Parser parser = new AutoDetectParser();
        org.xml.sax.helpers.DefaultHandler handler = new org.xml.sax.helpers.DefaultHandler();
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);

        try (InputStream is = new ByteArrayInputStream(content.toByteArray())) {
            parser.parse(is, handler, metadata, parseContext);
        } catch (AssertionError | Exception primaryEx) {
            metadata.add("tika:parsing-warning", "primary-parse-failed:" + primaryEx.getClass().getSimpleName());
            try {
                String customConfig = "<properties>\n" +
                        "  <parsers>\n" +
                        "    <parser class=\"ai.pipestream.shaded.tika.parser.microsoft.EMFParser\" enabled=\"false\"/>\n" +
                        "  </parsers>\n" +
                        "</properties>";
                try (InputStream cfg = new ByteArrayInputStream(customConfig.getBytes())) {
                    ai.pipestream.shaded.tika.config.TikaConfig tikaCfg = new ai.pipestream.shaded.tika.config.TikaConfig(cfg);
                    Parser fallbackParser = new AutoDetectParser(tikaCfg);
                    ParseContext fallbackCtx = new ParseContext();
                    fallbackCtx.set(Parser.class, fallbackParser);
                    try (InputStream is2 = new ByteArrayInputStream(content.toByteArray())) {
                        fallbackParser.parse(is2, handler, metadata, fallbackCtx);
                        metadata.add("tika:parsing-warning", "fallback:disabledEmfParser");
                    }
                }
            } catch (Exception fallbackEx) {
                metadata.add("tika:parsing-warning", "fallback-failed:" + fallbackEx.getClass().getSimpleName());
            }
        }

        // Inject raw bytes for EPUB structure enrichment if this looks like an EPUB
        try {
            if ((filename != null && filename.toLowerCase().endsWith(".epub")) ||
                (metadata.get("Content-Type") != null && metadata.get("Content-Type").toLowerCase().contains("epub"))) {
                String b64 = java.util.Base64.getEncoder().encodeToString(content.toByteArray());
                metadata.set("pipe:raw-bytes-b64", b64);
            }
        } catch (Exception ignored) {}

        // Post-process: Extract XMP Rights from images (same as in DocumentParser)
        try {
            String mimeType = metadata.get("Content-Type");
            if (mimeType != null && mimeType.startsWith("image/")) {
                documentParser.extractXMPRightsPublic(content, metadata);
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract XMP Rights in extractTikaResponse: %s", e.getMessage());
        }

        return TikaMetadataExtractor.extractComprehensiveMetadata(metadata, parser.getClass().getName(), extractedText, docId);
    }
}
