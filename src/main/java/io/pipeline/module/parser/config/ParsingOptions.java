package io.pipeline.module.parser.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * Core document parsing configuration options.
 */
@RegisterForReflection
@Schema(description = "Core document parsing configuration settings")
public record ParsingOptions(
    
    @JsonProperty("maxContentLength")
    @Schema(
        description = "Maximum content length to extract from documents (-1 for unlimited). " +
                     "Useful for preventing memory issues with very large documents.",
        examples = {"-1", "1048576", "5242880"},
        defaultValue = "-1"
    )
    Integer maxContentLength,
    
    @JsonProperty("extractMetadata")
    @Schema(
        description = "Whether to extract document metadata such as author, creation date, content type, etc.",
        examples = {"true", "false"},
        defaultValue = "true"
    )
    Boolean extractMetadata,
    
    @JsonProperty("maxMetadataValueLength")
    @Schema(
        description = "Maximum length for individual metadata field values (-1 for unlimited). " +
                     "Prevents excessive metadata from consuming memory.",
        examples = {"10000", "5000", "1000"},
        defaultValue = "10000"
    )
    @Min(-1)
    Integer maxMetadataValueLength,
    
    @JsonProperty("parseTimeoutSeconds")
    @Schema(
        description = "Timeout for document parsing operations in seconds. " +
                     "Prevents hanging on problematic documents.",
        examples = {"30", "60", "120"},
        defaultValue = "60"
    )
    @Min(1)
    @Max(300)
    Integer parseTimeoutSeconds
    
) {
    
    /**
     * Creates default parsing options suitable for most use cases.
     */
    public static ParsingOptions defaultOptions() {
        return new ParsingOptions(
            -1,      // maxContentLength: unlimited
            true,    // extractMetadata: enabled
            10000,   // maxMetadataValueLength: 10KB
            60       // parseTimeoutSeconds: 1 minute
        );
    }
    
    /**
     * Creates parsing options optimized for large document processing.
     */
    public static ParsingOptions largeDocumentOptions() {
        return new ParsingOptions(
            5242880, // maxContentLength: 5MB
            true,    // extractMetadata: enabled
            5000,    // maxMetadataValueLength: 5KB
            120      // parseTimeoutSeconds: 2 minutes
        );
    }
    
    /**
     * Creates parsing options optimized for fast processing with minimal metadata.
     */
    public static ParsingOptions fastProcessingOptions() {
        return new ParsingOptions(
            1048576, // maxContentLength: 1MB
            false,   // extractMetadata: disabled for speed
            1000,    // maxMetadataValueLength: 1KB
            30       // parseTimeoutSeconds: 30 seconds
        );
    }
}