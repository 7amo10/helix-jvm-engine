package com.helix.core.parser.ast;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AST node representing method invocations on a target expression (e.g. name.equals("test")).
 */
public class MethodCallNode implements ExpressionNode {

    private final ExpressionNode target;
    private final String methodName;
    private final List<ExpressionNode> arguments;

    public MethodCallNode(ExpressionNode target, String methodName, List<ExpressionNode> arguments) {
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.methodName = Objects.requireNonNull(methodName, "methodName cannot be null");
        this.arguments = arguments != null ? List.copyOf(arguments) : Collections.emptyList();
    }

    public ExpressionNode getTarget() {
        return target;
    }

    public String getMethodName() {
        return methodName;
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
        MethodCallNode that = (MethodCallNode) o;
        return Objects.equals(target, that.target) && Objects.equals(methodName, that.methodName) && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, methodName, arguments);
    }

    @Override
    public String toString() {
        return target + "." + methodName + "(" + arguments + ")";
    }
}
