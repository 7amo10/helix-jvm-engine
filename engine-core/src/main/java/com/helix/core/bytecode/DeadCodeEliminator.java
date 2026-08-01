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

/**
 * AST Optimization pass that eliminates unreachable branches and redundant constant logical terms.
 * (e.g. true && x -> x, false && x -> false, true || x -> true, false || x -> x).
 */
public class DeadCodeEliminator implements AstVisitor<ExpressionNode> {

    public ExpressionNode eliminate(ExpressionNode node) {
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
        return new UnaryOpNode(node.getOperator(), node.getOperand().accept(this));
    }

    @Override
    public ExpressionNode visit(BinaryOpNode node) {
        ExpressionNode left = node.getLeft().accept(this);
        ExpressionNode right = node.getRight().accept(this);

        if (node.getOperator() == BinaryOpNode.Operator.AND) {
            if (left instanceof LiteralNode lLit && lLit.getValue() instanceof Boolean b) {
                if (b) {
                    return right; // true && right -> right
                } else {
                    return new LiteralNode(false, Boolean.class); // false && right -> false
                }
            }
            if (right instanceof LiteralNode rLit && rLit.getValue() instanceof Boolean b) {
                if (b) {
                    return left; // left && true -> left
                } else {
                    return new LiteralNode(false, Boolean.class); // left && false -> false
                }
            }
        }

        if (node.getOperator() == BinaryOpNode.Operator.OR) {
            if (left instanceof LiteralNode lLit && lLit.getValue() instanceof Boolean b) {
                if (b) {
                    return new LiteralNode(true, Boolean.class); // true || right -> true
                } else {
                    return right; // false || right -> right
                }
            }
            if (right instanceof LiteralNode rLit && rLit.getValue() instanceof Boolean b) {
                if (b) {
                    return new LiteralNode(true, Boolean.class); // left || true -> true
                } else {
                    return left; // left || false -> left
                }
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
}
