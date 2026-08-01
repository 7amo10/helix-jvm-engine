package com.helix.core.parser.ast;

import java.util.Objects;

/**
 * AST node representing unary operations (!, -).
 */
public class UnaryOpNode implements ExpressionNode {

    public enum Operator {
        NOT("!"),
        NEGATE("-");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    private final Operator operator;
    private final ExpressionNode operand;

    public UnaryOpNode(Operator operator, ExpressionNode operand) {
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.operand = Objects.requireNonNull(operand, "operand node cannot be null");
    }

    public Operator getOperator() {
        return operator;
    }

    public ExpressionNode getOperand() {
        return operand;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnaryOpNode that = (UnaryOpNode) o;
        return operator == that.operator && Objects.equals(operand, that.operand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, operand);
    }

    @Override
    public String toString() {
        return "(" + operator.getSymbol() + operand + ")";
    }
}
