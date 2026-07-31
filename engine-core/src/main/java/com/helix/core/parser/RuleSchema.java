package com.helix.core.parser;

import com.helix.api.Rule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Data structure representing a parsed Rule definition with metadata and schema validation.
 */
public class RuleSchema implements Rule {

    private final String name;
    private final String version;
    private final String description;
    private final String category;
    private final String expression;
    private final Map<String, Class<?>> inputSchema;

    public RuleSchema(String name, String version, String description, String category, String expression, Map<String, Class<?>> inputSchema) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.category = category;
        this.expression = expression;
        this.inputSchema = inputSchema != null ? new HashMap<>(inputSchema) : Collections.emptyMap();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String getExpression() {
        return expression;
    }

    @Override
    public Map<String, Class<?>> getInputSchema() {
        return Collections.unmodifiableMap(inputSchema);
    }
}
