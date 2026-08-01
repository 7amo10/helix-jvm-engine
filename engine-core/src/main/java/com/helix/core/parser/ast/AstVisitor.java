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
}
