package io.pipeline.module.parser.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Error handling and resilience options for document parsing.
 */
@RegisterForReflection
@Schema(description = "Error handling and resilience configuration for robust document processing")
public record ErrorHandling(
    
    @JsonProperty("ignoreTikaException")
    @Schema(
        description = "Continue processing even if Tika throws parsing exceptions. " +
                     "When true, parsing errors are logged but don't stop the processing pipeline.",
        examples = {"false", "true"},
        defaultValue = "false"
    )
    Boolean ignoreTikaException,
    
    @JsonProperty("fallbackToPlainText")
    @Schema(
        description = "Fall back to plain text extraction when structured parsing fails. " +
                     "Attempts to extract raw text content even when format-specific parsing fails.",
        examples = {"true", "false"},
        defaultValue = "true"
    )
    Boolean fallbackToPlainText,
    
    @JsonProperty("logParsingErrors")
    @Schema(
        description = "Log detailed parsing error information for debugging and monitoring. " +
                     "Includes exception details, document information, and parsing context.",
        examples = {"true", "false"},
        defaultValue = "true"
    )
    Boolean logParsingErrors
    
) {
    
    /**
     * Creates default error handling options with balanced resilience.
     */
    public static ErrorHandling defaultOptions() {
        return new ErrorHandling(
            false,   // ignoreTikaException: fail on exceptions for data quality
            true,    // fallbackToPlainText: attempt recovery
            true     // logParsingErrors: enabled for debugging
        );
    }
    
    /**
     * Creates error handling optimized for maximum resilience in batch processing.
     * Prioritizes processing continuation over individual document quality.
     */
    public static ErrorHandling resilientBatchProcessing() {
        return new ErrorHandling(
            true,    // ignoreTikaException: continue on errors
            true,    // fallbackToPlainText: attempt recovery
            true     // logParsingErrors: enabled for monitoring
        );
    }
    
    /**
     * Creates error handling optimized for strict quality control.
     * Fails fast on any parsing issues to ensure high data quality.
     */
    public static ErrorHandling strictQualityControl() {
        return new ErrorHandling(
            false,   // ignoreTikaException: fail on any errors
            false,   // fallbackToPlainText: no fallbacks, maintain format fidelity
            true     // logParsingErrors: enabled for analysis
        );
    }
    
    /**
     * Creates error handling for production environments with minimal logging.
     * Balances resilience with performance by reducing log verbosity.
     */
    public static ErrorHandling productionOptimized() {
        return new ErrorHandling(
            false,   // ignoreTikaException: fail on exceptions for quality
            true,    // fallbackToPlainText: attempt recovery
            false    // logParsingErrors: disabled for performance
        );
    }
    
    /**
     * Creates error handling for development/debugging with maximum information.
     */
    public static ErrorHandling debugMode() {
        return new ErrorHandling(
            true,    // ignoreTikaException: continue to see all issues
            true,    // fallbackToPlainText: attempt recovery
            true     // logParsingErrors: enabled for debugging
        );
    }
}