
package io.pipeline.module.parser.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.pipeline.module.parser.util.DocumentParser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SchemaEnhancerTest {

    @Inject
    SchemaEnhancer schemaEnhancer;
    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testEnhanceSchema_addsSuggestionsToMimeTypes() throws Exception {
        // 1. Create a test schema that mimics the structure of the real one
        String originalSchema = "{\"properties\":{\"contentTypeHandling\":{\"type\":\"object\",\"properties\":{\"supportedMimeTypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}}}";

        // 2. Enhance the schema
        String enhancedSchemaJson = schemaEnhancer.enhanceSchema(originalSchema);

        // 3. Parse the enhanced schema
        JsonNode enhancedSchema = objectMapper.readTree(enhancedSchemaJson);

        // 4. Navigate to the 'items' node for supportedMimeTypes
        JsonNode itemsNode = enhancedSchema
                .path("properties")
                .path("contentTypeHandling")
                .path("properties")
                .path("supportedMimeTypes")
                .path("items");

        // 5. Assert that 'x-suggestions' exists on the 'items' node
        assertThat("The 'items' node should now have 'x-suggestions'", itemsNode.has("x-suggestions"), is(true));
        
        // 6. Assert that the suggestions are an array and are not empty
        JsonNode suggestionsNode = itemsNode.get("x-suggestions");
        assertThat("x-suggestions should be an array", suggestionsNode.isArray(), is(true));
        assertThat("x-suggestions array should not be empty", suggestionsNode.isEmpty(), is(false));

        // 7. Assert that a known MIME type is present
        boolean foundPdf = false;
        for (JsonNode suggestion : suggestionsNode) {
            if ("application/pdf".equals(suggestion.asText())) {
                foundPdf = true;
                break;
            }
        }
        assertThat("The suggestions should contain 'application/pdf'", foundPdf, is(true));

        // 8. Assert that 'x-suggestions' is NOT on the array level
        JsonNode arrayNode = enhancedSchema
                .path("properties")
                .path("contentTypeHandling")
                .path("properties")
                .path("supportedMimeTypes");
        assertThat("The array node itself should NOT have 'x-suggestions'", arrayNode.has("x-suggestions"), is(false));
    }

    @Test
    public void testEnhanceSchema_withOpenAPIStructure() throws Exception {
        // Test with full OpenAPI structure like the real schema
        String openApiSchema = """
            {
              "openapi": "3.1.0",
              "components": {
                "schemas": {
                  "ContentTypeHandling": {
                    "type": "object",
                    "properties": {
                      "supportedMimeTypes": {
                        "type": "array",
                        "items": {
                          "type": "string"
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

        String enhancedSchemaJson = schemaEnhancer.enhanceSchema(openApiSchema);
        JsonNode enhancedSchema = objectMapper.readTree(enhancedSchemaJson);

        // Navigate through OpenAPI structure
        JsonNode itemsNode = enhancedSchema
                .path("components")
                .path("schemas")
                .path("ContentTypeHandling")
                .path("properties")
                .path("supportedMimeTypes")
                .path("items");

        assertThat("Should have x-suggestions in items", itemsNode.has("x-suggestions"), is(true));
        assertThat("x-suggestions should be an array", itemsNode.get("x-suggestions").isArray(), is(true));
        
        // Basic sanity: suggestions array exists and includes common types
        ArrayNode suggestions = (ArrayNode) itemsNode.get("x-suggestions");
        assertThat("x-suggestions should not be empty", suggestions.isEmpty(), is(false));
        boolean hasPdf = false;
        for (JsonNode n : suggestions) {
            if ("application/pdf".equals(n.asText())) { hasPdf = true; break; }
        }
        assertThat("x-suggestions should contain application/pdf", hasPdf, is(true));
    }

    @Test
    public void testEnhanceSchema_preservesExistingSchema() throws Exception {
        // Test that enhancement doesn't break existing schema properties
        String originalSchema = """
            {
              "properties": {
                "contentTypeHandling": {
                  "type": "object",
                  "description": "Important description",
                  "properties": {
                    "supportedMimeTypes": {
                      "type": "array",
                      "description": "List of MIME types",
                      "default": [],
                      "items": {
                        "type": "string",
                        "pattern": "^[a-zA-Z]+/[a-zA-Z0-9.+-]+$"
                      }
                    },
                    "otherField": {
                      "type": "boolean"
                    }
                  }
                }
              }
            }
            """;

        String enhancedSchemaJson = schemaEnhancer.enhanceSchema(originalSchema);
        JsonNode enhancedSchema = objectMapper.readTree(enhancedSchemaJson);

        // Check that original properties are preserved
        JsonNode mimeTypesNode = enhancedSchema
                .path("properties")
                .path("contentTypeHandling")
                .path("properties")
                .path("supportedMimeTypes");

        assertThat("Should preserve type", mimeTypesNode.path("type").asText(), is("array"));
        assertThat("Should preserve description", mimeTypesNode.path("description").asText(), 
                  is("List of MIME types"));
        assertThat("Should preserve default", mimeTypesNode.path("default").isArray(), is(true));

        JsonNode itemsNode = mimeTypesNode.path("items");
        assertThat("Should preserve items type", itemsNode.path("type").asText(), is("string"));
        assertThat("Should preserve pattern", itemsNode.path("pattern").asText(), 
                  is("^[a-zA-Z]+/[a-zA-Z0-9.+-]+$"));
        assertThat("Should add x-suggestions", itemsNode.has("x-suggestions"), is(true));

        // Check other field is untouched
        JsonNode otherField = enhancedSchema
                .path("properties")
                .path("contentTypeHandling")
                .path("properties")
                .path("otherField");
        assertThat("Should preserve other fields", otherField.path("type").asText(), is("boolean"));
    }

    @Test
    public void testEnhanceSchema_handlesMultipleMimeTypeFields() throws Exception {
        // Test with multiple fields named supportedMimeTypes at different levels
        String schema = """
            {
              "properties": {
                "config1": {
                  "properties": {
                    "supportedMimeTypes": {
                      "type": "array",
                      "items": {"type": "string"}
                    }
                  }
                },
                "config2": {
                  "properties": {
                    "supportedMimeTypes": {
                      "type": "array",
                      "items": {"type": "string"}
                    }
                  }
                }
              }
            }
            """;

        String enhancedSchemaJson = schemaEnhancer.enhanceSchema(schema);
        JsonNode enhancedSchema = objectMapper.readTree(enhancedSchemaJson);

        // Both should be enhanced
        JsonNode items1 = enhancedSchema
                .path("properties").path("config1").path("properties")
                .path("supportedMimeTypes").path("items");
        JsonNode items2 = enhancedSchema
                .path("properties").path("config2").path("properties")
                .path("supportedMimeTypes").path("items");

        assertThat("First field should have suggestions", items1.has("x-suggestions"), is(true));
        assertThat("Second field should have suggestions", items2.has("x-suggestions"), is(true));
    }

    @Test
    public void testEnhanceSchema_ignoresNonArrayMimeTypeFields() throws Exception {
        // Test that it doesn't try to enhance non-array fields
        String schema = """
            {
              "properties": {
                "supportedMimeTypes": {
                  "type": "string"
                }
              }
            }
            """;

        String enhancedSchemaJson = schemaEnhancer.enhanceSchema(schema);
        JsonNode enhancedSchema = objectMapper.readTree(enhancedSchemaJson);

        JsonNode field = enhancedSchema.path("properties").path("supportedMimeTypes");
        assertThat("Should not add suggestions to non-array field", 
                  field.has("x-suggestions"), is(false));
    }

    @Test
    public void testEnhanceSchema_handlesInvalidJson() throws Exception {
        // Test graceful handling of invalid JSON
        String invalidJson = "{ invalid json }";
        
        String result = schemaEnhancer.enhanceSchema(invalidJson);
        assertThat("Should return original on invalid JSON", result, is(invalidJson));
    }

    @Test
    public void testEnhanceSchema_verifySuggestionContent() throws Exception {
        // Verify the actual content of suggestions
        String schema = """
            {
              "properties": {
                "supportedMimeTypes": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              }
            }
            """;

        String enhancedSchemaJson = schemaEnhancer.enhanceSchema(schema);
        JsonNode enhancedSchema = objectMapper.readTree(enhancedSchemaJson);

        ArrayNode suggestions = (ArrayNode) enhancedSchema
                .path("properties")
                .path("supportedMimeTypes")
                .path("items")
                .path("x-suggestions");

        // Collect all suggestions
        Set<String> suggestionValues = new HashSet<>();
        suggestions.forEach(node -> suggestionValues.add(node.asText()));

        // Verify common MIME types are present
        assertThat("Should contain PDF", suggestionValues.contains("application/pdf"), is(true));
        assertThat("Should contain JSON", suggestionValues.contains("application/json"), is(true));
        assertThat("Should contain plain text", suggestionValues.contains("text/plain"), is(true));
        assertThat("Should contain HTML", suggestionValues.contains("text/html"), is(true));
        assertThat("Should contain JPEG", suggestionValues.contains("image/jpeg"), is(true));
        assertThat("Should contain PNG", suggestionValues.contains("image/png"), is(true));
        
        // Verify Office formats
        assertThat("Should contain DOCX", 
                  suggestionValues.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), 
                  is(true));
        assertThat("Should contain XLSX", 
                  suggestionValues.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), 
                  is(true));
        
        // Verify suggestions are sorted
        String previous = "";
        boolean isSorted = true;
        for (JsonNode suggestion : suggestions) {
            String current = suggestion.asText();
            if (previous.compareTo(current) > 0) {
                isSorted = false;
                break;
            }
            previous = current;
        }
        assertThat("Suggestions should be sorted alphabetically", isSorted, is(true));
    }

    @Test
    public void testEnhanceSchema_performanceWithLargeSchema() throws Exception {
        // Test performance with a large schema
        StringBuilder largeSchema = new StringBuilder("{\"properties\":{");
        for (int i = 0; i < 100; i++) {
            if (i > 0) largeSchema.append(",");
            largeSchema.append("\"field").append(i).append("\":{");
            largeSchema.append("\"type\":\"object\",\"properties\":{");
            largeSchema.append("\"supportedMimeTypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}");
            largeSchema.append("}}");
        }
        largeSchema.append("}}");

        long startTime = System.currentTimeMillis();
        String enhancedSchemaJson = schemaEnhancer.enhanceSchema(largeSchema.toString());
        long endTime = System.currentTimeMillis();

        // Should complete in reasonable time (less than 1 second)
        assertThat("Enhancement should complete quickly", endTime - startTime, lessThan(1000L));

        JsonNode enhancedSchema = objectMapper.readTree(enhancedSchemaJson);
        // Verify at least one field was enhanced
        JsonNode firstField = enhancedSchema
                .path("properties").path("field0").path("properties")
                .path("supportedMimeTypes").path("items");
        assertThat("Should enhance fields in large schema", firstField.has("x-suggestions"), is(true));
    }

    @Test
    public void testEnhanceSchema_handlesArrayWithoutItems() throws Exception {
        // Test edge case where array doesn't have items property
        String schema = """
            {
              "properties": {
                "supportedMimeTypes": {
                  "type": "array"
                }
              }
            }
            """;

        String enhancedSchemaJson = schemaEnhancer.enhanceSchema(schema);
        assertNotNull(enhancedSchemaJson);
        
        // Should not throw exception and return valid JSON
        JsonNode enhancedSchema = objectMapper.readTree(enhancedSchemaJson);
        assertNotNull(enhancedSchema);
    }
}
