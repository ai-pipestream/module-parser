package io.pipeline.module.parser.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * Advanced parsing features and customizations.
 */
@RegisterForReflection
@Schema(description = "Advanced parsing features and specialized parser configurations")
public record AdvancedOptions(
    
    @JsonProperty("enableGeoTopicParser")
    @Schema(
        description = "Enable geographic location extraction from documents. " +
                     "Useful for processing documents that contain location references, addresses, or geographic data.",
        examples = {"false", "true"},
        defaultValue = "false"
    )
    Boolean enableGeoTopicParser,
    
    @JsonProperty("disableEmfParser")
    @Schema(
        description = "Disable EMF (Enhanced Metafile) parser to avoid assertion errors with problematic Office files. " +
                     "Recommended for batch processing of unknown Office document quality.",
        examples = {"false", "true"},
        defaultValue = "false"
    )
    Boolean disableEmfParser,
    
    @JsonProperty("extractEmbeddedDocs")
    @Schema(
        description = "Extract content from embedded documents (e.g., attachments in PDFs, embedded objects in Office files). " +
                     "May significantly increase processing time for complex documents.",
        examples = {"true", "false"},
        defaultValue = "true"
    )
    Boolean extractEmbeddedDocs,
    
    @JsonProperty("maxRecursionDepth")
    @Schema(
        description = "Maximum depth for parsing nested/embedded documents. " +
                     "Prevents infinite recursion and excessive processing time.",
        examples = {"3", "2", "5"},
        defaultValue = "3"
    )
    @Min(0)
    @Max(10)
    Integer maxRecursionDepth,
    
    @JsonProperty("enableOcrParser")
    @Schema(
        description = "Enable OCR (Optical Character Recognition) for extracting text from images and scanned documents. " +
                     "Requires tesseract to be installed on the system. Significantly increases processing time.",
        examples = {"false", "true"},
        defaultValue = "false",
        hidden = true
    )
    Boolean enableOcrParser,
    
    @JsonProperty("enableScientificParser")
    @Schema(
        description = "Enable scientific document parser for better handling of academic papers, research documents, " +
                     "and documents with complex mathematical notation and scientific formatting.",
        examples = {"false", "true"},
        defaultValue = "false"
    )
    Boolean enableScientificParser,
    
    @JsonProperty("enableGreedyBackoff")
    @Schema(
        description = "Enable greedy backoff parsing strategy. If any enabled parser fails, automatically disable it " +
                     "and retry parsing with remaining parsers until successful or only filename-based title remains. " +
                     "Provides maximum resilience for batch processing unknown document quality.",
        examples = {"false", "true"},
        defaultValue = "false"
    )
    Boolean enableGreedyBackoff
    
) {
    
    /**
     * Creates default advanced options suitable for most use cases.
     */
    public static AdvancedOptions defaultOptions() {
        return new AdvancedOptions(
            false,   // enableGeoTopicParser: disabled (specialized feature)
            false,   // disableEmfParser: enabled (handle all formats)
            true,    // extractEmbeddedDocs: enabled
            3,       // maxRecursionDepth: reasonable limit
            false,   // enableOcrParser: disabled (requires tesseract)
            false,   // enableScientificParser: disabled (specialized feature)
            false    // enableGreedyBackoff: disabled (conservative default)
        );
    }
    
    /**
     * Creates advanced options optimized for processing potentially problematic Office documents.
     */
    public static AdvancedOptions robustOfficeProcessing() {
        return new AdvancedOptions(
            false,   // enableGeoTopicParser: disabled
            true,    // disableEmfParser: disabled to avoid EMF issues
            true,    // extractEmbeddedDocs: enabled
            2,       // maxRecursionDepth: limited for stability
            false,   // enableOcrParser: disabled for speed
            false,   // enableScientificParser: disabled
            true     // enableGreedyBackoff: enabled for robustness
        );
    }
    
    /**
     * Creates advanced options for geographic/location-aware document processing.
     */
    public static AdvancedOptions geoAwareProcessing() {
        return new AdvancedOptions(
            true,    // enableGeoTopicParser: enabled for location extraction
            false,   // disableEmfParser: enabled
            true,    // extractEmbeddedDocs: enabled
            3,       // maxRecursionDepth: standard
            false,   // enableOcrParser: disabled
            false,   // enableScientificParser: disabled
            false    // enableGreedyBackoff: disabled (geo parsing is specialized)
        );
    }
    
    /**
     * Creates advanced options for fast, surface-level processing.
     */
    public static AdvancedOptions fastProcessing() {
        return new AdvancedOptions(
            false,   // enableGeoTopicParser: disabled for speed
            true,    // disableEmfParser: disabled to avoid potential issues
            false,   // extractEmbeddedDocs: disabled for speed
            1,       // maxRecursionDepth: minimal
            false,   // enableOcrParser: disabled for speed
            false,   // enableScientificParser: disabled for speed
            false    // enableGreedyBackoff: disabled for speed
        );
    }
    
    /**
     * Creates advanced options for comprehensive content extraction including OCR.
     */
    public static AdvancedOptions comprehensiveExtraction() {
        return new AdvancedOptions(
            true,    // enableGeoTopicParser: enabled
            false,   // disableEmfParser: enabled
            true,    // extractEmbeddedDocs: enabled
            3,       // maxRecursionDepth: standard
            true,    // enableOcrParser: enabled for images/scanned docs
            true,    // enableScientificParser: enabled for academic docs
            true     // enableGreedyBackoff: enabled for maximum resilience
        );
    }
}