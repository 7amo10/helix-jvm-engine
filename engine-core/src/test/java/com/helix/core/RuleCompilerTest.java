package com.helix.core;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.RuleCompilationException;
import com.helix.core.parser.RuleSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleCompilerTest {

    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler();
    }

    @Test
    @DisplayName("1. Should compile JSON rule end-to-end using ByteBuddy and execute successfully")
    void testEndToEndJsonCompilationByteBuddy() throws Exception {
        String json = """
                {
                    "name": "HighValueTransaction",
                    "version": "1.0.0",
                    "description": "Flags transactions over 1000",
                    "category": "FRAUD",
                    "expression": "amount > 1000 && country.equals(\\"US\\")",
                    "inputSchema": {
                        "amount": "double",
                        "country": "string"
                    }
                }
                """;

        CompiledRule compiledRule = compiler.compile(json, RuleCompiler.GeneratorType.BYTE_BUDDY);
        assertNotNull(compiledRule);
        assertEquals("HighValueTransaction", compiledRule.getName());

        ExecutionContext passContext = new ExecutionContext(Map.of("amount", 1500.0, "country", "US"));
        ExecutionResult passResult = compiledRule.execute(passContext);
        assertTrue(passResult.isSuccess());
        assertEquals(Boolean.TRUE, passResult.getResult().orElse(null));
    }

    @Test
    @DisplayName("2. Should compile JSON rule end-to-end using ASM generator")
    void testEndToEndJsonCompilationAsm() throws Exception {
        String json = """
                {
                    "name": "AsmRuleTest",
                    "version": "1.0.0",
                    "expression": "x + y * 2",
                    "inputSchema": {
                        "x": "long",
                        "y": "long"
                    }
                }
                """;

        CompiledRule compiledRule = compiler.compile(json, RuleCompiler.GeneratorType.ASM);
        assertNotNull(compiledRule);

        ExecutionContext context = new ExecutionContext(Map.of("x", 10L, "y", 5L));
        ExecutionResult result = compiledRule.execute(context);
        assertTrue(result.isSuccess());
        assertEquals(20L, result.getResult().orElse(null));
    }

    @Test
    @DisplayName("3. Should compile Rule object instance directly")
    void testCompileRuleObjectInstance() throws Exception {
        Rule rule = new RuleSchema("DirectRule", "1.0.0", "Direct rule", "TEST", "val >= 50", Map.of("val", Integer.class));
        CompiledRule compiledRule = compiler.compile(rule);
        assertNotNull(compiledRule);

        ExecutionResult result = compiledRule.execute(new ExecutionContext(Map.of("val", 75)));
        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getResult().orElse(null));
    }

    @Test
    @DisplayName("4. Should report RuleCompilationException when JSON parsing fails")
    void testMalformedJsonReporting() {
        String invalidJson = "{ malformed json ";
        RuleCompilationException ex = assertThrows(RuleCompilationException.class, () -> compiler.compile(invalidJson));
        assertTrue(ex.getMessage().contains("JSON parsing stage"));
    }

    @Test
    @DisplayName("5. Should report RuleCompilationException when AST building fails")
    void testAstBuildingFailureReporting() {
        String json = """
                {
                    "name": "AstFailRule",
                    "expression": "x + + * invalid syntax",
                    "inputSchema": {
                        "x": "int"
                    }
                }
                """;

        RuleCompilationException ex = assertThrows(RuleCompilationException.class, () -> compiler.compile(json));
        assertTrue(ex.getMessage().contains("AST building stage"));
    }

    @Test
    @DisplayName("6. Should report RuleCompilationException when type checking fails")
    void testTypeCheckFailureReporting() {
        String json = """
                {
                    "name": "TypeFailRule",
                    "expression": "amount && true",
                    "inputSchema": {
                        "amount": "double"
                    }
                }
                """;

        RuleCompilationException ex = assertThrows(RuleCompilationException.class, () -> compiler.compile(json));
        assertTrue(ex.getMessage().contains("type checking stage"));
    }
}
