package ai.pipestream.module.parser.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.UUID;

/**
 * Configuration record for parser service.
 * This record serves as the single source of truth for parser configuration schema.
 * The OpenAPI schema is auto-generated from this Java record.
 */
@RegisterForReflection
@Schema(
    name = "ParserConfig", 
    description = "Configuration for document parsing operations using Apache Tika with support for PDFs, Office docs, HTML, and more",
    examples = {
        """
        {
            "config_id": "pdf-extraction-config",
            "parsingOptions": {
                "maxContentLength": 1048576,
                "extractMetadata": true,
                "maxMetadataValueLength": 5000,
                "parseTimeoutSeconds": 30
            },
            "advancedOptions": {
                "enableGeoTopicParser": false,
                "disableEmfParser": true,
                "extractEmbeddedDocs": true,
                "maxRecursionDepth": 2
            },
            "contentTypeHandling": {
                "enableTitleExtraction": true,
                "fallbackToFilename": true,
                "supportedMimeTypes": [
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ]
            },
            "errorHandling": {
                "ignoreTikaException": false,
                "fallbackToPlainText": true,
                "logParsingErrors": true
            }
        }
        """,
        """
        {
            "config_id": "office-docs-config",
            "parsingOptions": {
                "maxContentLength": -1,
                "extractMetadata": true,
                "maxMetadataValueLength": 10000,
                "parseTimeoutSeconds": 60
            },
            "advancedOptions": {
                "enableGeoTopicParser": true,
                "disableEmfParser": true,
                "extractEmbeddedDocs": true,
                "maxRecursionDepth": 3
            },
            "contentTypeHandling": {
                "enableTitleExtraction": true,
                "fallbackToFilename": true,
                "supportedMimeTypes": [
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ]
            },
            "errorHandling": {
                "ignoreTikaException": false,
                "fallbackToPlainText": true,
                "logParsingErrors": true
            }
        }
        """
    }
)
public record ParserConfig(
    
    @JsonProperty("config_id")
    @JsonAlias({"configId"})
    @Schema(
        description = "Unique identifier for this parser configuration", 
        examples = {"pdf-extraction-config", "office-docs-config", "web-content-parser"}
    )
    String configId,
    
    @JsonProperty("parsingOptions")
    @Schema(description = "Core document parsing configuration")
    @Valid
    ParsingOptions parsingOptions,
    
    @JsonProperty("advancedOptions")
    @Schema(description = "Advanced parsing features and customizations")
    @Valid
    AdvancedOptions advancedOptions,
    
    @JsonProperty("contentTypeHandling")
    @Schema(description = "Content type specific processing options")
    @Valid
    ContentTypeHandling contentTypeHandling,
    
    @JsonProperty("errorHandling")
    @Schema(description = "Error handling and resilience options")
    @Valid
    ErrorHandling errorHandling,

    @JsonProperty("outlineExtraction")
    @Schema(description = "DocOutline extraction options for EPUB/HTML and others")
    @Valid
    OutlineExtractionOptions outlineExtraction,

    @JsonProperty("strategy")
    @Schema(description = "Parsing strategy orchestration",
            example = "first-success",
            enumeration = {"first-success", "union"})
    String strategy
    
) {
    
    /**
     * Creates a ParserConfig with auto-generated configId if not provided.
     */
    @JsonCreator
    public static ParserConfig create(
        @JsonProperty("parsingOptions") ParsingOptions parsingOptions,
        @JsonProperty("advancedOptions") AdvancedOptions advancedOptions,
        @JsonProperty("contentTypeHandling") ContentTypeHandling contentTypeHandling,
        @JsonProperty("errorHandling") ErrorHandling errorHandling,
        @JsonProperty("config_id") @JsonAlias({"configId"}) String configId
    ) {
        String finalConfigId = (configId != null && !configId.trim().isEmpty()) ? 
            configId : generateConfigId(parsingOptions, advancedOptions, contentTypeHandling);
        
        return new ParserConfig(
            finalConfigId,
            parsingOptions != null ? parsingOptions : ParsingOptions.defaultOptions(),
            advancedOptions != null ? advancedOptions : AdvancedOptions.defaultOptions(),
            contentTypeHandling != null ? contentTypeHandling : ContentTypeHandling.defaultOptions(),
            errorHandling != null ? errorHandling : ErrorHandling.defaultOptions(),
            OutlineExtractionOptions.defaultOptions(),
            "first-success"
        );
    }
    
    /**
     * Generates a configuration ID based on key parsing settings.
     */
    private static String generateConfigId(ParsingOptions parsingOptions, AdvancedOptions advancedOptions, ContentTypeHandling contentTypeHandling) {
        StringBuilder configIdBuilder = new StringBuilder("parser-");
        
        // Add parsing characteristics
        if (parsingOptions != null) {
            if (parsingOptions.maxContentLength() != null && parsingOptions.maxContentLength() > 0) {
                configIdBuilder.append("limited-");
            }
            if (parsingOptions.extractMetadata() != null && parsingOptions.extractMetadata()) {
                configIdBuilder.append("metadata-");
            }
        }
        
        // Add advanced features
        if (advancedOptions != null) {
            if (advancedOptions.enableGeoTopicParser() != null && advancedOptions.enableGeoTopicParser()) {
                configIdBuilder.append("geo-");
            }
            if (advancedOptions.disableEmfParser() != null && advancedOptions.disableEmfParser()) {
                configIdBuilder.append("noEmf-");
            }
        }
        
        // Add content handling
        if (contentTypeHandling != null && contentTypeHandling.supportedMimeTypes() != null && !contentTypeHandling.supportedMimeTypes().isEmpty()) {
            configIdBuilder.append("filtered-");
        }
        
        // Add random suffix to ensure uniqueness
        configIdBuilder.append(UUID.randomUUID().toString(), 0, 8);
        
        return configIdBuilder.toString();
    }
    
    /**
     * Creates a default parser configuration suitable for most use cases.
     */
    public static ParserConfig defaultConfig() {
        return new ParserConfig(
            "default-parser-" + UUID.randomUUID().toString().substring(0, 8),
            ParsingOptions.defaultOptions(),
            AdvancedOptions.defaultOptions(),
            ContentTypeHandling.defaultOptions(),
            ErrorHandling.defaultOptions(),
            OutlineExtractionOptions.defaultOptions(),
            "first-success"
        );
    }
    
    /**
     * Creates a parser configuration optimized for large document processing.
     * Uses larger content limits and robust error handling for batch processing.
     */
    public static ParserConfig largeDocumentProcessing() {
        return new ParserConfig(
            "large-docs-" + UUID.randomUUID().toString().substring(0, 8),
            ParsingOptions.largeDocumentOptions(),
            AdvancedOptions.robustOfficeProcessing(),
            ContentTypeHandling.defaultOptions(),
            ErrorHandling.resilientBatchProcessing(),
            OutlineExtractionOptions.defaultOptions(),
            "first-success"
        );
    }
    
    /**
     * Creates a parser configuration optimized for fast processing.
     * Minimizes content extraction and uses streamlined options for speed.
     */
    public static ParserConfig fastProcessing() {
        return new ParserConfig(
            "fast-parser-" + UUID.randomUUID().toString().substring(0, 8),
            ParsingOptions.fastProcessingOptions(),
            AdvancedOptions.fastProcessing(),
            ContentTypeHandling.noTitleExtraction(),
            ErrorHandling.productionOptimized(),
            OutlineExtractionOptions.defaultOptions(),
            "first-success"
        );
    }
    
    /**
     * Creates a parser configuration optimized for batch processing.
     * Emphasizes resilience and error recovery for large-scale operations.
     */
    public static ParserConfig batchProcessing() {
        return new ParserConfig(
            "batch-parser-" + UUID.randomUUID().toString().substring(0, 8),
            ParsingOptions.largeDocumentOptions(),
            AdvancedOptions.robustOfficeProcessing(),
            ContentTypeHandling.defaultOptions(),
            ErrorHandling.resilientBatchProcessing(),
            OutlineExtractionOptions.defaultOptions(),
            "first-success"
        );
    }
    
    /**
     * Creates a parser configuration with strict quality control.
     * Fails fast on any parsing issues to ensure high data quality.
     */
    public static ParserConfig strictQualityControl() {
        return new ParserConfig(
            "strict-parser-" + UUID.randomUUID().toString().substring(0, 8),
            ParsingOptions.defaultOptions(),
            AdvancedOptions.defaultOptions(),
            ContentTypeHandling.defaultOptions(),
            ErrorHandling.strictQualityControl(),
            OutlineExtractionOptions.defaultOptions(),
            "first-success"
        );
    }
}