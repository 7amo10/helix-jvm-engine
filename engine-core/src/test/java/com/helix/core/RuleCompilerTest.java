package com.helix.core;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleCompilationException;
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
    @DisplayName("Should compile JSON rule end-to-end using ByteBuddy and execute successfully")
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
    @DisplayName("Should compile JSON rule end-to-end using ASM generator")
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
    @DisplayName("Should report RuleCompilationException when JSON parsing fails")
    void testMalformedJsonReporting() {
        String invalidJson = "{ malformed json ";
        RuleCompilationException ex = assertThrows(RuleCompilationException.class, () -> compiler.compile(invalidJson));
        assertTrue(ex.getMessage().contains("JSON parsing stage"));
    }

    @Test
    @DisplayName("Should report RuleCompilationException when type checking fails")
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
