package com.helix.core;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Sprint2VerificationTest {

    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler();
    }

    @Test
    @DisplayName("End-of-Sprint Verification: Compile and execute amount > 1000 && country.equals(\"US\")")
    void testSprint2EndToEndVerification() throws Exception {
        String json = """
                {
                    "name": "Sprint2VerificationRule",
                    "version": "1.0.0",
                    "description": "Sprint 2 Verification Rule",
                    "category": "VERIFICATION",
                    "expression": "amount > 1000 && country.equals(\\"US\\")",
                    "inputSchema": {
                        "amount": "double",
                        "country": "string"
                    }
                }
                """;

        CompiledRule compiledRule = compiler.compile(json);
        assertNotNull(compiledRule);
        assertEquals("Sprint2VerificationRule", compiledRule.getName());

        // Pass case
        ExecutionContext passCtx = new ExecutionContext(Map.of("amount", 1500.0, "country", "US"));
        ExecutionResult passRes = compiledRule.execute(passCtx);
        assertTrue(passRes.isSuccess());
        assertEquals(Boolean.TRUE, passRes.getResult().orElse(null));

        // Fail case (amount <= 1000)
        ExecutionContext failAmountCtx = new ExecutionContext(Map.of("amount", 800.0, "country", "US"));
        ExecutionResult failAmountRes = compiledRule.execute(failAmountCtx);
        assertTrue(failAmountRes.isSuccess());
        assertEquals(Boolean.FALSE, failAmountRes.getResult().orElse(null));

        // Fail case (country != US)
        ExecutionContext failCountryCtx = new ExecutionContext(Map.of("amount", 2000.0, "country", "UK"));
        ExecutionResult failCountryRes = compiledRule.execute(failCountryCtx);
        assertTrue(failCountryRes.isSuccess());
        assertEquals(Boolean.FALSE, failCountryRes.getResult().orElse(null));
    }
}
