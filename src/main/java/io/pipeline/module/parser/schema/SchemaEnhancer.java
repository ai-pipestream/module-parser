package io.pipeline.module.parser.schema;

import io.pipeline.module.parser.util.DocumentParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enhances JSON schemas with dynamic runtime information that can't be added via annotations.
 */
@ApplicationScoped
public class SchemaEnhancer {
    
    private static final Logger LOG = Logger.getLogger(SchemaEnhancer.class);
    
    @Inject
    ObjectMapper objectMapper;
    
    // Cache the Tika MIME types at startup to avoid repeated calls
    private List<String> cachedMimeTypes;
    
    @PostConstruct
    void init() {
        LOG.info("Initializing SchemaEnhancer - caching Tika MIME types");
        Set<String> mimeTypes = DocumentParser.getSupportedMimeTypes();
        cachedMimeTypes = mimeTypes.stream()
            .sorted()
            .collect(Collectors.toList());
        LOG.infof("Cached %d Tika-supported MIME types for schema enhancement", cachedMimeTypes.size());
    }
    
    /**
     * Enhances a JSON schema string by adding runtime information like Tika MIME types.
     * 
     * @param schemaJson The original JSON schema as a string
     * @return Enhanced JSON schema with x-suggestions added where appropriate
     */
    public String enhanceSchema(String schemaJson) {
        try {
            LOG.debug("Starting schema enhancement for Tika MIME types");
            JsonNode root = objectMapper.readTree(schemaJson);
            if (root instanceof ObjectNode) {
                enhanceNode((ObjectNode) root);
            }
            String enhanced = objectMapper.writeValueAsString(root);
            LOG.debugf("Schema enhancement complete, size changed from %d to %d", 
                      schemaJson.length(), enhanced.length());
            return enhanced;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to enhance schema, returning original");
            return schemaJson;
        }
    }
    
    private void enhanceNode(ObjectNode node) {
        // Handle OpenAPI structure - look for components.schemas
        if (node.has("components") && node.get("components") instanceof ObjectNode) {
            ObjectNode components = (ObjectNode) node.get("components");
            if (components.has("schemas") && components.get("schemas") instanceof ObjectNode) {
                ObjectNode schemas = (ObjectNode) components.get("schemas");
                schemas.fields().forEachRemaining(schemaEntry -> {
                    if (schemaEntry.getValue() instanceof ObjectNode) {
                        enhanceNode((ObjectNode) schemaEntry.getValue());
                    }
                });
            }
        }
        
        // Check if this is a schema node with properties
        if (node.has("properties")) {
            ObjectNode properties = (ObjectNode) node.get("properties");
            properties.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();
                
                // Look for supportedMimeTypes field
                if (fieldName.equals("supportedMimeTypes") && fieldSchema instanceof ObjectNode) {
                    LOG.debugf("Found supportedMimeTypes field, enhancing with Tika MIME types");
                    enhanceMimeTypeField((ObjectNode) fieldSchema);
                }
                
                // Recursively process nested objects
                if (fieldSchema instanceof ObjectNode) {
                    enhanceNode((ObjectNode) fieldSchema);
                }
            });
        }
        
        // Also check array items
        if (node.has("items") && node.get("items") instanceof ObjectNode) {
            enhanceNode((ObjectNode) node.get("items"));
        }
    }
    
    private void enhanceMimeTypeField(ObjectNode fieldSchema) {
        // This field is an array. We need to add suggestions to its "items" schema.
        if (!fieldSchema.has("items") || !(fieldSchema.get("items") instanceof ObjectNode itemsNode)) {
            LOG.warn("supportedMimeTypes field found but does not have proper items structure");
            return; // Not the structure we are looking for
        }

        // Use cached MIME types instead of fetching them every time
        LOG.debugf("Adding %d cached Tika MIME types to schema suggestions", cachedMimeTypes.size());
        
        // Add x-suggestions to the items schema, which is the correct place for autocompletion in arrays.
        ArrayNode suggestions = itemsNode.putArray("x-suggestions");
        for (String mimeType : cachedMimeTypes) {
            suggestions.add(mimeType);
        }
    }
}