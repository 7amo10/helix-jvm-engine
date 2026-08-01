package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.parser.RuleSchema;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.VariableNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ByteBuddyGeneratorTest {

    private ByteBuddyGenerator generator;
    private AstBuilder astBuilder;

    @BeforeEach
    void setUp() {
        generator = new ByteBuddyGenerator();
        astBuilder = new AstBuilder();
    }

    @Test
    @DisplayName("1. Should generate CompiledRule for simple arithmetic expression")
    void testSimpleArithmeticExpression() throws Exception {
        Rule rule = new RuleSchema("ArithmeticRule", "1.0.0", "Desc", "CALC", "x + y", Map.of("x", Integer.class, "y", Integer.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        assertNotNull(compiledRule);
        assertEquals("ArithmeticRule", compiledRule.getName());
        assertEquals("1.0.0", compiledRule.getVersion());

        ExecutionContext context = new ExecutionContext(Map.of("x", 10L, "y", 20L));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals(30L, result.getResult().orElse(null));
        assertTrue(result.getExecutionTimeNanos() > 0);
    }

    @Test
    @DisplayName("2. Should generate CompiledRule for comparison expression: amount > 1000")
    void testComparisonExpression() throws Exception {
        Rule rule = new RuleSchema("ComparisonRule", "2.0.0", "Desc", "FILTER", "amount > 1000", Map.of("amount", Long.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        ExecutionContext context = new ExecutionContext(Map.of("amount", 1500L));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getResult().orElse(null));
    }

    @Test
    @DisplayName("3. Should generate CompiledRule for logical operators: x > 10 && y < 20")
    void testLogicalOperatorExpression() throws Exception {
        Rule rule = new RuleSchema("LogicalRule", "1.0.0", "Desc", "FILTER", "x > 10 && y < 20", Map.of("x", Integer.class, "y", Integer.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);

        ExecutionContext passContext = new ExecutionContext(Map.of("x", 15L, "y", 12L));
        ExecutionResult passResult = compiledRule.execute(passContext);
        assertTrue(passResult.isSuccess());
        assertEquals(Boolean.TRUE, passResult.getResult().orElse(null));

        ExecutionContext failContext = new ExecutionContext(Map.of("x", 5L, "y", 12L));
        ExecutionResult failResult = compiledRule.execute(failContext);
        assertTrue(failResult.isSuccess());
        assertEquals(Boolean.FALSE, failResult.getResult().orElse(null));
    }

    @Test
    @DisplayName("4. Should generate CompiledRule for method call expression: name.equals(\"test\")")
    void testMethodCallExpression() throws Exception {
        Rule rule = new RuleSchema("MethodCallRule", "1.0.0", "Desc", "AUTH", "name.equals(\"test\")", Map.of("name", String.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);

        ExecutionContext matchContext = new ExecutionContext(Map.of("name", "test"));
        ExecutionResult matchResult = compiledRule.execute(matchContext);
        assertTrue(matchResult.isSuccess());
        assertEquals(Boolean.TRUE, matchResult.getResult().orElse(null));

        ExecutionContext mismatchContext = new ExecutionContext(Map.of("name", "other"));
        ExecutionResult mismatchResult = compiledRule.execute(mismatchContext);
        assertTrue(mismatchResult.isSuccess());
        assertEquals(Boolean.FALSE, mismatchResult.getResult().orElse(null));
    }

    @Test
    @DisplayName("5. Should return failure ExecutionResult when missing required variable")
    void testMissingVariableExecutionFailure() throws Exception {
        Rule rule = new RuleSchema("FailRule", "1.0.0", "Desc", "TEST", "x + 1", Map.of("x", Integer.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        ExecutionContext emptyContext = new ExecutionContext();
        ExecutionResult result = compiledRule.execute(emptyContext);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().isPresent());
        assertTrue(result.getExecutionTimeNanos() > 0);
    }

    @Test
    @DisplayName("6. Should generate CompiledRule for subtraction and multiplication")
    void testSubtractionAndMultiplication() throws Exception {
        Rule rule = new RuleSchema("SubMultRule", "1.0.0", "Desc", "MATH", "a * b - c", Map.of("a", Integer.class, "b", Integer.class, "c", Integer.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        ExecutionContext context = new ExecutionContext(Map.of("a", 5L, "b", 4L, "c", 2L));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals(18L, result.getResult().orElse(null));
    }

    @Test
    @DisplayName("7. Should generate CompiledRule for floating point division")
    void testDivision() throws Exception {
        Rule rule = new RuleSchema("DivRule", "1.0.0", "Desc", "MATH", "total / count", Map.of("total", Double.class, "count", Double.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        ExecutionContext context = new ExecutionContext(Map.of("total", 10.0, "count", 4.0));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals(2.5, result.getResult().orElse(null));
    }

    @Test
    @DisplayName("8. Should generate CompiledRule for string concatenation")
    void testStringConcatenation() throws Exception {
        Rule rule = new RuleSchema("ConcatRule", "1.0.0", "Desc", "STRING", "prefix + suffix", Map.of("prefix", String.class, "suffix", String.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        ExecutionContext context = new ExecutionContext(Map.of("prefix", "JVM-", "suffix", "Engine"));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("JVM-Engine", result.getResult().orElse(null));
    }

    @Test
    @DisplayName("9. Should generate CompiledRule for unary NOT operation")
    void testUnaryNotExecution() throws Exception {
        Rule rule = new RuleSchema("NotRule", "1.0.0", "Desc", "BOOL", "!disabled", Map.of("disabled", Boolean.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        ExecutionContext context = new ExecutionContext(Map.of("disabled", false));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getResult().orElse(null));
    }

    @Test
    @DisplayName("10. Should generate CompiledRule for unary NEGATE operation")
    void testUnaryNegateExecution() throws Exception {
        Rule rule = new RuleSchema("NegateRule", "1.0.0", "Desc", "MATH", "-balance", Map.of("balance", Long.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        ExecutionContext context = new ExecutionContext(Map.of("balance", 500L));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals(-500L, result.getResult().orElse(null));
    }

    @Test
    @DisplayName("11. Should throw NullPointerException when rule or ast is null")
    void testNullRuleHandling() {
        assertThrows(NullPointerException.class, () -> generator.generate(null, new VariableNode("x")));
    }
}
