package ai.pipestream.module.parser.config.docling;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The mode for how to handle image references in the document.
 */
@RegisterForReflection
public enum ImageRefMode {

  @JsonProperty("embedded") EMBEDDED,
  @JsonProperty("placeholder") PLACEHOLDER,
  @JsonProperty("referenced") REFERENCED

}
