package ai.pipestream.module.parser;

import ai.pipestream.module.parser.schema.SchemaExtractorService;
import ai.pipestream.data.module.v1.*;
import ai.pipestream.data.v1.Blob;
import ai.pipestream.data.v1.DocOutline;
import ai.pipestream.data.v1.LogEntry;
import ai.pipestream.data.v1.LogEntrySource;
import ai.pipestream.data.v1.LogLevel;
import ai.pipestream.data.v1.ModuleLogOrigin;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.v1.SearchMetadata;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import ai.pipestream.parsed.data.docling.v1.DoclingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import ai.pipestream.module.parser.config.ParserConfig;
import ai.pipestream.module.parser.util.DocumentParser;
import ai.pipestream.module.parser.tika.TikaMetadataExtractor;
import com.google.protobuf.Any;
import ai.pipestream.module.parser.schema.SchemaEnhancer;
import ai.pipestream.server.meta.BuildInfoProvider;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.Optional;
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
public class ParserServiceImpl implements PipeStepProcessorService {

    private static final Logger LOG = Logger.getLogger(ParserServiceImpl.class);

    private static LogEntry moduleLog(String message, LogLevel level) {
        return LogEntry.newBuilder()
                .setSource(LogEntrySource.LOG_ENTRY_SOURCE_MODULE)
                .setLevel(level)
                .setMessage(message)
                .setTimestampEpochMs(System.currentTimeMillis())
                .setModule(ModuleLogOrigin.newBuilder().setModuleName("parser").build())
                .build();
    }

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SchemaExtractorService schemaExtractorService;
    
    @Inject
    SchemaEnhancer schemaEnhancer;

    @Inject
    DocumentParser documentParser;

    @Inject
    ai.pipestream.module.parser.docling.DoclingMetadataExtractor doclingMetadataExtractor;

    @Inject
    ai.pipestream.module.parser.docling.DoclingEndpointHolder doclingEndpointHolder;

    @Inject
    BuildInfoProvider buildInfoProvider;

    @Override
    public Uni<ProcessDataResponse> processData(ProcessDataRequest request) {
        LOG.debugf("Parser service received document: %s",
                 request.hasDocument() ? request.getDocument().getDocId() : "no document");

        if (!request.hasDocument()) {
            return Uni.createFrom().item(ProcessDataResponse.newBuilder()
                    .setOutcome(ProcessingOutcome.PROCESSING_OUTCOME_SUCCESS)
                    .addLogEntries(moduleLog("Parser service received request with no document", LogLevel.LOG_LEVEL_INFO))
                    .build());
        }

        // 1. Extract configuration
        ParserConfig config = extractConfiguration(request);
        
        // 2. Prepare context inputs (blob, filename, etc.)
        com.google.protobuf.ByteString blobData;
        String filename;
        String docId = request.getDocument().getDocId();
        boolean hasContent = false;

        if (request.getDocument().hasBlobBag() && request.getDocument().getBlobBag().hasBlob()) {
            Blob blob = request.getDocument().getBlobBag().getBlob();
            if (blob.hasData() && !blob.getData().isEmpty()) {
                blobData = blob.getData();
                filename = blob.hasFilename() ? blob.getFilename() : null;
                hasContent = true;
            } else {
                blobData = com.google.protobuf.ByteString.EMPTY;
                filename = null;
            }
        } else {
            blobData = com.google.protobuf.ByteString.EMPTY;
            filename = null;
        }

        if (!hasContent) {
             return Uni.createFrom().item(ProcessDataResponse.newBuilder()
                    .setOutcome(ProcessingOutcome.PROCESSING_OUTCOME_SUCCESS)
                    .setOutputDoc(request.getDocument())
                    .addLogEntries(moduleLog("No blob data present — document " + docId + " passed through without parsing", LogLevel.LOG_LEVEL_INFO))
                    .build());
        }

        final long startTime = System.currentTimeMillis();

        // Capture final variables for lambdas
        final com.google.protobuf.ByteString finalBlobData = blobData;
        final String finalFilename = filename;
        final String finalDocId = docId;

        // 3. Define Parallel Tasks

        // Task A: Tika Parsing (Base Text + Metadata)
        Uni<ParsingContext> tikaTask = Uni.createFrom().item(() -> {
            try {
                LOG.debugf("Starting Tika parsing for %s", finalFilename);
                boolean isFontFile = (finalFilename != null && finalFilename.toLowerCase().matches(".*\\.(ttf|ttc|otf|woff2?|pfa|pfb)$"));
                
                PipeDoc parsedDoc;
                if (isFontFile) {
                    String title = finalFilename;
                    if (finalFilename != null && finalFilename.lastIndexOf('.') > 0) {
                        title = finalFilename.substring(0, finalFilename.lastIndexOf('.'));
                    }
                    SearchMetadata sm = SearchMetadata.newBuilder().setTitle(title).setBody("").build();
                    parsedDoc = PipeDoc.newBuilder().setSearchMetadata(sm).build();
                } else {
                    parsedDoc = documentParser.parseDocument(finalBlobData, config, finalFilename);
                }
                
                TikaResponse tikaResponse = null;
                if (shouldExtractComprehensiveMetadata(config)) {
                     try {
                        tikaResponse = extractTikaResponse(finalBlobData, finalFilename, 
                                parsedDoc.getSearchMetadata().getBody(), finalDocId);
                    } catch (Exception e) {
                        LOG.warnf(e, "Tika extraction failed for document %s", finalDocId);
                    }
                }
                
                return new ParsingContext(request.getDocument(), parsedDoc, finalBlobData, finalFilename, config, tikaResponse);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

        // Task B: Docling Extraction
        Uni<Optional<DoclingResponse>> doclingTask = Uni.createFrom().item(Optional.<DoclingResponse>empty());
        if (shouldExtractDoclingMetadata(config)) {
            final byte[] contentBytes = finalBlobData.toByteArray();
            doclingTask = Uni.createFrom().item(() -> {
                try {
                    LOG.debugf("Starting Docling extraction for %s", finalFilename);
                    return Optional.ofNullable(doclingMetadataExtractor.extractComprehensiveMetadata(
                        contentBytes, finalFilename, finalDocId, config.doclingOptions()));
                } catch (Exception e) {
                    LOG.warnf(e, "Docling extraction failed for document %s", finalDocId);
                    return Optional.<DoclingResponse>empty();
                }
            }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
        }

        // 4. Execute both in parallel and merge
        return Uni.combine().all().unis(tikaTask, doclingTask).asTuple()
            .map(tuple -> {
                ParsingContext ctx = tuple.getItem1();
                Optional<DoclingResponse> doclingRes = tuple.getItem2();

                PipeDoc.Builder outputDocBuilder = ctx.parsedDoc.toBuilder()
                        .setDocId(ctx.originalDoc.getDocId());

                if (ctx.tikaResponse != null) {
                    storeTikaMetadata(outputDocBuilder, ctx.tikaResponse);
                }

                doclingRes.ifPresent(dr -> storeDoclingMetadata(outputDocBuilder, dr));

                enrichDocument(outputDocBuilder, ctx.tikaResponse, ctx);

                    PipeDoc outputDoc = outputDocBuilder.build();
                    long duration = System.currentTimeMillis() - startTime;
                    String bodyText = outputDoc.getSearchMetadata().getBody();
                    int wordCount = bodyText.isEmpty() ? 0 : bodyText.split("\\s+").length;
                    String mimeType = outputDoc.getSearchMetadata().hasSourceMimeType()
                            ? outputDoc.getSearchMetadata().getSourceMimeType() : "unknown";
                    String title = outputDoc.getSearchMetadata().hasTitle()
                            ? outputDoc.getSearchMetadata().getTitle() : "(none)";

                    ProcessDataResponse.Builder respBuilder = ProcessDataResponse.newBuilder()
                            .setOutcome(ProcessingOutcome.PROCESSING_OUTCOME_SUCCESS)
                            .setOutputDoc(outputDoc);

                    respBuilder.addLogEntries(moduleLog(String.format(
                            "Document received: %s, %d bytes, MIME type: %s",
                            finalFilename != null ? finalFilename : "(no filename)",
                            finalBlobData.size(), mimeType), LogLevel.LOG_LEVEL_INFO));

                    boolean isFontFile = (finalFilename != null && finalFilename.toLowerCase().matches(".*\\.(ttf|ttc|otf|woff2?|pfa|pfb)$"));
                    if (isFontFile) {
                        respBuilder.addLogEntries(moduleLog(String.format(
                                "Font file detected (%s) — skipped parsing, using filename as title", finalFilename), LogLevel.LOG_LEVEL_INFO));
                    } else {
                        respBuilder.addLogEntries(moduleLog(String.format(
                                "Parsed successfully: extracted %d words, title: '%s'", wordCount, title), LogLevel.LOG_LEVEL_INFO));
                    }

                    if (ctx.tikaResponse != null) {
                        respBuilder.addLogEntries(moduleLog("Tika metadata stored on document", LogLevel.LOG_LEVEL_INFO));
                    }
                    if (doclingRes.isPresent()) {
                        respBuilder.addLogEntries(moduleLog("Docling metadata stored on document", LogLevel.LOG_LEVEL_INFO));
                    }

                    int outlineSections = outputDoc.getSearchMetadata().hasDocOutline()
                            ? outputDoc.getSearchMetadata().getDocOutline().getSectionsCount() : 0;
                    if (outlineSections > 0) {
                        respBuilder.addLogEntries(moduleLog(String.format(
                                "Document outline extracted: %d sections", outlineSections), LogLevel.LOG_LEVEL_INFO));
                    }

                    int linkCount = outputDoc.getSearchMetadata().getDiscoveredLinksCount();
                    if (linkCount > 0) {
                        respBuilder.addLogEntries(moduleLog(String.format("Discovered %d links", linkCount), LogLevel.LOG_LEVEL_INFO));
                    }

                    respBuilder.addLogEntries(moduleLog(String.format("Parsing completed in %dms", duration), LogLevel.LOG_LEVEL_INFO));

                    return respBuilder.build();
                })
        .onFailure().recoverWithItem(t -> {
            LOG.error("Error parsing document: " + t.getMessage(), t);
            return ProcessDataResponse.newBuilder()
                    .setOutcome(ProcessingOutcome.PROCESSING_OUTCOME_FAILURE)
                    .addLogEntries(moduleLog("Parser service failed: " + t.getMessage(), LogLevel.LOG_LEVEL_ERROR))
                    .build();
        });
    }

    // Updated Helper class
    private record ParsingContext(PipeDoc originalDoc, PipeDoc parsedDoc, com.google.protobuf.ByteString blobData, 
                                  String filename, ParserConfig config, TikaResponse tikaResponse) {}

    /**
     * Post-processing logic moved here for cleaner flow (Outline, Links, etc.)
     */
    private void enrichDocument(PipeDoc.Builder outputDocBuilder, TikaResponse tikaResponse, ParsingContext ctx) {
        if (!shouldExtractComprehensiveMetadata(ctx.config) || tikaResponse == null) return;
        
        try {
            // 1. EPUB TOC
            try {
                if (tikaResponse.hasEpub() && tikaResponse.getEpub().getTableOfContentsCount() > 0) {
                    ai.pipestream.data.v1.DocOutline outline = ai.pipestream.module.parser.tika.builders.EpubStructureExtractor
                            .buildDocOutlineFromToc(tikaResponse.getEpub().getTableOfContentsList());
                    ai.pipestream.data.v1.SearchMetadata sm = outputDocBuilder.getSearchMetadata().toBuilder().setDocOutline(outline).build();
                    outputDocBuilder.setSearchMetadata(sm);
                }
            } catch (Exception ignored) {}

            // 2. PDF Bookmarks
            try {
                if (tikaResponse.hasPdf() && (ctx.filename != null && ctx.filename.toLowerCase().endsWith(".pdf"))) {
                    ai.pipestream.data.v1.DocOutline outline = ai.pipestream.module.parser.tika.builders.PdfOutlineExtractor
                            .buildDocOutlineFromPdf(ctx.blobData.toByteArray());
                    if (outline.getSectionsCount() > 0) {
                        ai.pipestream.data.v1.SearchMetadata sm = outputDocBuilder.getSearchMetadata().toBuilder().setDocOutline(outline).build();
                        outputDocBuilder.setSearchMetadata(sm);
                    }
                }
            } catch (Exception ignored) {}

            // 3. Markdown
            try {
                boolean mdEnabled = ctx.config.outlineExtraction() == null || Boolean.TRUE.equals(ctx.config.outlineExtraction().enableMarkdownOutline());
                if (mdEnabled && ctx.filename != null && ctx.filename.toLowerCase().endsWith(".md")) {
                    byte[] bytes = ctx.parsedDoc.getSearchMetadata().getBody().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    DocOutline outline = ai.pipestream.module.parser.tika.builders.MarkdownExtractor.buildDocOutlineFromMarkdown(bytes, 1, 6, true);
                    if (outline.getSectionsCount() > 0) {
                         ai.pipestream.data.v1.SearchMetadata sm = outputDocBuilder.getSearchMetadata().toBuilder().setDocOutline(outline).build();
                         outputDocBuilder.setSearchMetadata(sm);
                    }
                }
            } catch (Exception ignored) {}
            
            // 4. HTML
             try {
                if (tikaResponse.hasHtml()) {
                    ai.pipestream.parsed.data.html.v1.HtmlMetadata html = tikaResponse.getHtml();
                    ai.pipestream.data.v1.SearchMetadata.Builder smBuilder = outputDocBuilder.getSearchMetadata().toBuilder();
                    boolean added = false;

                    // A. Extract Semantic Links from Metadata (HEAD)
                    if (html.hasCanonicalUrl()) {
                        smBuilder.addDiscoveredLinks(ai.pipestream.data.v1.LinkReference.newBuilder()
                                .setUrl(html.getCanonicalUrl()).setRel("canonical").build());
                        added = true;
                    }
                    if (html.hasAlternateUrl()) {
                        smBuilder.addDiscoveredLinks(ai.pipestream.data.v1.LinkReference.newBuilder()
                                .setUrl(html.getAlternateUrl()).setRel("alternate").build());
                        added = true;
                    }
                    if (html.hasStylesheetUrl()) {
                        smBuilder.addDiscoveredLinks(ai.pipestream.data.v1.LinkReference.newBuilder()
                                .setUrl(html.getStylesheetUrl()).setRel("stylesheet").build());
                        added = true;
                    }
                    if (html.hasRssUrl()) {
                        smBuilder.addDiscoveredLinks(ai.pipestream.data.v1.LinkReference.newBuilder()
                                .setUrl(html.getRssUrl()).setRel("rss").build());
                        added = true;
                    }
                    if (html.hasAtomUrl()) {
                        smBuilder.addDiscoveredLinks(ai.pipestream.data.v1.LinkReference.newBuilder()
                                .setUrl(html.getAtomUrl()).setRel("atom").build());
                        added = true;
                    }
                    
                    // Also populate basic metadata from HTML specific fields
                    if (html.hasTitle() && smBuilder.getTitle().isEmpty()) {
                        smBuilder.setTitle(html.getTitle());
                        added = true;
                    }

                    // B. Extract Body Links via Jsoup (BODY)
                    if (ctx.blobData != null) {
                        java.util.List<ai.pipestream.data.v1.LinkReference> bodyLinks = 
                            ai.pipestream.module.parser.tika.builders.HtmlOutlineExtractor.extractLinks(
                                ctx.blobData.toByteArray(), 
                                "", 
                                true, 
                                null, 
                                null
                            );
                        if (!bodyLinks.isEmpty()) {
                            smBuilder.addAllDiscoveredLinks(bodyLinks);
                            added = true;
                        }
                    }
                    
                    if (added) {
                        outputDocBuilder.setSearchMetadata(smBuilder.build());
                    }
                }
            } catch (Exception ignored) {
                LOG.warn("HTML enrichment failed", ignored);
            }

            // 5. Resolve section character offsets for any DocOutline that was set.
            // Enables section-level vector centroids and search result highlighting.
            try {
                ai.pipestream.data.v1.SearchMetadata currentSm = outputDocBuilder.getSearchMetadata();
                if (currentSm.hasDocOutline() && currentSm.getDocOutline().getSectionsCount() > 0) {
                    String bodyText = currentSm.getBody();
                    if (bodyText != null && !bodyText.isEmpty()) {
                        DocOutline resolved = ai.pipestream.module.parser.tika.builders.SectionOffsetResolver
                                .resolve(currentSm.getDocOutline(), bodyText);
                        outputDocBuilder.setSearchMetadata(currentSm.toBuilder().setDocOutline(resolved).build());
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            LOG.warn("Enrichment failed", e);
        }
    }

    @Override
    public Uni<GetServiceRegistrationResponse> getServiceRegistration(GetServiceRegistrationRequest request) {
        LOG.debug("Parser service registration requested");

        GetServiceRegistrationResponse.Builder responseBuilder = GetServiceRegistrationResponse.newBuilder()
                .setModuleName("parser")
                .setVersion(buildInfoProvider.getVersion())
                .putAllMetadata(buildInfoProvider.registrationMetadata())
                .setCapabilities(Capabilities.newBuilder().addTypes(CapabilityType.CAPABILITY_TYPE_PARSER).build());

        Optional<String> schemaOptional = schemaExtractorService.extractParserConfigSchemaResolvedForJsonForms();
        
        if (schemaOptional.isPresent()) {
            String jsonSchema = schemaOptional.get();
            String enhancedSchema = schemaEnhancer.enhanceSchema(jsonSchema);
            responseBuilder.setJsonConfigSchema(enhancedSchema);
            LOG.debugf("Successfully extracted and enhanced JSONForms-ready schema (%d characters)", enhancedSchema.length());
        } else {
            responseBuilder.setHealthCheckPassed(false);
            responseBuilder.setHealthCheckMessage("Failed to resolve ParserConfig schema for JSONForms");
            return Uni.createFrom().item(responseBuilder.build());
        }

        if (request.hasTestRequest()) {
            return processData(request.getTestRequest())
                .map(processResponse -> {
                    if (processResponse.getOutcome() == ProcessingOutcome.PROCESSING_OUTCOME_SUCCESS) {
                        responseBuilder
                            .setHealthCheckPassed(true)
                            .setHealthCheckMessage("Parser module is healthy");
                    } else {
                        responseBuilder
                            .setHealthCheckPassed(false)
                            .setHealthCheckMessage("Parser module health check failed");
                    }
                    return responseBuilder.build();
                })
                .onFailure().recoverWithItem(error -> {
                    return responseBuilder
                        .setHealthCheckPassed(false)
                        .setHealthCheckMessage("Health check failed: " + error.getMessage())
                        .build();
                });
        } else {
            responseBuilder
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("No health check performed");
            return Uni.createFrom().item(responseBuilder.build());
        }
    }
    
    private ParserConfig extractConfiguration(ProcessDataRequest request) {
        if (request.hasConfig() && request.getConfig().hasJsonConfig()) {
            try {
                Struct jsonConfig = request.getConfig().getJsonConfig();
                String jsonString = structToJsonString(jsonConfig);
                return objectMapper.readValue(jsonString, ParserConfig.class);
            } catch (Exception e) {
                LOG.warnf("Failed to parse ParserConfig from JSON: %s", e.getMessage());
            }
        }
        return ParserConfig.defaultConfig();
    }
    
    private String structToJsonString(Struct struct) throws Exception {
        return JsonFormat.printer().print(struct);
    }

    private boolean shouldExtractComprehensiveMetadata(ParserConfig config) {
        return config.enableTika() != null ? config.enableTika() : true;
    }

    private boolean shouldExtractDoclingMetadata(ParserConfig config) {
        return config.enableDocling() != null ? config.enableDocling() : false;
    }

    private TikaResponse extractTikaResponse(com.google.protobuf.ByteString content,
                                             String filename,
                                             String extractedText,
                                             String docId) throws Exception {
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isEmpty()) {
            metadata.set("resourceName", filename);
        }

        Parser parser = new AutoDetectParser();
        org.xml.sax.helpers.DefaultHandler handler = new org.xml.sax.helpers.DefaultHandler();
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);

        try (InputStream is = new ByteArrayInputStream(content.toByteArray())) {
            parser.parse(ai.pipestream.shaded.tika.io.TikaInputStream.get(is), handler, metadata, parseContext);
        }

        // Enrich with XMP Rights for images (critical for CC tests)
        String mimeType = metadata.get(Metadata.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith("image/")) {
            try {
                documentParser.extractXMPRightsPublic(content, metadata);
            } catch (Exception e) {
                LOG.warn("Failed to extract XMP rights in Tika response generation", e);
            }
        }

        return TikaMetadataExtractor.extractComprehensiveMetadata(metadata, parser.getClass().getName(), extractedText, docId);
    }

    private void storeTikaMetadata(PipeDoc.Builder outputDocBuilder, TikaResponse tikaResponse) {
        try {
            Any tikaAny = Any.pack(tikaResponse);
            String tikaVersion = ai.pipestream.module.parser.tika.builders.MetadataUtils.getTikaVersion();
            com.google.protobuf.Timestamp now = com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(System.currentTimeMillis() / 1000)
                    .build();

            ai.pipestream.data.v1.ParsedMetadata tikaMetadata = ai.pipestream.data.v1.ParsedMetadata.newBuilder()
                    .setParserName("tika")
                    .setParserVersion(tikaVersion)
                    .setParsedAt(now)
                    .setData(tikaAny)
                    .build();

            outputDocBuilder.putParsedMetadata("tika", tikaMetadata);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to store Tika metadata");
        }
    }

    private void storeDoclingMetadata(PipeDoc.Builder outputDocBuilder, DoclingResponse doclingResponse) {
        try {
            Any doclingAny = Any.pack(doclingResponse);
            com.google.protobuf.Timestamp now = com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(System.currentTimeMillis() / 1000)
                    .build();

            ai.pipestream.data.v1.ParsedMetadata doclingMetadata = ai.pipestream.data.v1.ParsedMetadata.newBuilder()
                    .setParserName("docling")
                    .setParserVersion("1.10.0")
                    .setParsedAt(now)
                    .setData(doclingAny)
                    .build();

            outputDocBuilder.putParsedMetadata("docling", doclingMetadata);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to store Docling metadata");
        }
    }

    @Override
    public Uni<UpdateBackendEndpointResponse> updateBackendEndpoint(UpdateBackendEndpointRequest request) {
        String newUrl = request.getEndpointUrl();
        String backendId = request.getBackendId();

        // Only "docling" backend (or empty = default = docling)
        if (!backendId.isEmpty() && !"docling".equals(backendId)) {
            return Uni.createFrom().item(UpdateBackendEndpointResponse.newBuilder()
                    .setSuccess(false)
                    .setActiveEndpointUrl(doclingEndpointHolder.getActiveUrl())
                    .setErrorMessage("Unknown backend_id: " + backendId + ". Supported: docling")
                    .build());
        }

        var result = doclingEndpointHolder.swap(newUrl);
        return Uni.createFrom().item(UpdateBackendEndpointResponse.newBuilder()
                .setSuccess(result.success())
                .setActiveEndpointUrl(result.activeUrl())
                .setPreviousEndpointUrl(result.previousUrl())
                .setErrorMessage(result.error() != null ? result.error() : "")
                .build());
    }
}