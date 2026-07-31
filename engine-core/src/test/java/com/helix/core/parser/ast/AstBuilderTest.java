package com.helix.core.parser.ast;

import com.helix.core.parser.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AstBuilderTest {

    private AstBuilder astBuilder;

    @BeforeEach
    void setUp() {
        astBuilder = new AstBuilder();
    }

    @Test
    @DisplayName("Should build AST for simple binary operation")
    void testSimpleBinaryOperation() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("amount > 1000");

        assertTrue(node instanceof BinaryOpNode);
        BinaryOpNode binaryOp = (BinaryOpNode) node;
        assertEquals(BinaryOpNode.Operator.GREATER_THAN, binaryOp.getOperator());
        assertEquals(new VariableNode("amount"), binaryOp.getLeft());
        assertEquals(new LiteralNode(1000L), binaryOp.getRight());
    }

    @Test
    @DisplayName("Should respect operator precedence (* before +)")
    void testOperatorPrecedence() throws ParseException {
        // x + y * 2  ==>  x + (y * 2)
        ExpressionNode node = astBuilder.buildAst("x + y * 2");

        assertTrue(node instanceof BinaryOpNode);
        BinaryOpNode outer = (BinaryOpNode) node;
        assertEquals(BinaryOpNode.Operator.ADD, outer.getOperator());
        assertEquals(new VariableNode("x"), outer.getLeft());

        assertTrue(outer.getRight() instanceof BinaryOpNode);
        BinaryOpNode inner = (BinaryOpNode) outer.getRight();
        assertEquals(BinaryOpNode.Operator.MULTIPLY, inner.getOperator());
        assertEquals(new VariableNode("y"), inner.getLeft());
        assertEquals(new LiteralNode(2L), inner.getRight());
    }

    @Test
    @DisplayName("Should respect parentheses for grouping precedence")
    void testParenthesesGrouping() throws ParseException {
        // (x + y) * 2
        ExpressionNode node = astBuilder.buildAst("(x + y) * 2");

        assertTrue(node instanceof BinaryOpNode);
        BinaryOpNode outer = (BinaryOpNode) node;
        assertEquals(BinaryOpNode.Operator.MULTIPLY, outer.getOperator());

        assertTrue(outer.getLeft() instanceof BinaryOpNode);
        BinaryOpNode inner = (BinaryOpNode) outer.getLeft();
        assertEquals(BinaryOpNode.Operator.ADD, inner.getOperator());
        assertEquals(new VariableNode("x"), inner.getLeft());
        assertEquals(new VariableNode("y"), inner.getRight());

        assertEquals(new LiteralNode(2L), outer.getRight());
    }

    @Test
    @DisplayName("Should build AST for unary operations")
    void testUnaryOperator() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("!flag");

        assertTrue(node instanceof UnaryOpNode);
        UnaryOpNode unary = (UnaryOpNode) node;
        assertEquals(UnaryOpNode.Operator.NOT, unary.getOperator());
        assertEquals(new VariableNode("flag"), unary.getOperand());
    }

    @Test
    @DisplayName("Should build AST for method calls")
    void testMethodCallNode() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("name.equals(\"test\")");

        assertTrue(node instanceof MethodCallNode);
        MethodCallNode methodCall = (MethodCallNode) node;
        assertEquals(new VariableNode("name"), methodCall.getTarget());
        assertEquals("equals", methodCall.getMethodName());
        assertEquals(1, methodCall.getArguments().size());
        assertEquals(new LiteralNode("test", String.class), methodCall.getArguments().get(0));
    }

    @Test
    @DisplayName("Should build AST for logical AND / OR combined expressions")
    void testLogicalExpressions() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("x > 10 && y < 20 || z == true");

        assertTrue(node instanceof BinaryOpNode);
        BinaryOpNode root = (BinaryOpNode) node;
        assertEquals(BinaryOpNode.Operator.OR, root.getOperator());
    }

    @Test
    @DisplayName("Should traverse AST using Visitor pattern")
    void testAstVisitorPattern() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("x + 5");

        AstVisitor<String> printVisitor = new AstVisitor<>() {
            @Override
            public String visit(LiteralNode node) {
                return String.valueOf(node.getValue());
            }

            @Override
            public String visit(VariableNode node) {
                return node.getName();
            }

            @Override
            public String visit(BinaryOpNode node) {
                return node.getLeft().accept(this) + " " + node.getOperator().getSymbol() + " " + node.getRight().accept(this);
            }

            @Override
            public String visit(UnaryOpNode node) {
                return node.getOperator().getSymbol() + node.getOperand().accept(this);
            }

            @Override
            public String visit(MethodCallNode node) {
                return node.getTarget().accept(this) + "." + node.getMethodName() + "()";
            }
        };

        String printed = node.accept(printVisitor);
        assertEquals("x + 5", printed);
    }
}
