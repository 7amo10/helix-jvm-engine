package com.helix.core.parser.ast;

/**
 * AST node specifically representing comparison and relational expressions
 * (&gt;, &lt;, &gt;=, &lt;=, ==, !=).
 */
public class ComparisonNode extends BinaryOpNode {

    public ComparisonNode(Operator operator, ExpressionNode left, ExpressionNode right) {
        super(operator, left, right);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
