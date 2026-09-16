package com.helix.core.parser.ast;

/**
 * Visitor interface for AST node traversal and code generation.
 *
 * @param <R> return type of visitor methods
 */
public interface AstVisitor<R> {
    R visit(LiteralNode node);
    R visit(VariableNode node);
    R visit(BinaryOpNode node);
    R visit(UnaryOpNode node);
    R visit(MethodCallNode node);

    default R visit(FieldAccessNode node) {
        return null;
    }

    default R visit(FunctionCallNode node) {
        return null;
    }

    default R visit(BinaryExpressionNode node) {
        return visit((BinaryOpNode) node);
    }

    default R visit(ComparisonNode node) {
        return visit((BinaryOpNode) node);
    }
}
