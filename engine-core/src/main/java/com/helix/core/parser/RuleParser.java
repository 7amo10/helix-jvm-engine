package com.helix.core.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.api.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Parses JSON rule definitions into valid {@link Rule} instances with type mappings and schema validation.
 */
public class RuleParser {

    private static final Logger log = LoggerFactory.getLogger(RuleParser.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parses a raw JSON string into a {@link Rule}.
     *
     * @param jsonContent raw JSON rule content
     * @return parsed Rule object
     * @throws ParseException if JSON is malformed or required schema attributes are missing
     */
    public Rule parse(String jsonContent) throws ParseException {
        if (jsonContent == null || jsonContent.isBlank()) {
            throw new ParseException("JSON content cannot be null or empty");
        }

        try {
            JsonNode rootNode = mapper.readTree(jsonContent);
            return parseJsonNode(rootNode);
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse JSON rule content", e);
            throw new ParseException("Malformed JSON rule content: " + e.getMessage(), e);
        }
    }

    private Rule parseJsonNode(JsonNode rootNode) throws ParseException {
        if (!rootNode.isObject()) {
            throw new ParseException("JSON root element must be a JSON object");
        }

        // Mandatory fields check
        if (!rootNode.hasNonNull("name")) {
            throw new ParseException("Missing required rule attribute: 'name'");
        }
        if (!rootNode.hasNonNull("expression")) {
            throw new ParseException("Missing required rule attribute: 'expression'");
        }

        String name = rootNode.get("name").asText();
        String expression = rootNode.get("expression").asText();
        String version = rootNode.has("version") ? rootNode.get("version").asText() : "1.0.0";
        String description = rootNode.has("description") ? rootNode.get("description").asText() : "";
        String category = rootNode.has("category") ? rootNode.get("category").asText() : "DEFAULT";

        Map<String, Class<?>> inputSchema = new HashMap<>();
        if (rootNode.has("inputSchema") && rootNode.get("inputSchema").isObject()) {
            JsonNode schemaNode = rootNode.get("inputSchema");
            Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String varName = field.getKey();
                String typeStr = field.getValue().asText();
                Class<?> resolvedType = resolveType(typeStr);
                inputSchema.put(varName, resolvedType);
            }
        }

        return new RuleSchema(name, version, description, category, expression, inputSchema);
    }

    /**
     * Resolves type strings to Java primitive/wrapper/Object classes.
     */
    private Class<?> resolveType(String typeStr) throws ParseException {
        if (typeStr == null) {
            throw new ParseException("Input schema variable type cannot be null");
        }
        return switch (typeStr.trim().toLowerCase()) {
            case "int", "integer" -> Integer.class;
            case "double" -> Double.class;
            case "long" -> Long.class;
            case "boolean" -> Boolean.class;
            case "string" -> String.class;
            case "object" -> Object.class;
            default -> {
                try {
                    yield Class.forName(typeStr);
                } catch (ClassNotFoundException e) {
                    throw new ParseException("Unsupported or unknown variable type: " + typeStr, e);
                }
            }
        };
    }
}
