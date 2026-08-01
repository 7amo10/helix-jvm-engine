package com.helix.core.bytecode;

import com.helix.core.parser.ast.AstVisitor;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.MethodCallNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.core.parser.ast.VariableNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AST Optimization pass that performs constant folding on literal expressions.
 * Reduces compile-time constant operations (e.g. 2 + 3 -> 5, "a" + "b" -> "ab").
 */
public class ConstantFolder implements AstVisitor<ExpressionNode> {

    public ExpressionNode fold(ExpressionNode node) {
        if (node == null) return null;
        return node.accept(this);
    }

    @Override
    public ExpressionNode visit(LiteralNode node) {
        return node;
    }

    @Override
    public ExpressionNode visit(VariableNode node) {
        return node;
    }

    @Override
    public ExpressionNode visit(UnaryOpNode node) {
        ExpressionNode operand = node.getOperand().accept(this);
        if (operand instanceof LiteralNode lit) {
            Object val = lit.getValue();
            if (node.getOperator() == UnaryOpNode.Operator.NOT && val instanceof Boolean b) {
                return new LiteralNode(!b, Boolean.class);
            }
            if (node.getOperator() == UnaryOpNode.Operator.NEGATE && val instanceof Number n) {
                if (n instanceof Double d) return new LiteralNode(-d);
                if (n instanceof Long l) return new LiteralNode(-l);
                if (n instanceof Integer i) return new LiteralNode(-i);
            }
        }
        return new UnaryOpNode(node.getOperator(), operand);
    }

    @Override
    public ExpressionNode visit(BinaryOpNode node) {
        ExpressionNode left = node.getLeft().accept(this);
        ExpressionNode right = node.getRight().accept(this);

        if (left instanceof LiteralNode leftLit && right instanceof LiteralNode rightLit) {
            Object lVal = leftLit.getValue();
            Object rVal = rightLit.getValue();

            // String concatenation
            if (node.getOperator() == BinaryOpNode.Operator.ADD && (lVal instanceof String || rVal instanceof String)) {
                return new LiteralNode(String.valueOf(lVal) + String.valueOf(rVal), String.class);
            }

            // Numeric operations
            if (lVal instanceof Number n1 && rVal instanceof Number n2) {
                return foldNumeric(node.getOperator(), n1, n2);
            }

            // Boolean logical operations
            if (lVal instanceof Boolean b1 && rVal instanceof Boolean b2) {
                return foldBoolean(node.getOperator(), b1, b2);
            }

            // Equality operations
            if (node.getOperator() == BinaryOpNode.Operator.EQUAL) {
                return new LiteralNode(Objects.equals(lVal, rVal), Boolean.class);
            }
            if (node.getOperator() == BinaryOpNode.Operator.NOT_EQUAL) {
                return new LiteralNode(!Objects.equals(lVal, rVal), Boolean.class);
            }
        }

        return new BinaryOpNode(node.getOperator(), left, right);
    }

    @Override
    public ExpressionNode visit(MethodCallNode node) {
        ExpressionNode target = node.getTarget().accept(this);
        List<ExpressionNode> args = new ArrayList<>();
        for (ExpressionNode arg : node.getArguments()) {
            args.add(arg.accept(this));
        }
        return new MethodCallNode(target, node.getMethodName(), args);
    }

    private ExpressionNode foldNumeric(BinaryOpNode.Operator op, Number n1, Number n2) {
        boolean isDouble = n1 instanceof Double || n2 instanceof Double;
        boolean isLong = n1 instanceof Long || n2 instanceof Long;

        switch (op) {
            case ADD -> {
                if (isDouble) return new LiteralNode(n1.doubleValue() + n2.doubleValue());
                if (isLong) return new LiteralNode(n1.longValue() + n2.longValue());
                return new LiteralNode(n1.intValue() + n2.intValue());
            }
            case SUBTRACT -> {
                if (isDouble) return new LiteralNode(n1.doubleValue() - n2.doubleValue());
                if (isLong) return new LiteralNode(n1.longValue() - n2.longValue());
                return new LiteralNode(n1.intValue() - n2.intValue());
            }
            case MULTIPLY -> {
                if (isDouble) return new LiteralNode(n1.doubleValue() * n2.doubleValue());
                if (isLong) return new LiteralNode(n1.longValue() * n2.longValue());
                return new LiteralNode(n1.intValue() * n2.intValue());
            }
            case DIVIDE -> {
                if (isDouble) return new LiteralNode(n1.doubleValue() / n2.doubleValue());
                if (isLong) return new LiteralNode(n1.longValue() / n2.longValue());
                return new LiteralNode(n1.intValue() / n2.intValue());
            }
            case GREATER_THAN -> {
                return new LiteralNode(n1.doubleValue() > n2.doubleValue(), Boolean.class);
            }
            case GREATER_EQUAL -> {
                return new LiteralNode(n1.doubleValue() >= n2.doubleValue(), Boolean.class);
            }
            case LESS_THAN -> {
                return new LiteralNode(n1.doubleValue() < n2.doubleValue(), Boolean.class);
            }
            case LESS_EQUAL -> {
                return new LiteralNode(n1.doubleValue() <= n2.doubleValue(), Boolean.class);
            }
            case EQUAL -> {
                return new LiteralNode(Double.compare(n1.doubleValue(), n2.doubleValue()) == 0, Boolean.class);
            }
            case NOT_EQUAL -> {
                return new LiteralNode(Double.compare(n1.doubleValue(), n2.doubleValue()) != 0, Boolean.class);
            }
            default -> {
                return null;
            }
        }
    }

    private ExpressionNode foldBoolean(BinaryOpNode.Operator op, Boolean b1, Boolean b2) {
        return switch (op) {
            case AND -> new LiteralNode(b1 && b2, Boolean.class);
            case OR -> new LiteralNode(b1 || b2, Boolean.class);
            case EQUAL -> new LiteralNode(b1.equals(b2), Boolean.class);
            case NOT_EQUAL -> new LiteralNode(!b1.equals(b2), Boolean.class);
            default -> null;
        };
    }
}
