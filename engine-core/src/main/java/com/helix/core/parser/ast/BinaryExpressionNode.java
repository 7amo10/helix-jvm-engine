package com.helix.core.parser.ast;

/**
 * AST node representing general binary expressions (arithmetic and logical operations).
 */
public class BinaryExpressionNode extends BinaryOpNode {

    public BinaryExpressionNode(Operator operator, ExpressionNode left, ExpressionNode right) {
        super(operator, left, right);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
