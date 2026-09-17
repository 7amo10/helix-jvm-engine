package com.helix.core.parser.ast;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AST node representing built-in or registered function invocations (e.g. ML("fraud"), len(items), abs(x)).
 */
public class FunctionCallNode implements ExpressionNode {

    private final String functionName;
    private final List<ExpressionNode> arguments;

    public FunctionCallNode(String functionName, List<ExpressionNode> arguments) {
        this.functionName = Objects.requireNonNull(functionName, "functionName cannot be null");
        this.arguments = arguments != null ? Collections.unmodifiableList(arguments) : Collections.emptyList();
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<ExpressionNode> getArguments() {
        return arguments;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionCallNode that = (FunctionCallNode) o;
        return Objects.equals(functionName, that.functionName) && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionName, arguments);
    }

    @Override
    public String toString() {
        return functionName + "(" + String.join(", ", arguments.stream().map(Object::toString).toList()) + ")";
    }
}
