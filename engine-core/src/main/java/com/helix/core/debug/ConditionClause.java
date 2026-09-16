package com.helix.core.debug;

import com.helix.core.parser.ast.ExpressionNode;

import java.util.Objects;

/**
 * Represents an identifiable condition clause within a rule AST that can hold breakpoints.
 */
public class ConditionClause {

    private final int index;
    private final String description;
    private final String operator;
    private final ExpressionNode leftNode;
    private final ExpressionNode rightNode;

    public ConditionClause(int index, String description, String operator, ExpressionNode leftNode, ExpressionNode rightNode) {
        this.index = index;
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.leftNode = leftNode;
        this.rightNode = rightNode;
    }

    public int getIndex() {
        return index;
    }

    public String getDescription() {
        return description;
    }

    public String getOperator() {
        return operator;
    }

    public ExpressionNode getLeftNode() {
        return leftNode;
    }

    public ExpressionNode getRightNode() {
        return rightNode;
    }

    @Override
    public String toString() {
        return "[" + index + "] " + description;
    }
}
