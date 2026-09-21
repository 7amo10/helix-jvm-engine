package com.helix.core.bytecode;

import com.helix.api.Rule;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.OnnxInferenceNode;
import com.helix.core.parser.ast.VariableNode;

import java.util.Objects;

/**
 * Deterministic node identifier resolver ensuring consistent naming across
 * AST profiling hooks and adaptive AST optimizers.
 */
public final class AstNodeIdResolver {

    private AstNodeIdResolver() {
        // Utility class
    }

    /**
     * Resolves a unique, consistent node identifier for profiling and optimization.
     *
     * @param rule rule definition
     * @param node AST expression node
     * @return deterministic node identifier string
     */
    public static String resolveNodeId(Rule rule, ExpressionNode node) {
        String ruleName = rule != null ? rule.getName() : "";
        return resolveNodeId(ruleName, node);
    }

    /**
     * Resolves a unique, consistent node identifier for profiling and optimization.
     *
     * @param ruleName name of the rule
     * @param node     AST expression node
     * @return deterministic node identifier string
     */
    public static String resolveNodeId(String ruleName, ExpressionNode node) {
        if (node instanceof BinaryOpNode b) {
            if (b.getLeft() instanceof OnnxInferenceNode onnx) {
                return "clause_ml_" + sanitizeName(onnx.getModelName()) + "_" + b.getOperator().name().toLowerCase();
            } else if (b.getRight() instanceof OnnxInferenceNode onnx) {
                return "clause_ml_" + sanitizeName(onnx.getModelName()) + "_" + b.getOperator().name().toLowerCase();
            } else if (b.getLeft() instanceof VariableNode var) {
                return "clause_" + sanitizeName(var.getName()) + "_" + b.getOperator().name().toLowerCase();
            } else if (b.getRight() instanceof VariableNode var) {
                return "clause_" + sanitizeName(var.getName()) + "_" + b.getOperator().name().toLowerCase();
            } else {
                return "clause_cmp_" + b.getOperator().name().toLowerCase();
            }
        } else if (node instanceof OnnxInferenceNode onnx) {
            return "clause_ml_" + sanitizeName(onnx.getModelName());
        } else if (node instanceof VariableNode var) {
            return "clause_var_" + sanitizeName(var.getName());
        }
        return "clause_node_" + Math.abs(Objects.hash(ruleName != null ? ruleName : "", node));
    }

    /**
     * Sanitizes raw names into valid Java identifier substrings.
     *
     * @param name input string
     * @return sanitized string containing only alphanumeric characters and underscores
     */
    public static String sanitizeName(String name) {
        if (name == null) return "null";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
