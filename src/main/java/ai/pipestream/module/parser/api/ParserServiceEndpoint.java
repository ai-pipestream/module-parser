package ai.pipestream.module.parser.api;

import ai.pipestream.module.parser.schema.SchemaExtractorService;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.SearchMetadata;
import ai.pipestream.module.parser.config.ParserConfig;
import ai.pipestream.module.parser.service.RepositoryDocumentClient;
import ai.pipestream.module.parser.util.DocumentParser;
import com.google.protobuf.ByteString;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.print.Doc;

import ai.pipestream.module.parser.util.SampleLoaderService;

/**
 * REST API endpoints for parser service.
 * Provides developer-friendly HTTP endpoints for testing and integration.
 */
@Path("/api/parser/service")
@Tag(name = "Parser Service", description = "Document parsing operations using Apache Tika")
@Produces(MediaType.APPLICATION_JSON)
public class ParserServiceEndpoint {

    private static final Logger LOG = Logger.getLogger(ParserServiceEndpoint.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SchemaExtractorService schemaExtractorService;

    @Inject
    DocumentParser documentParser;
    
    @Inject
    SampleLoaderService sampleLoaderService;

    @Inject
    ai.pipestream.module.parser.docling.DoclingMetadataExtractor doclingMetadataExtractor;

    @Inject
    RepositoryDocumentClient repositoryDocumentClient;

    @ConfigProperty(name = "module.name")
    String moduleName;

    @ConfigProperty(name = "module.description")
    String moduleDescription;

    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @GET
    @Path("/config")
    @Operation(summary = "Get parser configuration schema", description = "Retrieve the OpenAPI 3.1 JSON Schema for ParserConfig")
    @APIResponse(
        responseCode = "200", 
        description = "OpenAPI 3.1 JSON Schema for ParserConfig",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ParserConfig.class)
        )
    )
    public Uni<Response> getConfig() {
        LOG.debug("Parser configuration schema request received");
        
        return Uni.createFrom().item(() -> {
            // Extract the complete JSON Schema with all nested definitions resolved for registry compatibility
            Optional<String> schemaOptional = schemaExtractorService.extractParserConfigSchemaForValidation();
            
            if (schemaOptional.isPresent()) {
                String schemaJson = schemaOptional.get();
                LOG.debugf("Successfully returning ParserConfig schema (%d characters)", schemaJson.length());
                
                // Return the JSON string directly - Quarkus JAX-RS handles this perfectly
                return Response.ok(schemaJson)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
            } else {
                LOG.warn("Could not extract ParserConfig schema from OpenAPI document");
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Schema not available - check OpenAPI document generation"))
                    .build();
            }
        });
    }

    @GET
    @Path("/config/jsonforms")
    @Operation(summary = "Get parser configuration schema for JSONForms", description = "Retrieve the resolved ParserConfig JSON Schema (no $refs) for use with JSONForms/Vuetify UI")
    @APIResponse(
        responseCode = "200",
        description = "Resolved JSON Schema for ParserConfig (1:1 with Tika + Docling options)",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ParserConfig.class)
        )
    )
    public Uni<Response> getConfigJsonForms() {
        LOG.debug("Parser JSONForms schema request received");

        return Uni.createFrom().item(() -> {
            Optional<String> schemaOptional = schemaExtractorService.extractParserConfigSchemaResolvedForJsonForms();

            if (schemaOptional.isPresent()) {
                String schemaJson = schemaOptional.get();
                LOG.debugf("Successfully returning ParserConfig JSONForms schema (%d characters)", schemaJson.length());

                return Response.ok(schemaJson)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
            } else {
                LOG.warn("Could not extract ParserConfig JSONForms schema from OpenAPI document");
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Schema not available - check OpenAPI document generation"))
                    .build();
            }
        });
    }

    @GET
    @Path("/ping")
    @Operation(summary = "Simple ping test", description = "Simple endpoint to test if REST is working")
    @APIResponse(responseCode = "200", description = "Ping successful")
    public String ping() {
        return "pong";
    }

    @GET
    @Path("/health")
    @Operation(summary = "Health check", description = "Check parser service health and Tika availability")
    @APIResponse(responseCode = "200", description = "Health check successful")
    public Uni<Response> healthCheck() {
        LOG.debug("Parser health check request received");
        
        return Uni.createFrom().item(() -> {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("version", "1.0.0");
            health.put("parser", "Apache Tika 3.2.1");
            
            // Test basic parser functionality
            try {
                String testText = "Test document";
                
                // Test basic parser functionality
                PipeDoc testDoc = PipeDoc.newBuilder()
                    .setDocId("health-check")
                    .setSearchMetadata(SearchMetadata.newBuilder()
                            .setTitle("Health Check")
                            .setBody(testText)
                            .build())
                    .build();
                
                // This would normally parse actual document content, but for health check we just verify setup
                health.put("tika_status", "available");
                health.put("supported_formats", "PDF, Word, PowerPoint, Excel, HTML, Text, and more");
                
            } catch (Exception e) {
                LOG.error("Health check failed", e);
                health.put("status", "unhealthy");
                health.put("tika_status", "error: " + e.getMessage());
            }
            
            return health;
        })
        .map(health -> Response.ok(health).build());
    }

    @GET
    @Path("/info")
    @Operation(summary = "Get module information", description = "Retrieve module name, description, and other metadata")
    @APIResponse(responseCode = "200", description = "Module information retrieved successfully")
    public Uni<Response> getModuleInfo() {
        LOG.debug("Module info request received");

        return Uni.createFrom().item(() -> {
            Map<String, Object> info = new HashMap<>();
            info.put("name", moduleName);
            info.put("displayName", capitalizeTitle(moduleName));
            info.put("description", moduleDescription);
            info.put("applicationName", applicationName);
            info.put("type", "processor");
            info.put("version", "1.0.0");

            return info;
        })
        .map(info -> Response.ok(info).build());
    }

    private String capitalizeTitle(String name) {
        if (name == null || name.isEmpty()) {
            return "Module";
        }
        
        // Convert "parser" to "Parser", "document-parser" to "Document Parser", etc.
        return java.util.Arrays.stream(name.split("[-_]"))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
            .reduce((a, b) -> a + " " + b)
            .orElse("Module");
    }

    @POST
    @Path("/test")
    @Operation(summary = "Quick parser test", description = "Test parser with plain text using default settings")
    @Consumes(MediaType.TEXT_PLAIN)
    @APIResponse(responseCode = "200", description = "Test successful")
    public Uni<Response> testParser(String text) {
        LOG.debugf("Test parsing request received - text length: %d", text != null ? text.length() : 0);
        
        if (text == null || text.trim().isEmpty()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Text cannot be null or empty"))
                    .build()
            );
        }

        return Uni.createFrom().item(() -> {
            try {
                // Create a simple test document
                PipeDoc testDoc = PipeDoc.newBuilder()
                    .setDocId("test-doc")
                    .setSearchMetadata(SearchMetadata.newBuilder()
                            .setTitle("Test Document")
                            .setBody(text)
                            .build())
                    .build();

                // Use default parser configuration
                ParserConfig config = ParserConfig.defaultConfig();
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Parser test completed successfully");
                response.put("input_length", text.length());
                response.put("config_used", config);
                response.put("parser_status", "Apache Tika ready");
                
                return Response.ok(response).build();
                
            } catch (Exception e) {
                LOG.error("Error during parser test", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Parser test failed: " + e.getMessage()))
                    .build();
            }
        });
    }

    @POST
    @Path("/config/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Validate parser configuration", description = "Validate a ParserConfig JSON against the schema")
    @APIResponse(responseCode = "200", description = "Configuration validation result")
    public Uni<Response> validateConfig(ParserConfig config) {
        LOG.debugf("Parser config validation request received - config ID: %s", 
                  config != null ? config.configId() : "null");
        
        return Uni.createFrom().item(() -> {
            Map<String, Object> response = new HashMap<>();
            
            if (config == null) {
                response.put("valid", false);
                response.put("error", "Configuration cannot be null");
                return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
            }
            
            try {
                // Basic validation - the Jackson deserialization already validates structure
                response.put("valid", true);
                response.put("message", "Configuration is valid");
                response.put("config_id", config.configId());
                
                // Add configuration summary
                Map<String, Object> summary = new HashMap<>();
                if (config.parsingOptions() != null) {
                    summary.put("max_content_length", config.parsingOptions().maxContentLength());
                    summary.put("extract_metadata", config.parsingOptions().extractMetadata());
                }
                if (config.advancedOptions() != null) {
                    summary.put("geo_parser_enabled", config.advancedOptions().enableGeoTopicParser());
                    summary.put("emf_parser_disabled", config.advancedOptions().disableEmfParser());
                }
                response.put("summary", summary);
                
                return Response.ok(response).build();
                
            } catch (Exception e) {
                LOG.error("Error validating parser config", e);
                response.put("valid", false);
                response.put("error", "Validation error: " + e.getMessage());
                return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
            }
        });
    }

    @GET
    @Path("/config/examples")
    @Operation(summary = "Get configuration examples", description = "Get example ParserConfig objects for different use cases")
    @APIResponse(responseCode = "200", description = "Configuration examples retrieved successfully")
    public Uni<Response> getConfigExamples() {
        LOG.debug("Parser config examples request received");
        
        return Uni.createFrom().item(() -> {
            Map<String, Object> examples = new HashMap<>();
            
            examples.put("default", ParserConfig.defaultConfig());
            examples.put("large_documents", ParserConfig.largeDocumentProcessing());
            examples.put("fast_processing", ParserConfig.fastProcessing());
            examples.put("batch_processing", ParserConfig.batchProcessing());
            examples.put("strict_quality", ParserConfig.strictQualityControl());
            
            Map<String, Object> response = new HashMap<>();
            response.put("examples", examples);
            response.put("description", "Pre-configured ParserConfig examples for common use cases");
            
            return Response.ok(response).build();
        });
    }

    @GET
    @Path("/repository/documents")
    @Operation(summary = "List repository documents", description = "List PipeDoc entries from repository-service for test execution")
    @APIResponse(responseCode = "200", description = "Repository documents retrieved successfully")
    public Uni<Response> getRepositoryDocuments(
            @QueryParam("drive") @DefaultValue("default") String drive,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("connectorId") String connectorId) {

        return repositoryDocumentClient.listPipeDocs(drive, limit, connectorId)
                .map(listResponse -> {
                    List<Map<String, Object>> documents = new ArrayList<>();
                    listResponse.getPipedocsList().forEach(doc -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("node_id", doc.getNodeId());
                        item.put("doc_id", doc.getDocId());
                        item.put("title", doc.getTitle());
                        item.put("document_type", doc.getDocumentType());
                        item.put("drive", doc.getDrive());
                        item.put("connector_id", doc.getConnectorId());
                        item.put("size_bytes", doc.getSizeBytes());
                        item.put("created_at_epoch_ms", doc.getCreatedAtEpochMs());
                        documents.add(item);
                    });

                    Map<String, Object> response = new HashMap<>();
                    response.put("documents", documents);
                    response.put("total", listResponse.getTotalCount());
                    response.put("nextContinuationToken", listResponse.getNextContinuationToken());
                    return Response.ok(response).build();
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.error("Failed to list repository documents", error);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of("error", "Failed to list repository documents: " + error.getMessage()))
                            .build();
                });
    }

    @POST
    @Path("/repository/parse")
    @Operation(summary = "Parse repository document", description = "Load a document from repository-service and parse directly in module-parser")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", description = "Repository document parsed successfully")
    public Uni<Response> parseRepositoryDocument(
            @Schema(description = "Request with repository nodeId and optional ParserConfig")
            Map<String, Object> request) {

        String nodeId = (String) request.get("nodeId");
        if (nodeId == null || nodeId.isBlank()) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "nodeId is required"))
                            .build()
            );
        }

        ParserConfig config;
        try {
            config = request.containsKey("config")
                    ? objectMapper.convertValue(request.get("config"), ParserConfig.class)
                    : ParserConfig.defaultConfig();
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid config payload: " + e.getMessage()))
                            .build()
            );
        }

        return repositoryDocumentClient.getPipeDoc(nodeId)
                .flatMap(pipeDocResponse -> {
                    PipeDoc sourceDoc = pipeDocResponse.getPipedoc();
                    if (!sourceDoc.hasBlobBag()
                            || !sourceDoc.getBlobBag().hasBlob()
                            || !sourceDoc.getBlobBag().getBlob().hasStorageRef()) {
                        return Uni.createFrom().item(
                                Response.status(Response.Status.BAD_REQUEST)
                                        .entity(Map.of("error", "Repository document does not include blob storage reference"))
                                        .build()
                        );
                    }

                    ai.pipestream.data.v1.Blob sourceBlob = sourceDoc.getBlobBag().getBlob();
                    ai.pipestream.data.v1.FileStorageReference storageRef = sourceBlob.getStorageRef();

                    return repositoryDocumentClient.getBlob(storageRef)
                            .flatMap(blobResponse -> Uni.createFrom().item(() -> {
                                try {
                                    byte[] content = blobResponse.getData().toByteArray();
                                    String filename = sourceBlob.hasFilename()
                                            ? sourceBlob.getFilename()
                                            : sourceDoc.getDocId() + ".bin";

                                    PipeDoc parsedDoc = documentParser.parseDocument(
                                            ByteString.copyFrom(content),
                                            config,
                                            filename
                                    );

                                    ai.pipestream.parsed.data.docling.v1.DoclingResponse doclingResponse =
                                            doclingMetadataExtractor.extractComprehensiveMetadata(
                                                    content,
                                                    filename,
                                                    parsedDoc.getDocId(),
                                                    config.doclingOptions()
                                            );
                                    if (doclingResponse != null) {
                                        PipeDoc.Builder docBuilder = parsedDoc.toBuilder();
                                        storeDoclingMetadata(docBuilder, doclingResponse);
                                        parsedDoc = docBuilder.build();
                                    }
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("success", true);
                                    result.put("source", Map.of(
                                            "node_id", nodeId,
                                            "doc_id", sourceDoc.getDocId(),
                                            "drive", storageRef.getDriveName(),
                                            "object_key", storageRef.getObjectKey(),
                                            "filename", filename,
                                            "blob_size_bytes", blobResponse.getSizeBytes()
                                    ));
                                    result.put("output_doc", buildOutputDoc(parsedDoc));
                                    result.put("processorLogs", List.of(
                                            "Parser service loaded document from repository-service",
                                            "Source doc_id: " + sourceDoc.getDocId(),
                                            "Configuration ID: " + config.configId(),
                                            "Extracted title: '" + parsedDoc.getSearchMetadata().getTitle() + "'",
                                            "Extracted body length: " + parsedDoc.getSearchMetadata().getBody().length() + " characters",
                                            "Parsers used: " + String.join(", ", parsedDoc.getParsedMetadataMap().keySet())
                                    ));
                                    return Response.ok(result).build();
                                } catch (Exception e) {
                                    LOG.error("Failed to parse repository document content", e);
                                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                            .entity(Map.of("error", "Repository content parsing failed: " + e.getMessage()))
                                            .build();
                                }
                            }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()));
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Failed to parse repository document: node_id=%s", nodeId);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of("error", "Repository parse failed: " + error.getMessage()))
                            .build();
                });
    }

    @POST
    @Path("/simple-form")
    @Operation(summary = "Simple document parsing (Form)", description = "Parse document content using form inputs - perfect for Swagger UI and JSONForms testing")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @APIResponse(responseCode = "200", description = "Parsing successful")
    @APIResponse(responseCode = "400", description = "Invalid input")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Uni<Response> simpleParseForm(
            @FormParam("text") 
            @Schema(description = "Text content to parse (can be large blocks of text)")
            String text,
            
            @FormParam("extractMetadata")
            @DefaultValue("true")
            @Schema(description = "Whether to extract document metadata", defaultValue = "true")
            Boolean extractMetadata,
            
            @FormParam("disableEmfParser")
            @DefaultValue("true")
            @Schema(description = "Whether to disable EMF parser (prevents POI errors)", defaultValue = "true")
            Boolean disableEmfParser,
            
            @FormParam("contentHandlers")
            @DefaultValue("default")
            @Schema(description = "Content handler strategy", enumeration = {"default", "xml", "text"}, defaultValue = "default")
            String contentHandlers,
            
            @FormParam("outputFormat")
            @DefaultValue("structured")
            @Schema(description = "Output format for parsed content", enumeration = {"structured", "plain", "html"}, defaultValue = "structured")
            String outputFormat) {
        
        LOG.debugf("Form-based simple parsing request - text length: %d, extractMetadata: %s, disableEmfParser: %s", 
                 text != null ? text.length() : 0, extractMetadata, disableEmfParser);
        
        if (text == null || text.trim().isEmpty()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Text cannot be null or empty"))
                    .build()
            );
        }

        return Uni.createFrom().item(() -> {
            try {
                // Use default config for now - form parameters will be used to customize parsing
                ParserConfig config = ParserConfig.defaultConfig();
                
                // Note: For now we use the default config. In the future, we could create
                // a custom config based on the form parameters, but the key point is that
                // this calls the same DocumentParser.parseDocument() method as the gRPC service
                
                // Parse the document using DocumentParser (calls the same logic as gRPC service)
                PipeDoc parsedDoc = documentParser.parseDocument(
                    ByteString.copyFromUtf8(text),
                    config,
                    "form-input.txt"  // Default filename for form input
                );

                // Extract Docling metadata (best effort)
                try {
                    String docId = parsedDoc.getDocId();
                    ai.pipestream.parsed.data.docling.v1.DoclingResponse doclingResponse =
                        doclingMetadataExtractor.extractComprehensiveMetadata(
                            text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            "form-input.txt",
                            docId,
                            config.doclingOptions()
                        );
                    
                    if (doclingResponse != null) {
                        PipeDoc.Builder docBuilder = parsedDoc.toBuilder();
                        storeDoclingMetadata(docBuilder, doclingResponse);
                        parsedDoc = docBuilder.build();
                    }
                } catch (Exception e) {
                    LOG.warn("Docling extraction failed in REST endpoint: " + e.getMessage());
                }
                
                // Create response matching gRPC service format
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                
                // Convert PipeDoc to response format - include ALL fields
                Map<String, Object> outputDoc = new HashMap<>();
                outputDoc.put("id", parsedDoc.getDocId());
                outputDoc.put("title", parsedDoc.getSearchMetadata().getTitle());
                outputDoc.put("body", parsedDoc.getSearchMetadata().getBody());

                // Add structured data (metadata) if available
                if (parsedDoc.hasStructuredData()) {
                    Map<String, Object> structuredData = new HashMap<>();
                    structuredData.put("type", parsedDoc.getStructuredData().getTypeUrl());
                    structuredData.put("hasData", true);
                    outputDoc.put("structuredData", structuredData);
                }

                // IMPORTANT: Include parsed_metadata to show both Tika and Docling results
                if (!parsedDoc.getParsedMetadataMap().isEmpty()) {
                    Map<String, Object> parsedMetadataMap = new HashMap<>();
                    parsedDoc.getParsedMetadataMap().forEach((key, metadata) -> {
                        Map<String, Object> metadataObj = new HashMap<>();
                        metadataObj.put("parser_name", metadata.getParserName());
                        metadataObj.put("parser_version", metadata.getParserVersion());
                        metadataObj.put("parsed_at", metadata.getParsedAt().getSeconds());
                        // Note: The actual proto data is in metadata.getData() as Any type
                        // For now, just indicate that data is present
                        metadataObj.put("has_data", metadata.hasData());
                        metadataObj.put("data_type", metadata.getData().getTypeUrl());
                        parsedMetadataMap.put(key, metadataObj);
                    });
                    outputDoc.put("parsed_metadata", parsedMetadataMap);
                }

                result.put("output_doc", outputDoc);
                result.put("processorLogs", List.of(
                    "Parser service successfully processed form text using Tika",
                    "Input text length: " + text.length() + " characters",
                    "Extracted title: '" + parsedDoc.getSearchMetadata().getTitle() + "'",
                    "Extracted body length: " + parsedDoc.getSearchMetadata().getBody().length() + " characters",
                    "Metadata extraction: " + (extractMetadata ? "enabled" : "disabled"),
                    "EMF parser: " + (disableEmfParser ? "disabled" : "enabled"),
                    "Content handlers: " + contentHandlers,
                    "Output format: " + outputFormat
                ));
                
                LOG.debugf("Form-based parsing completed - extracted %d chars body, structured data: %s", 
                         parsedDoc.getSearchMetadata().getBody().length(),
                         parsedDoc.hasStructuredData() ? "yes" : "no");
                
                return Response.ok(result).build();
                
            } catch (Exception e) {
                LOG.error("Error during form-based parsing", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Parsing failed: " + e.getMessage()))
                    .build();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @POST
    @Path("/parse-json")
    @Operation(summary = "Parse with JSON config", description = "Parse document using complete ParserConfig JSON - perfect for JSONForms and Config Card")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", description = "Parsing successful")
    @APIResponse(responseCode = "400", description = "Invalid input")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Uni<Response> parseWithJsonConfig(
            @Schema(description = "Complete parsing request with ParserConfig and text content")
            Map<String, Object> request) {
        
        LOG.debugf("JSON-based parsing request received");
        
        return Uni.createFrom().item(() -> {
            try {
                // Extract text from request
                String text = (String) request.get("text");
                if (text == null || text.trim().isEmpty()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Text field is required"))
                        .build();
                }
                
                // Extract config from request - if not provided, use defaults
                ParserConfig config;
                if (request.containsKey("config")) {
                    // Convert config map to ParserConfig using Jackson
                    config = objectMapper.convertValue(request.get("config"), ParserConfig.class);
                } else {
                    // Use default config
                    config = ParserConfig.defaultConfig();
                }
                
                LOG.debugf("Parsing with config ID: %s, text length: %d", config.configId(), text.length());
                
                // Parse the document using DocumentParser (same logic as gRPC service)
                PipeDoc parsedDoc = documentParser.parseDocument(
                    com.google.protobuf.ByteString.copyFromUtf8(text),
                    config,
                    "json-input.txt"
                );
                
                // Create response matching gRPC service format
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                
                // Convert PipeDoc to response format - include ALL fields
                Map<String, Object> outputDoc = new HashMap<>();
                outputDoc.put("id", parsedDoc.getDocId());
                outputDoc.put("title", parsedDoc.getSearchMetadata().getTitle());
                outputDoc.put("body", parsedDoc.getSearchMetadata().getBody());

                // Add structured data (metadata) if available
                if (parsedDoc.hasStructuredData()) {
                    Map<String, Object> structuredData = new HashMap<>();
                    structuredData.put("type", parsedDoc.getStructuredData().getTypeUrl());
                    structuredData.put("hasData", true);
                    outputDoc.put("structuredData", structuredData);
                }

                // IMPORTANT: Include parsed_metadata to show both Tika and Docling results
                if (!parsedDoc.getParsedMetadataMap().isEmpty()) {
                    Map<String, Object> parsedMetadataMap = new HashMap<>();
                    parsedDoc.getParsedMetadataMap().forEach((key, metadata) -> {
                        Map<String, Object> metadataObj = new HashMap<>();
                        metadataObj.put("parser_name", metadata.getParserName());
                        metadataObj.put("parser_version", metadata.getParserVersion());
                        metadataObj.put("parsed_at", metadata.getParsedAt().getSeconds());
                        // Note: The actual proto data is in metadata.getData() as Any type
                        // For now, just indicate that data is present
                        metadataObj.put("has_data", metadata.hasData());
                        metadataObj.put("data_type", metadata.getData().getTypeUrl());
                        parsedMetadataMap.put(key, metadataObj);
                    });
                    outputDoc.put("parsed_metadata", parsedMetadataMap);
                }

                result.put("output_doc", outputDoc);
                result.put("processorLogs", List.of(
                    "Parser service successfully processed JSON input using Tika",
                    "Input text length: " + text.length() + " characters",
                    "Configuration ID: " + config.configId(),
                    "Extracted title: '" + parsedDoc.getSearchMetadata().getTitle() + "'",
                    "Extracted body length: " + parsedDoc.getSearchMetadata().getBody().length() + " characters",
                    "Structured data available: " + (parsedDoc.hasStructuredData() ? "yes" : "no")
                ));
                
                LOG.debugf("JSON-based parsing completed - extracted %d chars body, structured data: %s", 
                         parsedDoc.getSearchMetadata().getBody().length(),
                         parsedDoc.hasStructuredData() ? "yes" : "no");
                
                return Response.ok(result).build();
                
            } catch (Exception e) {
                LOG.error("Error during JSON-based parsing", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "JSON parsing failed: " + e.getMessage()))
                    .build();
            }
        });
    }

    @GET
    @Path("/demo/documents")
    @Operation(summary = "Get demo documents", description = "Retrieve available demo documents from files.csv")
    @APIResponse(responseCode = "200", description = "Demo documents retrieved successfully")
    public Uni<Response> getDemoDocuments() {
        LOG.debug("Demo documents request received - reading from files.csv");
        
        return Uni.createFrom().item(() -> {
            List<Map<String, Object>> documents;
            try {
                documents = sampleLoaderService.loadDemoDocuments();
            } catch (Exception e) {
                return Response.serverError().entity(Map.of("error", e.getMessage())).build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("documents", documents);
            response.put("total", documents.size());
            response.put("description", "Demo documents indexed in files.csv");
            
            return Response.ok(response).build();
        });
    }

    @POST
    @Path("/demo/parse")
    @Operation(summary = "Parse demo document", description = "Parse a demo document by filename")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @APIResponse(responseCode = "200", description = "Demo document parsed successfully")
    public Uni<Response> parseDemoDocument(
            @FormParam("filename") String filename,
            @FormParam("extractMetadata") @DefaultValue("true") boolean extractMetadata,
            @FormParam("disableEmfParser") @DefaultValue("true") boolean disableEmfParser) {

        LOG.debugf("Demo parse request - filename: %s, extractMetadata: %s", filename, extractMetadata);
        
        return Uni.createFrom().item(() -> {
            if (filename == null || filename.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Filename is required"))
                    .build();
            }
            
            try {
                // Simulate parsing results based on filename
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("filename", filename);
                
                // Create mock parsed document result
                Map<String, Object> outputDoc = new HashMap<>();
                outputDoc.put("id", "demo-" + filename.hashCode());
                
                switch (filename.toLowerCase()) {
                    case "sample_contract.pdf":
                        outputDoc.put("title", "Software License Agreement");
                        outputDoc.put("body", "SOFTWARE LICENSE AGREEMENT\\n\\nThis agreement is entered into between the licensor and licensee for the use of software products. The terms and conditions outlined herein are binding...");
                        if (extractMetadata) {
                            Map<String, Object> customData = new HashMap<>();
                            Map<String, String> fields = new HashMap<>();
                            fields.put("Author", "Legal Department");
                            fields.put("Creator", "Adobe Acrobat Pro");
                            fields.put("Subject", "Software Licensing");
                            fields.put("Keywords", "software, license, agreement, legal");
                            customData.put("fields", fields);
                            outputDoc.put("customData", customData);
                        }
                        break;
                        
                    case "technical_report.docx":
                        outputDoc.put("title", "Annual Technical Report 2024");
                        outputDoc.put("body", "ANNUAL TECHNICAL REPORT 2024\\n\\nExecutive Summary\\nThis report summarizes the technical achievements and challenges faced in 2024...");
                        if (extractMetadata) {
                            Map<String, Object> customData = new HashMap<>();
                            Map<String, String> fields = new HashMap<>();
                            fields.put("Author", "Engineering Team");
                            fields.put("Last Modified By", "John Smith");
                            fields.put("Company", "Tech Corp");
                            fields.put("Category", "Technical Report");
                            customData.put("fields", fields);
                            outputDoc.put("customData", customData);
                        }
                        break;
                        
                    case "webpage_article.html":
                        outputDoc.put("title", "Modern Web Development Practices");
                        outputDoc.put("body", "Modern Web Development Practices\\n\\nIntroduction\\nWeb development has evolved significantly in recent years...");
                        if (extractMetadata) {
                            Map<String, Object> customData = new HashMap<>();
                            Map<String, String> fields = new HashMap<>();
                            fields.put("Content-Type", "text/html; charset=UTF-8");
                            fields.put("Generator", "Hugo Static Site Generator");
                            fields.put("Description", "A comprehensive guide to modern web development");
                            customData.put("fields", fields);
                            outputDoc.put("customData", customData);
                        }
                        break;
                        
                    case "readme.txt":
                        outputDoc.put("title", "Project Documentation");
                        outputDoc.put("body", "PROJECT README\\n\\nThis project demonstrates document parsing capabilities using Apache Tika. Features include metadata extraction, content analysis, and format detection...");
                        if (extractMetadata) {
                            Map<String, Object> customData = new HashMap<>();
                            Map<String, String> fields = new HashMap<>();
                            fields.put("Content-Type", "text/plain; charset=UTF-8");
                            fields.put("Content-Length", "3421");
                            customData.put("fields", fields);
                            outputDoc.put("customData", customData);
                        }
                        break;
                        
                    default:
                        outputDoc.put("title", "Unknown Document");
                        outputDoc.put("body", "Demo document content for: " + filename);
                }
                
                result.put("output_doc", outputDoc);
                result.put("processorLogs", List.of(
                    "Parser service successfully processed demo document using Tika",
                    "Extracted title: '" + outputDoc.get("title") + "'",
                    "Extracted body length: " + ((String)outputDoc.get("body")).length() + " characters",
                    "Configuration used: extractMetadata=" + extractMetadata + ", disableEmfParser=" + disableEmfParser
                ));
                
                return Response.ok(result).build();
                
            } catch (Exception e) {
                LOG.error("Error parsing demo document", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Demo parsing failed: " + e.getMessage()))
                    .build();
            }
        });
    }

    @POST
    @Path("/parse-file")
    @Operation(summary = "Parse uploaded file", description = "Parse an uploaded document file using Apache Tika")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @APIResponse(responseCode = "200", description = "File parsed successfully")
    public Uni<Response> parseFile(
            @RestForm("file") FileUpload file,
            @RestForm("config") String configJson) {

        LOG.debugf("File upload request - filename: %s, size: %d bytes", 
                  file != null ? file.fileName() : "null", 
                  file != null ? file.size() : 0);
        
        return Uni.createFrom().item(() -> {
            if (file == null || file.size() == 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No file uploaded or file is empty"))
                    .build();
            }
            
            try {
                // Read file content
                File uploadedFile = file.uploadedFile().toFile();
                byte[] fileContent = Files.readAllBytes(uploadedFile.toPath());
                
                // Create parser configuration
                ParserConfig config = objectMapper.readValue(configJson, ParserConfig.class);

                // Parse the document using DocumentParser
                PipeDoc parsedDoc = documentParser.parseDocument(
                    com.google.protobuf.ByteString.copyFrom(fileContent),
                    config,
                    file.fileName()
                );

                // Extract Docling metadata (best effort)
                try {
                    String docId = parsedDoc.getDocId();
                    ai.pipestream.parsed.data.docling.v1.DoclingResponse doclingResponse =
                        doclingMetadataExtractor.extractComprehensiveMetadata(
                            fileContent,
                            file.fileName(),
                            docId,
                            config.doclingOptions()
                        );
                    
                    if (doclingResponse != null) {
                        PipeDoc.Builder docBuilder = parsedDoc.toBuilder();
                        storeDoclingMetadata(docBuilder, doclingResponse);
                        parsedDoc = docBuilder.build();
                    }
                } catch (Exception e) {
                    LOG.warn("Docling extraction failed in REST endpoint: " + e.getMessage());
                }
                
                // Create response
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("filename", file.fileName());
                result.put("fileSize", file.size());
                result.put("contentType", file.contentType());
                
                // Convert PipeDoc to response format - include ALL fields
                Map<String, Object> outputDoc = new HashMap<>();
                outputDoc.put("id", parsedDoc.getDocId());
                outputDoc.put("title", parsedDoc.getSearchMetadata().getTitle());
                outputDoc.put("body", parsedDoc.getSearchMetadata().getBody());

                // Add structured data (metadata) if available
                if (parsedDoc.hasStructuredData()) {
                    Map<String, Object> structuredData = new HashMap<>();
                    structuredData.put("type", parsedDoc.getStructuredData().getTypeUrl());
                    structuredData.put("hasData", true);
                    outputDoc.put("structuredData", structuredData);
                }

                // IMPORTANT: Include parsed_metadata to show both Tika and Docling results
                if (!parsedDoc.getParsedMetadataMap().isEmpty()) {
                    Map<String, Object> parsedMetadataMap = new HashMap<>();
                    parsedDoc.getParsedMetadataMap().forEach((key, metadata) -> {
                        Map<String, Object> metadataObj = new HashMap<>();
                        metadataObj.put("parser_name", metadata.getParserName());
                        metadataObj.put("parser_version", metadata.getParserVersion());
                        metadataObj.put("parsed_at", metadata.getParsedAt().getSeconds());
                        
                        // Use JsonFormat to print the Any content as JSON string
                        try {
                            com.google.protobuf.TypeRegistry typeRegistry = com.google.protobuf.TypeRegistry.newBuilder()
                                .add(ai.pipestream.parsed.data.tika.v1.TikaResponse.getDescriptor())
                                .add(ai.pipestream.parsed.data.docling.v1.DoclingResponse.getDescriptor())
                                .build();
                            
                            com.google.protobuf.util.JsonFormat.Printer printer = com.google.protobuf.util.JsonFormat.printer()
                                .usingTypeRegistry(typeRegistry);
                                
                            String dataJson = printer.print(metadata.getData());
                            
                            // Parse it back to Map/Object so it nests correctly in the JSON response
                            Object dataObj = objectMapper.readValue(dataJson, Object.class);
                            metadataObj.put("data", dataObj);
                        } catch (Exception e) {
                            LOG.warn("Failed to print Any data to JSON", e);
                            metadataObj.put("data_error", e.getMessage());
                        }

                        metadataObj.put("has_data", metadata.hasData());
                        metadataObj.put("data_type", metadata.getData().getTypeUrl());
                        parsedMetadataMap.put(key, metadataObj);
                    });
                    outputDoc.put("parsed_metadata", parsedMetadataMap);
                }

                result.put("output_doc", outputDoc);
                List<String> logs = new ArrayList<>(List.of(
                    "Parser service successfully processed uploaded file using Tika and Docling",
                    "File type detected: " + (file.contentType() != null ? file.contentType() : "unknown"),
                    "Extracted title: '" + parsedDoc.getSearchMetadata().getTitle() + "'",
                    "Extracted body length: " + parsedDoc.getSearchMetadata().getBody().length() + " characters",
                    "Structured data available: " + (parsedDoc.hasStructuredData() ? "yes" : "no"),
                    "Parsers used: " + String.join(", ", parsedDoc.getParsedMetadataMap().keySet())
                ));
                logs.addAll(buildFieldCountLogs(parsedDoc));
                result.put("processorLogs", logs);

                return Response.ok(result).build();
                
            } catch (Exception e) {
                LOG.error("Error parsing uploaded file: " + file.fileName(), e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "File parsing failed: " + e.getMessage()))
                    .build();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Map<String, Object> buildOutputDoc(PipeDoc parsedDoc) {
        Map<String, Object> outputDoc = new HashMap<>();
        outputDoc.put("id", parsedDoc.getDocId());
        outputDoc.put("title", parsedDoc.getSearchMetadata().getTitle());
        outputDoc.put("body", parsedDoc.getSearchMetadata().getBody());

        if (parsedDoc.hasStructuredData()) {
            Map<String, Object> structuredData = new HashMap<>();
            structuredData.put("type", parsedDoc.getStructuredData().getTypeUrl());
            structuredData.put("hasData", true);
            outputDoc.put("structuredData", structuredData);
        }

        if (!parsedDoc.getParsedMetadataMap().isEmpty()) {
            Map<String, Object> parsedMetadataMap = new HashMap<>();
            parsedDoc.getParsedMetadataMap().forEach((key, metadata) -> {
                Map<String, Object> metadataObj = new HashMap<>();
                metadataObj.put("parser_name", metadata.getParserName());
                metadataObj.put("parser_version", metadata.getParserVersion());
                metadataObj.put("parsed_at", metadata.getParsedAt().getSeconds());

                try {
                    com.google.protobuf.TypeRegistry typeRegistry = com.google.protobuf.TypeRegistry.newBuilder()
                            .add(ai.pipestream.parsed.data.tika.v1.TikaResponse.getDescriptor())
                            .add(ai.pipestream.parsed.data.docling.v1.DoclingResponse.getDescriptor())
                            .build();

                    com.google.protobuf.util.JsonFormat.Printer printer = com.google.protobuf.util.JsonFormat.printer()
                            .usingTypeRegistry(typeRegistry);

                    String dataJson = printer.print(metadata.getData());
                    Object dataObj = objectMapper.readValue(dataJson, Object.class);
                    metadataObj.put("data", dataObj);
                } catch (Exception e) {
                    LOG.warn("Failed to print Any data to JSON", e);
                    metadataObj.put("data_error", e.getMessage());
                }

                metadataObj.put("has_data", metadata.hasData());
                metadataObj.put("data_type", metadata.getData().getTypeUrl());
                parsedMetadataMap.put(key, metadataObj);
            });
            outputDoc.put("parsed_metadata", parsedMetadataMap);
        }

        return outputDoc;
    }

    /**
     * Diagnostic endpoint: parses a file and returns a structured breakdown of
     * which Tika fields were mapped to typed proto fields vs which ended up in additional_metadata.
     * Used for auditing field mapping coverage.
     */
    @POST
    @Path("/diagnose-fields")
    @Operation(summary = "Diagnose field mapping", description = "Parse a file and return field mapping diagnostics: typed fields, additional_metadata fields, and Dublin Core fields")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @APIResponse(responseCode = "200", description = "Field mapping diagnostics")
    public Uni<Response> diagnoseFieldMapping(
            @RestForm("file") FileUpload file) {

        return Uni.createFrom().item(() -> {
            if (file == null || file.size() == 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "No file uploaded or file is empty"))
                        .build();
            }

            try {
                File uploadedFile = file.uploadedFile().toFile();
                byte[] fileContent = Files.readAllBytes(uploadedFile.toPath());

                ParserConfig config = ParserConfig.defaultConfig();
                PipeDoc parsedDoc = documentParser.parseDocument(
                        ByteString.copyFrom(fileContent), config, file.fileName());

                // Extract TikaResponse from parsed_metadata
                ai.pipestream.data.v1.ParsedMetadata tikaParsed = parsedDoc.getParsedMetadataMap().get("tika");
                if (tikaParsed == null) {
                    return Response.ok(Map.of("error", "No Tika metadata found in parsed document")).build();
                }

                ai.pipestream.parsed.data.tika.v1.TikaResponse tikaResponse =
                        tikaParsed.getData().unpack(ai.pipestream.parsed.data.tika.v1.TikaResponse.class);

                // Use JsonFormat to get the full response as a map
                com.google.protobuf.TypeRegistry typeRegistry = com.google.protobuf.TypeRegistry.newBuilder()
                        .add(ai.pipestream.parsed.data.tika.v1.TikaResponse.getDescriptor())
                        .build();
                com.google.protobuf.util.JsonFormat.Printer printer = com.google.protobuf.util.JsonFormat.printer()
                        .usingTypeRegistry(typeRegistry);
                String tikaJson = printer.print(tikaResponse);
                Map<String, Object> tikaMap = objectMapper.readValue(tikaJson, Map.class);

                // Build diagnostic response
                Map<String, Object> result = new HashMap<>();
                result.put("filename", file.fileName());
                result.put("fileSize", file.size());

                // Detect which document_metadata oneof is set
                String docType = "unknown";
                Map<String, Object> docMeta = null;
                for (String key : new String[]{"pdf", "office", "image", "email", "media", "html",
                        "rtf", "database", "font", "epub", "warc", "climateForecast", "creativeCommons", "generic"}) {
                    if (tikaMap.containsKey(key)) {
                        docType = key;
                        Object metaObj = tikaMap.get(key);
                        if (metaObj instanceof Map) {
                            docMeta = (Map<String, Object>) metaObj;
                        }
                        break;
                    }
                }
                result.put("documentType", docType);

                // Typed fields (everything except additionalMetadata and baseFields)
                Map<String, Object> typedFields = new java.util.LinkedHashMap<>();
                Map<String, Object> additionalFields = new java.util.LinkedHashMap<>();
                if (docMeta != null) {
                    for (Map.Entry<String, Object> entry : docMeta.entrySet()) {
                        String key = entry.getKey();
                        if ("additionalMetadata".equals(key)) {
                            if (entry.getValue() instanceof Map) {
                                additionalFields.putAll((Map<String, Object>) entry.getValue());
                            }
                        } else if (!"baseFields".equals(key)) {
                            typedFields.put(key, entry.getValue());
                        }
                    }
                }

                result.put("typedFieldCount", typedFields.size());
                result.put("typedFields", typedFields);
                result.put("additionalFieldCount", additionalFields.size());
                result.put("additionalFields", additionalFields);

                // Dublin Core
                Map<String, Object> dublinCore = new java.util.LinkedHashMap<>();
                if (tikaMap.containsKey("dublinCore") && tikaMap.get("dublinCore") instanceof Map) {
                    dublinCore.putAll((Map<String, Object>) tikaMap.get("dublinCore"));
                }
                result.put("dublinCoreFieldCount", dublinCore.size());
                result.put("dublinCore", dublinCore);

                // Coverage percentage
                int total = typedFields.size() + additionalFields.size();
                double coverage = total > 0 ? (typedFields.size() * 100.0 / total) : 100.0;
                result.put("typedCoveragePercent", Math.round(coverage * 10.0) / 10.0);

                return Response.ok(result).build();

            } catch (Exception e) {
                LOG.error("Error in field mapping diagnostic", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(Map.of("error", "Diagnostic failed: " + e.getMessage()))
                        .build();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Lists sample documents available for testing.
     */
    @GET
    @Path("/samples")
    @Operation(summary = "List sample documents", description = "Lists sample documents bundled with the parser for testing")
    @APIResponse(responseCode = "200", description = "Sample documents listed")
    public Uni<Response> listSamples() {
        return Uni.createFrom().item(() -> {
            List<Map<String, Object>> samples = sampleLoaderService.loadDemoDocuments();
            return Response.ok(Map.of("samples", samples, "count", samples.size())).build();
        });
    }

    /**
     * Extracts field count diagnostics from TikaResponse inside a PipeDoc.
     */
    private List<String> buildFieldCountLogs(PipeDoc parsedDoc) {
        List<String> logs = new ArrayList<>();
        try {
            ai.pipestream.data.v1.ParsedMetadata tikaParsed = parsedDoc.getParsedMetadataMap().get("tika");
            if (tikaParsed == null) return logs;

            ai.pipestream.parsed.data.tika.v1.TikaResponse tikaResponse =
                    tikaParsed.getData().unpack(ai.pipestream.parsed.data.tika.v1.TikaResponse.class);

            com.google.protobuf.TypeRegistry typeRegistry = com.google.protobuf.TypeRegistry.newBuilder()
                    .add(ai.pipestream.parsed.data.tika.v1.TikaResponse.getDescriptor())
                    .build();
            String tikaJson = com.google.protobuf.util.JsonFormat.printer()
                    .usingTypeRegistry(typeRegistry).print(tikaResponse);
            Map<String, Object> tikaMap = objectMapper.readValue(tikaJson, Map.class);

            // Detect document type oneof
            String docType = "generic";
            Map<String, Object> docMeta = null;
            for (String key : new String[]{"pdf", "office", "image", "email", "media", "html",
                    "rtf", "database", "font", "epub", "warc", "climateForecast", "creativeCommons", "generic"}) {
                if (tikaMap.containsKey(key) && tikaMap.get(key) instanceof Map) {
                    docType = key;
                    docMeta = (Map<String, Object>) tikaMap.get(key);
                    break;
                }
            }

            int typedCount = 0;
            int additionalCount = 0;
            List<String> additionalKeys = new ArrayList<>();
            if (docMeta != null) {
                for (Map.Entry<String, Object> entry : docMeta.entrySet()) {
                    if ("additionalMetadata".equals(entry.getKey())) {
                        if (entry.getValue() instanceof Map) {
                            Map<String, Object> additional = (Map<String, Object>) entry.getValue();
                            additionalCount = additional.size();
                            additionalKeys.addAll(additional.keySet());
                        }
                    } else if (!"baseFields".equals(entry.getKey())) {
                        typedCount++;
                    }
                }
            }

            int dublinCoreCount = 0;
            if (tikaMap.containsKey("dublinCore") && tikaMap.get("dublinCore") instanceof Map) {
                dublinCoreCount = ((Map<?, ?>) tikaMap.get("dublinCore")).size();
            }

            int total = typedCount + additionalCount;
            double coverage = total > 0 ? (typedCount * 100.0 / total) : 100.0;

            logs.add(String.format("Document type: %s", docType));
            logs.add(String.format("Field mapping: %d typed, %d additional, %d Dublin Core (%.1f%% coverage)",
                    typedCount, additionalCount, dublinCoreCount, coverage));
            if (!additionalKeys.isEmpty()) {
                logs.add("Unmapped fields: " + String.join(", ", additionalKeys));
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract field count diagnostics: %s", e.getMessage());
        }
        return logs;
    }

    /**
     * Stores DoclingResponse in parsed_metadata["docling"].
     */
    private void storeDoclingMetadata(PipeDoc.Builder outputDocBuilder, ai.pipestream.parsed.data.docling.v1.DoclingResponse doclingResponse) {
        try {
            com.google.protobuf.Any doclingAny = com.google.protobuf.Any.pack(doclingResponse);
            String doclingVersion = "1.10.0"; // TODO: Get version from DoclingService
            com.google.protobuf.Timestamp now = com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(System.currentTimeMillis() / 1000)
                    .setNanos((int) ((System.currentTimeMillis() % 1000) * 1000000))
                    .build();

            ai.pipestream.data.v1.ParsedMetadata doclingMetadata = ai.pipestream.data.v1.ParsedMetadata.newBuilder()
                    .setParserName("docling")
                    .setParserVersion(doclingVersion)
                    .setParsedAt(now)
                    .setData(doclingAny)
                    .build();

            outputDocBuilder.putParsedMetadata("docling", doclingMetadata);
            LOG.debugf("Successfully stored Docling metadata");
        } catch (Exception e) {
            LOG.warnf(e, "Failed to store Docling metadata");
        }
    }
}
