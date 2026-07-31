package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.parser.RuleSchema;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
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
    @DisplayName("Should generate CompiledRule for simple arithmetic expression")
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
    @DisplayName("Should generate CompiledRule for comparison expression: amount > 1000")
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
    @DisplayName("Should generate CompiledRule for logical operators: x > 10 && y < 20")
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
    @DisplayName("Should generate CompiledRule for method call expression: name.equals(\"test\")")
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
    @DisplayName("Should return failure ExecutionResult when missing required variable")
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
}
