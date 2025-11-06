package io.pipeline.module.parser.config;

import io.pipeline.module.parser.util.DocumentParser;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides MIME type suggestions for OpenAPI schema generation.
 */
public class MimeTypeSuggestions {
    
    /**
     * Gets all Tika-supported MIME types as a list for schema examples.
     * This is used by OpenAPI annotations to provide autocomplete suggestions.
     */
    public static List<String> getAllSupportedMimeTypes() {
        return DocumentParser.getSupportedMimeTypes()
            .stream()
            .sorted()
            .collect(Collectors.toList());
    }
    
    /**
     * Gets common MIME types that are frequently used.
     */
    public static List<String> getCommonMimeTypes() {
        return List.of(
            "application/pdf",
            "application/json",
            "application/xml",
            "text/plain",
            "text/html",
            "text/csv",
            "image/jpeg",
            "image/png",
            "image/gif",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint"
        );
    }
}