package ai.pipestream.module.parser.config.docling;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Document formats supported for output in Docling.
 */
@RegisterForReflection
public enum OutputFormat {

  @JsonProperty("doctags") DOCTAGS,
  @JsonProperty("html") HTML,
  @JsonProperty("html_split_page") HTML_SPLIT_PAGE,
  @JsonProperty("json") JSON,
  @JsonProperty("md") MARKDOWN,
  @JsonProperty("text") TEXT

}
