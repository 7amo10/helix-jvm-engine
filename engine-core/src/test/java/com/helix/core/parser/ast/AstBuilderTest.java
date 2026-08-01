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
    @DisplayName("1. Should build AST for simple binary operation")
    void testSimpleBinaryOperation() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("amount > 1000");

        assertTrue(node instanceof BinaryOpNode);
        BinaryOpNode binaryOp = (BinaryOpNode) node;
        assertEquals(BinaryOpNode.Operator.GREATER_THAN, binaryOp.getOperator());
        assertEquals(new VariableNode("amount"), binaryOp.getLeft());
        assertEquals(new LiteralNode(1000L), binaryOp.getRight());
    }

    @Test
    @DisplayName("2. Should respect operator precedence (* before +)")
    void testOperatorPrecedence() throws ParseException {
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
    @DisplayName("3. Should respect parentheses for grouping precedence")
    void testParenthesesGrouping() throws ParseException {
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
    @DisplayName("4. Should build AST for unary NOT operator")
    void testUnaryNotOperator() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("!flag");

        assertTrue(node instanceof UnaryOpNode);
        UnaryOpNode unary = (UnaryOpNode) node;
        assertEquals(UnaryOpNode.Operator.NOT, unary.getOperator());
        assertEquals(new VariableNode("flag"), unary.getOperand());
    }

    @Test
    @DisplayName("5. Should build AST for unary Negate operator")
    void testUnaryNegateOperator() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("-val");

        assertTrue(node instanceof UnaryOpNode);
        UnaryOpNode unary = (UnaryOpNode) node;
        assertEquals(UnaryOpNode.Operator.NEGATE, unary.getOperator());
        assertEquals(new VariableNode("val"), unary.getOperand());
    }

    @Test
    @DisplayName("6. Should build AST for single-arg method call")
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
    @DisplayName("7. Should build AST for multi-arg method call")
    void testMultiArgMethodCall() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("str.substring(0, 5)");

        assertTrue(node instanceof MethodCallNode);
        MethodCallNode methodCall = (MethodCallNode) node;
        assertEquals("substring", methodCall.getMethodName());
        assertEquals(2, methodCall.getArguments().size());
        assertEquals(new LiteralNode(0L), methodCall.getArguments().get(0));
        assertEquals(new LiteralNode(5L), methodCall.getArguments().get(1));
    }

    @Test
    @DisplayName("8. Should build AST for method call chaining")
    void testMethodCallChaining() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("user.getName().toLowerCase()");

        assertTrue(node instanceof MethodCallNode);
        MethodCallNode outerCall = (MethodCallNode) node;
        assertEquals("toLowerCase", outerCall.getMethodName());

        assertTrue(outerCall.getTarget() instanceof MethodCallNode);
        MethodCallNode innerCall = (MethodCallNode) outerCall.getTarget();
        assertEquals("getName", innerCall.getMethodName());
        assertEquals(new VariableNode("user"), innerCall.getTarget());
    }

    @Test
    @DisplayName("9. Should build AST for combined logical expressions")
    void testLogicalExpressions() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("x > 10 && y < 20 || z == true");

        assertTrue(node instanceof BinaryOpNode);
        BinaryOpNode root = (BinaryOpNode) node;
        assertEquals(BinaryOpNode.Operator.OR, root.getOperator());
    }

    @Test
    @DisplayName("10. Should build AST for relational ops (>=, <=, !=)")
    void testRelationalOps() throws ParseException {
        ExpressionNode node1 = astBuilder.buildAst("a >= b");
        assertEquals(BinaryOpNode.Operator.GREATER_EQUAL, ((BinaryOpNode) node1).getOperator());

        ExpressionNode node2 = astBuilder.buildAst("c <= d");
        assertEquals(BinaryOpNode.Operator.LESS_EQUAL, ((BinaryOpNode) node2).getOperator());

        ExpressionNode node3 = astBuilder.buildAst("e != f");
        assertEquals(BinaryOpNode.Operator.NOT_EQUAL, ((BinaryOpNode) node3).getOperator());
    }

    @Test
    @DisplayName("11. Should handle string escaping in string literals")
    void testStringEscaping() throws ParseException {
        ExpressionNode node = astBuilder.buildAst("\"hello \\\"world\\\"\"");

        assertTrue(node instanceof LiteralNode);
        assertEquals("hello \"world\"", ((LiteralNode) node).getValue());
    }

    @Test
    @DisplayName("12. Should traverse AST using Visitor pattern")
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

    @Test
    @DisplayName("13. Should throw ParseException for empty expression")
    void testEmptyExpression() {
        assertThrows(ParseException.class, () -> astBuilder.buildAst(""));
    }

    @Test
    @DisplayName("14. Should throw ParseException for unterminated string literal")
    void testUnterminatedStringLiteral() {
        assertThrows(ParseException.class, () -> astBuilder.buildAst("name == \"unterminated"));
    }

    @Test
    @DisplayName("15. Should throw ParseException for unexpected character symbol")
    void testUnexpectedCharacter() {
        assertThrows(ParseException.class, () -> astBuilder.buildAst("x @ y"));
    }

    @Test
    @DisplayName("16. Should throw ParseException for unexpected trailing tokens")
    void testTrailingTokens() {
        assertThrows(ParseException.class, () -> astBuilder.buildAst("x + y z"));
    }
}
