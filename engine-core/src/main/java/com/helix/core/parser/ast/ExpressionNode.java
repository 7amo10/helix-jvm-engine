package com.helix.core.parser.ast;

/**
 * Base interface for all nodes in the Abstract Syntax Tree.
 */
public interface ExpressionNode {

    /**
     * Accepts an AST visitor.
     *
     * @param visitor the visitor
     * @param <R>     return type
     * @return result from visitor
     */
    <R> R accept(AstVisitor<R> visitor);
}
