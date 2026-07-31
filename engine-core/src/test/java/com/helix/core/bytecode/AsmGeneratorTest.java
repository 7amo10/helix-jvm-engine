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

class AsmGeneratorTest {

    private AsmGenerator generator;
    private AstBuilder astBuilder;

    @BeforeEach
    void setUp() {
        generator = new AsmGenerator();
        astBuilder = new AstBuilder();
    }

    @Test
    @DisplayName("Should generate CompiledRule using ASM for simple arithmetic")
    void testAsmSimpleArithmetic() throws Exception {
        Rule rule = new RuleSchema("AsmArithmetic", "1.0.0", "ASM test", "POC", "x + y", Map.of("x", Integer.class, "y", Integer.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        assertNotNull(compiledRule);
        assertEquals("AsmArithmetic", compiledRule.getName());
        assertEquals("1.0.0", compiledRule.getVersion());

        ExecutionContext context = new ExecutionContext(Map.of("x", 15L, "y", 25L));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals(40L, result.getResult().orElse(null));
        assertTrue(result.getExecutionTimeNanos() > 0);
    }

    @Test
    @DisplayName("Should generate CompiledRule using ASM for comparison expression")
    void testAsmComparison() throws Exception {
        Rule rule = new RuleSchema("AsmComparison", "1.0.0", "ASM test", "POC", "amount > 1000", Map.of("amount", Long.class));
        ExpressionNode ast = astBuilder.buildAst(rule.getExpression());

        CompiledRule compiledRule = generator.generate(rule, ast);
        ExecutionContext context = new ExecutionContext(Map.of("amount", 2000L));
        ExecutionResult result = compiledRule.execute(context);

        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getResult().orElse(null));
    }
}
