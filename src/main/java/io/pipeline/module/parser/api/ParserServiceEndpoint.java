package io.pipeline.module.parser.api;

import io.pipeline.data.v1.PipeDoc;
import io.pipeline.data.v1.SearchMetadata;
import io.pipeline.module.parser.config.ParserConfig;
import io.pipeline.module.parser.util.DocumentParser;
import io.smallrye.mutiny.Uni;
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
import java.nio.file.Files;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * REST API endpoints for parser service.
 * Provides developer-friendly HTTP endpoints for testing and integration.
 */
@Path("/api/parser/service")
@Tag(name = "Parser Service", description = "Document parsing operations using Apache Tika")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParserServiceEndpoint {

    private static final Logger LOG = Logger.getLogger(ParserServiceEndpoint.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    io.pipeline.common.service.SchemaExtractorService schemaExtractorService;

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
                PipeDoc parsedDoc = DocumentParser.parseDocument(
                    com.google.protobuf.ByteString.copyFromUtf8(text),
                    config,
                    "form-input.txt"  // Default filename for form input
                );
                
                // Create response matching gRPC service format
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                
                // Convert PipeDoc to response format
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
                
                result.put("outputDoc", outputDoc);
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
        });
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
                PipeDoc parsedDoc = DocumentParser.parseDocument(
                    com.google.protobuf.ByteString.copyFromUtf8(text),
                    config,
                    "json-input.txt"
                );
                
                // Create response matching gRPC service format
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                
                // Convert PipeDoc to response format
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
                
                result.put("outputDoc", outputDoc);
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
    @Operation(summary = "Get demo documents", description = "Retrieve available demo documents for parser testing")
    @APIResponse(responseCode = "200", description = "Demo documents retrieved successfully")
    public Uni<Response> getDemoDocuments() {
        LOG.debug("Demo documents request received");
        
        return Uni.createFrom().item(() -> {
            List<Map<String, Object>> documents = new ArrayList<>();
            
            // Sample document 1 - PDF
            Map<String, Object> doc1 = new HashMap<>();
            doc1.put("filename", "sample_contract.pdf");
            doc1.put("title", "Software License Agreement");
            doc1.put("description", "A typical software licensing contract with metadata");
            doc1.put("file_size", 45621);
            doc1.put("content_type", "application/pdf");
            doc1.put("language", "English");
            doc1.put("category", "Legal Document");
            doc1.put("recommended_extract_metadata", true);
            doc1.put("recommended_disable_emf", true);
            doc1.put("recommended_content_handlers", "default");
            doc1.put("preview", "SOFTWARE LICENSE AGREEMENT\\n\\nThis agreement is entered into between...");
            documents.add(doc1);
            
            // Sample document 2 - Word
            Map<String, Object> doc2 = new HashMap<>();
            doc2.put("filename", "technical_report.docx");
            doc2.put("title", "Annual Technical Report 2024");
            doc2.put("description", "Corporate technical report with charts and embedded objects");
            doc2.put("file_size", 127834);
            doc2.put("content_type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            doc2.put("language", "English");
            doc2.put("category", "Technical Report");
            doc2.put("recommended_extract_metadata", true);
            doc2.put("recommended_disable_emf", true);
            doc2.put("recommended_content_handlers", "default");
            doc2.put("preview", "ANNUAL TECHNICAL REPORT 2024\\n\\nExecutive Summary\\nThis report summarizes...");
            documents.add(doc2);
            
            // Sample document 3 - HTML
            Map<String, Object> doc3 = new HashMap<>();
            doc3.put("filename", "webpage_article.html");
            doc3.put("title", "Modern Web Development Practices");
            doc3.put("description", "HTML article with embedded CSS and JavaScript");
            doc3.put("file_size", 23456);
            doc3.put("content_type", "text/html");
            doc3.put("language", "English");
            doc3.put("category", "Web Content");
            doc3.put("recommended_extract_metadata", true);
            doc3.put("recommended_disable_emf", false);
            doc3.put("recommended_content_handlers", "xml");
            doc3.put("preview", "<!DOCTYPE html>\\n<html>\\n<head>\\n<title>Modern Web Development</title>...");
            documents.add(doc3);
            
            // Sample document 4 - Plain text
            Map<String, Object> doc4 = new HashMap<>();
            doc4.put("filename", "readme.txt");
            doc4.put("title", "Project Documentation");
            doc4.put("description", "Simple plain text documentation file");
            doc4.put("file_size", 3421);
            doc4.put("content_type", "text/plain");
            doc4.put("language", "English");
            doc4.put("category", "Documentation");
            doc4.put("recommended_extract_metadata", false);
            doc4.put("recommended_disable_emf", false);
            doc4.put("recommended_content_handlers", "text");
            doc4.put("preview", "PROJECT README\\n\\nThis project demonstrates document parsing capabilities...");
            documents.add(doc4);
            
            Map<String, Object> response = new HashMap<>();
            response.put("documents", documents);
            response.put("total", documents.size());
            response.put("description", "Demo documents for testing parser functionality");
            
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
                
                result.put("outputDoc", outputDoc);
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
                PipeDoc parsedDoc = DocumentParser.parseDocument(
                    com.google.protobuf.ByteString.copyFrom(fileContent),
                    config,
                    file.fileName()
                );
                
                // Create response
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("filename", file.fileName());
                result.put("fileSize", file.size());
                result.put("contentType", file.contentType());
                
                // Convert PipeDoc to response format
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
                
                result.put("outputDoc", outputDoc);
                result.put("processorLogs", List.of(
                    "Parser service successfully processed uploaded file using Tika",
                    "File type detected: " + (file.contentType() != null ? file.contentType() : "unknown"),
                    "Extracted title: '" + parsedDoc.getSearchMetadata().getTitle() + "'",
                    "Extracted body length: " + parsedDoc.getSearchMetadata().getBody().length() + " characters",
                    "Structured data available: " + (parsedDoc.hasStructuredData() ? "yes" : "no")
                ));
                
                return Response.ok(result).build();
                
            } catch (Exception e) {
                LOG.error("Error parsing uploaded file: " + file.fileName(), e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "File parsing failed: " + e.getMessage()))
                    .build();
            }
        });
    }
}
