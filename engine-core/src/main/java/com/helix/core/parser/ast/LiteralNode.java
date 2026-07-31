package com.helix.core.parser.ast;

import java.util.Objects;

/**
 * AST node representing literal values (numbers, booleans, strings).
 */
public class LiteralNode implements ExpressionNode {

    private final Object value;
    private final Class<?> type;

    public LiteralNode(Object value) {
        this.value = value;
        this.type = value != null ? value.getClass() : Object.class;
    }

    public LiteralNode(Object value, Class<?> type) {
        this.value = value;
        this.type = type != null ? type : (value != null ? value.getClass() : Object.class);
    }

    public Object getValue() {
        return value;
    }

    public Class<?> getType() {
        return type;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiteralNode that = (LiteralNode) o;
        return Objects.equals(value, that.value) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, type);
    }

    @Override
    public String toString() {
        return "LiteralNode{" + value + '}';
    }
}
