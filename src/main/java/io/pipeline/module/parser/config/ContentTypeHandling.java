package io.pipeline.module.parser.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;

import java.util.List;
import java.util.ArrayList;

/**
 * Content type specific processing options.
 */
@RegisterForReflection
@Schema(description = "Content type specific processing and filtering options")
public record ContentTypeHandling(
    
    @JsonProperty("enableTitleExtraction")
    @Schema(
        description = "Enable document title extraction and post-processing. " +
                     "Attempts to extract meaningful titles from document metadata and content.",
        examples = {"true", "false"},
        defaultValue = "true"
    )
    Boolean enableTitleExtraction,
    
    @JsonProperty("fallbackToFilename")
    @Schema(
        description = "Use filename as title fallback when no title is found in document metadata. " +
                     "Useful for processing files with meaningful filenames.",
        examples = {"true", "false"},
        defaultValue = "true"
    )
    Boolean fallbackToFilename,
    
    @JsonProperty("supportedMimeTypes")
    @Schema(
        description = "List of supported MIME types to process. Empty list means all supported types are allowed. " +
                     "Use this to restrict processing to specific document formats. " +
                     "The system supports all Apache Tika MIME types for document parsing.",
        examples = {
            "[]",
            "[\"application/pdf\"]",
            "[\"application/pdf\", \"application/vnd.openxmlformats-officedocument.wordprocessingml.document\"]",
            "[\"text/plain\", \"text/html\", \"application/json\"]"
        },
        defaultValue = "[]"
    )
    List<String> supportedMimeTypes
    
) {
    
    /**
     * Creates default content type handling options.
     */
    public static ContentTypeHandling defaultOptions() {
        return new ContentTypeHandling(
            true,               // enableTitleExtraction: enabled
            true,               // fallbackToFilename: enabled
            new ArrayList<>()   // supportedMimeTypes: empty (all types allowed)
        );
    }
    
    /**
     * Creates content type handling optimized for PDF processing only.
     */
    public static ContentTypeHandling pdfOnlyProcessing() {
        return new ContentTypeHandling(
            true,               // enableTitleExtraction: enabled
            true,               // fallbackToFilename: enabled
            List.of("application/pdf")
        );
    }
    
    /**
     * Creates content type handling for Microsoft Office documents only.
     */
    public static ContentTypeHandling officeDocumentsOnly() {
        return new ContentTypeHandling(
            true,               // enableTitleExtraction: enabled
            true,               // fallbackToFilename: enabled
            List.of(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",     // .docx
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",  // .pptx
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",          // .xlsx
                "application/msword",           // .doc
                "application/vnd.ms-powerpoint", // .ppt
                "application/vnd.ms-excel"      // .xls
            )
        );
    }
    
    /**
     * Creates content type handling for text-based documents only.
     */
    public static ContentTypeHandling textDocumentsOnly() {
        return new ContentTypeHandling(
            true,               // enableTitleExtraction: enabled
            true,               // fallbackToFilename: enabled
            List.of(
                "text/plain",
                "text/html",
                "text/xml",
                "application/xml",
                "application/json",
                "text/markdown"
            )
        );
    }
    
    /**
     * Creates content type handling with title extraction disabled for performance.
     */
    public static ContentTypeHandling noTitleExtraction() {
        return new ContentTypeHandling(
            false,              // enableTitleExtraction: disabled
            false,              // fallbackToFilename: disabled
            new ArrayList<>()   // supportedMimeTypes: empty (all types allowed)
        );
    }
}