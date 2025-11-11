package ai.pipestream.module.parser.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RegisterForReflection
@Schema(description = "Options controlling extraction of a neutral DocOutline from documents like EPUB and HTML")
public record OutlineExtractionOptions(
        @JsonProperty("enableEpubOutline")
        @Schema(description = "Populate SearchMetadata.doc_outline from EPUB TOC when available", defaultValue = "true")
        Boolean enableEpubOutline,

        @JsonProperty("enableHtmlOutline")
        @Schema(description = "Populate SearchMetadata.doc_outline from HTML headings (h1..h6)", defaultValue = "true")
        Boolean enableHtmlOutline,

        @JsonProperty("htmlIncludeCss")
        @Schema(description = "Optional CSS selector to include only matched subtrees for heading/link extraction", defaultValue = "")
        String htmlIncludeCss,

        @JsonProperty("htmlExcludeCss")
        @Schema(description = "Optional CSS selector to exclude matched subtrees from heading/link extraction", defaultValue = "")
        String htmlExcludeCss,

        @JsonProperty("htmlStripScripts")
        @Schema(description = "Strip <script>/<noscript> before heading/link extraction", defaultValue = "true")
        Boolean htmlStripScripts,

        @JsonProperty("enableMarkdownOutline")
        @Schema(description = "Populate SearchMetadata.doc_outline and discovered_links from Markdown using flexmark", defaultValue = "true")
        Boolean enableMarkdownOutline,

        @JsonProperty("htmlMinHeadingLevel")
        @Schema(description = "Minimum HTML heading level to include (1..6)", defaultValue = "1")
        @Min(1) @Max(6)
        Integer htmlMinHeadingLevel,

        @JsonProperty("htmlMaxHeadingLevel")
        @Schema(description = "Maximum HTML heading level to include (1..6)", defaultValue = "6")
        @Min(1) @Max(6)
        Integer htmlMaxHeadingLevel,

        @JsonProperty("htmlGenerateIds")
        @Schema(description = "Generate stable section ids when heading has no id attribute", defaultValue = "true")
        Boolean htmlGenerateIds
) {
    public static OutlineExtractionOptions defaultOptions() {
        return new OutlineExtractionOptions(
                true,   // enableEpubOutline
                true,   // enableHtmlOutline
                null,   // htmlIncludeCss
                null,   // htmlExcludeCss
                true,   // htmlStripScripts
                true,   // enableMarkdownOutline
                1,      // htmlMinHeadingLevel
                6,      // htmlMaxHeadingLevel
                true    // htmlGenerateIds
        );
    }
}
