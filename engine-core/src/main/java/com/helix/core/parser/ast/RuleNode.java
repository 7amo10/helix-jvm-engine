package com.helix.core.parser.ast;

import com.helix.api.Rule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Composite AST node encapsulating a complete compiled rule definition and its parsed AST root.
 */
public class RuleNode implements Rule, ExpressionNode {

    private final String name;
    private final String version;
    private final String description;
    private final String category;
    private final String expression;
    private final Map<String, Class<?>> inputSchema;
    private final ExpressionNode rootNode;

    public RuleNode(String name, String expression, ExpressionNode rootNode) {
        this(name, "1.0.0", "", "DEFAULT", expression, Collections.emptyMap(), rootNode);
    }

    public RuleNode(String name, String expression, Map<String, Class<?>> inputSchema, ExpressionNode rootNode) {
        this(name, "1.0.0", "", "DEFAULT", expression, inputSchema, rootNode);
    }

    public RuleNode(String name, String version, String description, String category,
                    String expression, Map<String, Class<?>> inputSchema, ExpressionNode rootNode) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.version = version != null ? version : "1.0.0";
        this.description = description != null ? description : "";
        this.category = category != null ? category : "DEFAULT";
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.inputSchema = inputSchema != null ? Collections.unmodifiableMap(new HashMap<>(inputSchema)) : Collections.emptyMap();
        this.rootNode = Objects.requireNonNull(rootNode, "rootNode cannot be null");
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
        return inputSchema;
    }

    public ExpressionNode getRootNode() {
        return rootNode;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return rootNode.accept(visitor);
    }

    @Override
    public String toString() {
        return "RuleNode{name='" + name + "', expression='" + expression + "', root=" + rootNode + "}";
    }
}
