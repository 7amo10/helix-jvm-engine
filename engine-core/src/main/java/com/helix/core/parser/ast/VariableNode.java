package com.helix.core.parser.ast;

import java.util.Objects;

/**
 * AST node representing an input variable reference by name.
 */
public class VariableNode implements ExpressionNode {

    private final String name;

    public VariableNode(String name) {
        this.name = Objects.requireNonNull(name, "variable name cannot be null");
    }

    public String getName() {
        return name;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariableNode that = (VariableNode) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "VariableNode{" + name + '}';
    }
}
