package com.helix.core.parser.ast;

import java.util.Objects;

/**
 * AST node representing binary operations (+, -, *, /, >, >=, <, <=, ==, !=, &&, ||).
 */
public class BinaryOpNode implements ExpressionNode {

    public enum Operator {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        GREATER_THAN(">"),
        GREATER_EQUAL(">="),
        LESS_THAN("<"),
        LESS_EQUAL("<="),
        EQUAL("=="),
        NOT_EQUAL("!="),
        AND("&&"),
        OR("||");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }

        public static Operator fromSymbol(String symbol) {
            for (Operator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown binary operator symbol: " + symbol);
        }
    }

    private final Operator operator;
    private final ExpressionNode left;
    private final ExpressionNode right;

    public BinaryOpNode(Operator operator, ExpressionNode left, ExpressionNode right) {
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.left = Objects.requireNonNull(left, "left node cannot be null");
        this.right = Objects.requireNonNull(right, "right node cannot be null");
    }

    public Operator getOperator() {
        return operator;
    }

    public ExpressionNode getLeft() {
        return left;
    }

    public ExpressionNode getRight() {
        return right;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinaryOpNode that = (BinaryOpNode) o;
        return operator == that.operator && Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, left, right);
    }

    @Override
    public String toString() {
        return "(" + left + " " + operator.getSymbol() + " " + right + ")";
    }
}
