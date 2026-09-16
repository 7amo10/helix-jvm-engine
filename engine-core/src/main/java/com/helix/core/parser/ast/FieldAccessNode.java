package com.helix.core.parser.ast;

import java.util.Objects;

/**
 * AST node representing context property and field navigation (e.g. user.age, ctx.amount).
 */
public class FieldAccessNode implements ExpressionNode {

    private final ExpressionNode target;
    private final String fieldName;

    public FieldAccessNode(ExpressionNode target, String fieldName) {
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
    }

    public ExpressionNode getTarget() {
        return target;
    }

    public String getFieldName() {
        return fieldName;
    }

    /**
     * Resolves the full property path when the target chain consists of nested FieldAccessNodes or VariableNodes.
     * E.g. "user.profile.age"
     *
     * @return dot-separated full property path
     */
    public String getFullPath() {
        if (target instanceof VariableNode v) {
            return v.getName() + "." + fieldName;
        } else if (target instanceof FieldAccessNode f) {
            return f.getFullPath() + "." + fieldName;
        }
        return fieldName;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAccessNode that = (FieldAccessNode) o;
        return Objects.equals(target, that.target) && Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, fieldName);
    }

    @Override
    public String toString() {
        return target + "." + fieldName;
    }
}
