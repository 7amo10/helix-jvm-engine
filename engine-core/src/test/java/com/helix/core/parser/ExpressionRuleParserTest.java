package com.helix.core.parser;

import com.helix.core.bytecode.AstEvaluator;
import com.helix.core.parser.ast.BinaryExpressionNode;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ComparisonNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.FieldAccessNode;
import com.helix.core.parser.ast.FunctionCallNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.MethodCallNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.core.parser.ast.VariableNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionRuleParserTest {

    private ExpressionRuleParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExpressionRuleParser();
    }

    @Test
    @DisplayName("Should parse arithmetic operations with correct precedence (+, -, *, /, %)")
    void testParseArithmeticPrecedence() throws Exception {
        // 1 + 2 * 3 -> 1 + (2 * 3)
        ExpressionNode node = parser.parse("1 + 2 * 3");
        assertInstanceOf(BinaryExpressionNode.class, node);
        BinaryExpressionNode addNode = (BinaryExpressionNode) node;
        assertEquals(BinaryOpNode.Operator.ADD, addNode.getOperator());
        assertEquals(1, ((LiteralNode) addNode.getLeft()).getValue());

        assertInstanceOf(BinaryExpressionNode.class, addNode.getRight());
        BinaryExpressionNode multNode = (BinaryExpressionNode) addNode.getRight();
        assertEquals(BinaryOpNode.Operator.MULTIPLY, multNode.getOperator());
        assertEquals(2, ((LiteralNode) multNode.getLeft()).getValue());
        assertEquals(3, ((LiteralNode) multNode.getRight()).getValue());

        // Modulo precedence: 10 % 3 == 1
        ExpressionNode modExpr = parser.parse("10 % 3");
        assertInstanceOf(BinaryExpressionNode.class, modExpr);
        BinaryExpressionNode modNode = (BinaryExpressionNode) modExpr;
        assertEquals(BinaryOpNode.Operator.MODULO, modNode.getOperator());
    }

    @Test
    @DisplayName("Should parse logical expressions with correct precedence (||, &&, !)")
    void testParseLogicalPrecedence() throws Exception {
        // a || b && !c -> a || (b && (!c))
        ExpressionNode node = parser.parse("a || b && !c");
        assertInstanceOf(BinaryExpressionNode.class, node);
        BinaryExpressionNode orNode = (BinaryExpressionNode) node;
        assertEquals(BinaryOpNode.Operator.OR, orNode.getOperator());
        assertEquals("a", ((VariableNode) orNode.getLeft()).getName());

        assertInstanceOf(BinaryExpressionNode.class, orNode.getRight());
        BinaryExpressionNode andNode = (BinaryExpressionNode) orNode.getRight();
        assertEquals(BinaryOpNode.Operator.AND, andNode.getOperator());
        assertEquals("b", ((VariableNode) andNode.getLeft()).getName());

        assertInstanceOf(UnaryOpNode.class, andNode.getRight());
        UnaryOpNode notNode = (UnaryOpNode) andNode.getRight();
        assertEquals(UnaryOpNode.Operator.NOT, notNode.getOperator());
        assertEquals("c", ((VariableNode) notNode.getOperand()).getName());
    }

    @Test
    @DisplayName("Should parse comparisons into ComparisonNode (>, <, >=, <=, ==, !=)")
    void testParseComparisons() throws Exception {
        ExpressionNode gt = parser.parse("amount > 1000");
        assertInstanceOf(ComparisonNode.class, gt);
        assertEquals(BinaryOpNode.Operator.GREATER_THAN, ((ComparisonNode) gt).getOperator());

        ExpressionNode ge = parser.parse("amount >= 1000");
        assertInstanceOf(ComparisonNode.class, ge);
        assertEquals(BinaryOpNode.Operator.GREATER_EQUAL, ((ComparisonNode) ge).getOperator());

        ExpressionNode lt = parser.parse("score < 0.5");
        assertInstanceOf(ComparisonNode.class, lt);
        assertEquals(BinaryOpNode.Operator.LESS_THAN, ((ComparisonNode) lt).getOperator());

        ExpressionNode le = parser.parse("score <= 0.5");
        assertInstanceOf(ComparisonNode.class, le);
        assertEquals(BinaryOpNode.Operator.LESS_EQUAL, ((ComparisonNode) le).getOperator());

        ExpressionNode eq = parser.parse("status == 'ACTIVE'");
        assertInstanceOf(ComparisonNode.class, eq);
        assertEquals(BinaryOpNode.Operator.EQUAL, ((ComparisonNode) eq).getOperator());

        ExpressionNode ne = parser.parse("status != null");
        assertInstanceOf(ComparisonNode.class, ne);
        assertEquals(BinaryOpNode.Operator.NOT_EQUAL, ((ComparisonNode) ne).getOperator());
    }

    @Test
    @DisplayName("Should parse context property access and nested field navigation (user.age, ctx.amount)")
    void testParsePropertyAccess() throws Exception {
        ExpressionNode node = parser.parse("user.age >= 18");
        assertInstanceOf(ComparisonNode.class, node);
        ComparisonNode cmp = (ComparisonNode) node;

        assertInstanceOf(FieldAccessNode.class, cmp.getLeft());
        FieldAccessNode fieldNode = (FieldAccessNode) cmp.getLeft();
        assertEquals("age", fieldNode.getFieldName());
        assertEquals("user", ((VariableNode) fieldNode.getTarget()).getName());
        assertEquals("user.age", fieldNode.getFullPath());

        // Multi-level navigation: order.customer.address.city
        ExpressionNode nestedNode = parser.parse("order.customer.address.city == \"Berlin\"");
        assertInstanceOf(ComparisonNode.class, nestedNode);
        ComparisonNode nestedCmp = (ComparisonNode) nestedNode;
        assertInstanceOf(FieldAccessNode.class, nestedCmp.getLeft());
        FieldAccessNode cityAccess = (FieldAccessNode) nestedCmp.getLeft();
        assertEquals("city", cityAccess.getFieldName());
        assertEquals("order.customer.address.city", cityAccess.getFullPath());
    }

    @Test
    @DisplayName("Should parse function calls (ML(\"fraud\"), len(items), max(a, b))")
    void testParseFunctionCalls() throws Exception {
        ExpressionNode ml = parser.parse("ML(\"fraud\") > 0.8");
        assertInstanceOf(ComparisonNode.class, ml);
        ComparisonNode cmp = (ComparisonNode) ml;
        assertInstanceOf(FunctionCallNode.class, cmp.getLeft());
        FunctionCallNode fn = (FunctionCallNode) cmp.getLeft();
        assertEquals("ML", fn.getFunctionName());
        assertEquals(1, fn.getArguments().size());
        assertEquals("fraud", ((LiteralNode) fn.getArguments().get(0)).getValue());

        ExpressionNode multiArg = parser.parse("max(10, 20) + min(5, 2)");
        assertInstanceOf(BinaryExpressionNode.class, multiArg);
    }

    @Test
    @DisplayName("Should parse method calls on object references")
    void testParseMethodCalls() throws Exception {
        ExpressionNode node = parser.parse("user.getName() == 'Alice'");
        assertInstanceOf(ComparisonNode.class, node);
        ComparisonNode cmp = (ComparisonNode) node;
        assertInstanceOf(MethodCallNode.class, cmp.getLeft());
        MethodCallNode call = (MethodCallNode) cmp.getLeft();
        assertEquals("getName", call.getMethodName());
        assertEquals("user", ((VariableNode) call.getTarget()).getName());
        assertTrue(call.getArguments().isEmpty());
    }

    @Test
    @DisplayName("Should parse literals correctly (integers, longs, doubles, booleans, strings, null)")
    void testParseLiterals() throws Exception {
        ExpressionNode intLit = parser.parse("42");
        assertInstanceOf(LiteralNode.class, intLit);
        assertEquals(42, ((LiteralNode) intLit).getValue());

        ExpressionNode longLit = parser.parse("10000000000L");
        assertInstanceOf(LiteralNode.class, longLit);
        assertEquals(10000000000L, ((LiteralNode) longLit).getValue());

        ExpressionNode doubleLit = parser.parse("3.14159");
        assertInstanceOf(LiteralNode.class, doubleLit);
        assertEquals(3.14159, ((LiteralNode) doubleLit).getValue());

        ExpressionNode expLit = parser.parse("1e-4");
        assertInstanceOf(LiteralNode.class, expLit);
        assertEquals(0.0001, (Double) ((LiteralNode) expLit).getValue(), 1e-9);

        ExpressionNode boolTrue = parser.parse("true");
        assertEquals(Boolean.TRUE, ((LiteralNode) boolTrue).getValue());

        ExpressionNode boolFalse = parser.parse("false");
        assertEquals(Boolean.FALSE, ((LiteralNode) boolFalse).getValue());

        ExpressionNode nullLit = parser.parse("null");
        assertNull(((LiteralNode) nullLit).getValue());

        ExpressionNode strLit = parser.parse("\"hello \\\"world\\\"\\n\"");
        assertEquals("hello \"world\"\n", ((LiteralNode) strLit).getValue());
    }

    @Test
    @DisplayName("Should respect parenthesized expressions")
    void testParentheses() throws Exception {
        ExpressionNode node = parser.parse("(1 + 2) * 3");
        assertInstanceOf(BinaryExpressionNode.class, node);
        BinaryExpressionNode mult = (BinaryExpressionNode) node;
        assertEquals(BinaryOpNode.Operator.MULTIPLY, mult.getOperator());
        assertInstanceOf(BinaryExpressionNode.class, mult.getLeft());
        assertEquals(BinaryOpNode.Operator.ADD, ((BinaryExpressionNode) mult.getLeft()).getOperator());
    }

    @Test
    @DisplayName("Should support comments in expressions (line and block comments)")
    void testCommentsInExpressions() throws Exception {
        String exprWithComments = """
                // Check if user is eligible
                /* Multi-line comment
                   spanning lines */
                user.age >= 18 && score > 0.5 // trailing comment
                """;
        ExpressionNode node = parser.parse(exprWithComments);
        assertInstanceOf(BinaryExpressionNode.class, node);
    }

    @Test
    @DisplayName("Should perform parse-time constant folding and dead code elimination")
    void testParseAndFold() throws Exception {
        // 2 + 3 * 4 -> 14
        ExpressionNode foldedNum = parser.parseAndFold("2 + 3 * 4");
        assertInstanceOf(LiteralNode.class, foldedNum);
        assertEquals(14, ((LiteralNode) foldedNum).getValue());

        // String concatenation folding: 'Hello, ' + 'World!'
        ExpressionNode foldedStr = parser.parseAndFold("'Hello, ' + 'World!'");
        assertInstanceOf(LiteralNode.class, foldedStr);
        assertEquals("Hello, World!", ((LiteralNode) foldedStr).getValue());

        // Dead code elimination: true || x > 5 -> true
        ExpressionNode deadOr = parser.parseAndFold("true || x > 5");
        assertInstanceOf(LiteralNode.class, deadOr);
        assertEquals(true, ((LiteralNode) deadOr).getValue());

        // Dead code elimination: false && y < 10 -> false
        ExpressionNode deadAnd = parser.parseAndFold("false && y < 10");
        assertInstanceOf(LiteralNode.class, deadAnd);
        assertEquals(false, ((LiteralNode) deadAnd).getValue());

        // Dead code simplification: true && x > 10 -> x > 10
        ExpressionNode simplified = parser.parseAndFold("true && x > 10");
        assertInstanceOf(ComparisonNode.class, simplified);
    }

    @Test
    @DisplayName("Should parse complete RuleNode with metadata and strict schema validation")
    void testParseRuleWithSchema() throws Exception {
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("amount", Double.class);
        schema.put("riskScore", Double.class);

        RuleNode rule = parser.parseRule(
                "HighRiskTransactionRule",
                "2.0.0",
                "Flags high-risk large volume transactions",
                "FRAUD",
                "amount > 1000.0 && riskScore < 0.2",
                schema
        );

        assertNotNull(rule);
        assertEquals("HighRiskTransactionRule", rule.getName());
        assertEquals("2.0.0", rule.getVersion());
        assertEquals("Flags high-risk large volume transactions", rule.getDescription());
        assertEquals("FRAUD", rule.getCategory());
        assertEquals("amount > 1000.0 && riskScore < 0.2", rule.getExpression());
        assertEquals(2, rule.getInputSchema().size());
        assertNotNull(rule.getRootNode());
    }

    @Test
    @DisplayName("Should fail type checking when expression has incompatible types")
    void testTypeCheckingFailure() {
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("name", String.class);

        // Arithmetic subtraction on a String should throw TypeMismatchException
        assertThrows(TypeMismatchException.class, () ->
                parser.parseRule("InvalidTypeRule", "name - 5 > 0", schema));
    }

    @Test
    @DisplayName("Should throw ExpressionParseException with visual pointer on syntax errors")
    void testSyntaxErrorDiagnostics() {
        // Missing right operand
        ExpressionParseException ex1 = assertThrows(ExpressionParseException.class, () ->
                parser.parse("amount >"));
        assertTrue(ex1.getMessage().contains("column"));
        assertTrue(ex1.getMessage().contains("^"));

        // Single '&' instead of '&&'
        ExpressionParseException ex2 = assertThrows(ExpressionParseException.class, () ->
                parser.parse("a & b"));
        assertTrue(ex2.getMessage().contains("&&"));
        assertEquals(1, ex2.getLine());
        assertEquals(3, ex2.getColumn());
        assertTrue(ex2.getMessage().contains("^"));

        // Single '|' instead of '||'
        ExpressionParseException ex3 = assertThrows(ExpressionParseException.class, () ->
                parser.parse("a | b"));
        assertTrue(ex3.getMessage().contains("||"));

        // Single '=' instead of '=='
        ExpressionParseException ex4 = assertThrows(ExpressionParseException.class, () ->
                parser.parse("a = b"));
        assertTrue(ex4.getMessage().contains("=="));

        // Unterminated string literal
        ExpressionParseException ex5 = assertThrows(ExpressionParseException.class, () ->
                parser.parse("name == \"unclosed string"));
        assertTrue(ex5.getMessage().contains("Unterminated string"));

        // Unterminated block comment
        ExpressionParseException ex6 = assertThrows(ExpressionParseException.class, () ->
                parser.parse("x > 0 /* unclosed comment"));
        assertTrue(ex6.getMessage().contains("Unterminated block comment"));

        // Unclosed parenthesis
        ExpressionParseException ex7 = assertThrows(ExpressionParseException.class, () ->
                parser.parse("(x + 5"));
        assertTrue(ex7.getMessage().contains("Expected ')'"));

        // Null or blank expression
        assertThrows(ExpressionParseException.class, () -> parser.parse(""));
        assertThrows(ExpressionParseException.class, () -> parser.parse("   "));
        assertThrows(ExpressionParseException.class, () -> parser.parse(null));

        // Blank rule name
        assertThrows(ExpressionParseException.class, () ->
                parser.parseRule("", "x > 0"));
    }

    @Test
    @DisplayName("Should evaluate parsed expression with AstEvaluator correctly")
    void testAstEvaluatorExecution() throws Exception {
        ExpressionNode node = parser.parse("user.age >= 18 && amount % 10 == 0");

        com.helix.api.ExecutionContext ctx = new com.helix.api.ExecutionContext();
        Map<String, Object> user = new HashMap<>();
        user.put("age", 25);
        ctx.setVariable("user", user);
        ctx.setVariable("amount", 100);

        AstEvaluator evaluator = new AstEvaluator(ctx);
        Object result = node.accept(evaluator);
        assertEquals(Boolean.TRUE, result);

        // Negative scenario
        ctx.setVariable("amount", 103);
        Object falseResult = node.accept(evaluator);
        assertEquals(Boolean.FALSE, falseResult);
    }

    @Test
    @DisplayName("Acceptance Criteria: Parsing latency should be well under 20 microseconds")
    void testMicrosecondParsingLatency() throws Exception {
        String expr = "amount > 1000 && riskScore < 0.2 && user.age >= 18";

        // Warmup JIT
        for (int i = 0; i < 5000; i++) {
            parser.parse(expr);
        }

        // Benchmark
        int iterations = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            parser.parse(expr);
        }
        long duration = System.nanoTime() - start;
        double avgMicros = (duration / (double) iterations) / 1000.0;

        System.out.println("Average parse latency: " + avgMicros + " µs");
        assertTrue(avgMicros < 20.0, "Average latency (" + avgMicros + " µs) must be < 20 µs");
    }
}
