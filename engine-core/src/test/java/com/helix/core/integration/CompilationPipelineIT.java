package com.helix.core.integration;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.RuleCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompilationPipelineIT {

    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler();
    }

    @Test
    @DisplayName("1. Should compile and execute simple rule: amount > 10")
    void testSimpleRuleCompilation() throws Exception {
        String jsonRule = """
                {
                    "name": "SimpleThresholdRule",
                    "expression": "amount > 10",
                    "inputSchema": {
                        "amount": "double"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(jsonRule);
        assertNotNull(rule);

        ExecutionResult resultPass = rule.execute(new ExecutionContext(Map.of("amount", 25.0)));
        assertTrue(resultPass.isSuccess());
        assertEquals(Boolean.TRUE, resultPass.getResult().orElse(null));

        ExecutionResult resultFail = rule.execute(new ExecutionContext(Map.of("amount", 5.0)));
        assertTrue(resultFail.isSuccess());
        assertEquals(Boolean.FALSE, resultFail.getResult().orElse(null));
    }

    @Test
    @DisplayName("2. Should compile complex rule with multiple sub-expressions")
    void testComplexRuleWithMultipleExpressions() throws Exception {
        String jsonRule = """
                {
                    "name": "LoanApprovalRule",
                    "version": "1.2.0",
                    "description": "Risk assessment rule for loan approval",
                    "category": "CREDIT",
                    "expression": "(score >= 700 || income > 50000) && !hasDefault",
                    "inputSchema": {
                        "score": "int",
                        "income": "long",
                        "hasDefault": "boolean"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(jsonRule);

        // Score 720, Income 30000, no default -> Approved
        ExecutionResult res1 = rule.execute(new ExecutionContext(Map.of("score", 720, "income", 30000L, "hasDefault", false)));
        assertTrue(res1.isSuccess());
        assertEquals(Boolean.TRUE, res1.getResult().orElse(null));

        // Score 650, Income 60000, no default -> Approved
        ExecutionResult res2 = rule.execute(new ExecutionContext(Map.of("score", 650, "income", 60000L, "hasDefault", false)));
        assertTrue(res2.isSuccess());
        assertEquals(Boolean.TRUE, res2.getResult().orElse(null));

        // Score 750, Income 80000, has default -> Rejected
        ExecutionResult res3 = rule.execute(new ExecutionContext(Map.of("score", 750, "income", 80000L, "hasDefault", true)));
        assertTrue(res3.isSuccess());
        assertEquals(Boolean.FALSE, res3.getResult().orElse(null));
    }

    @Test
    @DisplayName("3. Should compile rule using all supported arithmetic, relational, and logical operators")
    void testAllSupportedOperators() throws Exception {
        String jsonRule = """
                {
                    "name": "AllOperatorsRule",
                    "expression": "(a + b * 2 - c / 2 > 100) && (x == y || z != 5) && (u <= v && w >= 0)",
                    "inputSchema": {
                        "a": "int",
                        "b": "int",
                        "c": "int",
                        "x": "int",
                        "y": "int",
                        "z": "int",
                        "u": "double",
                        "v": "double",
                        "w": "double"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(jsonRule);
        ExecutionContext context = new ExecutionContext(Map.of(
                "a", 50, "b", 30, "c", 20,
                "x", 10, "y", 10, "z", 1,
                "u", 1.5, "v", 2.0, "w", 0.0
        ));

        ExecutionResult result = rule.execute(context);
        assertTrue(result.isSuccess());
        assertEquals(Boolean.FALSE, result.getResult().orElse(null));
    }

    @Test
    @DisplayName("4. Should compile rule with Java String method invocations")
    void testRuleWithMethodCalls() throws Exception {
        String jsonRule = """
                {
                    "name": "UserAuthenticationRule",
                    "expression": "email.endsWith(\\"@company.com\\") && name.equalsIgnoreCase(\\"Admin\\")",
                    "inputSchema": {
                        "email": "string",
                        "name": "string"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(jsonRule);

        ExecutionContext validAdmin = new ExecutionContext(Map.of("email", "john@company.com", "name", "admin"));
        ExecutionResult validRes = rule.execute(validAdmin);
        assertTrue(validRes.isSuccess());
        assertEquals(Boolean.TRUE, validRes.getResult().orElse(null));

        ExecutionContext invalidDomain = new ExecutionContext(Map.of("email", "john@gmail.com", "name", "admin"));
        ExecutionResult invalidRes = rule.execute(invalidDomain);
        assertTrue(invalidRes.isSuccess());
        assertEquals(Boolean.FALSE, invalidRes.getResult().orElse(null));
    }

    @Test
    @DisplayName("5. Should verify execution equivalence between ByteBuddy and ASM generators")
    void testByteBuddyAndAsmGeneratorsEquivalence() throws Exception {
        String jsonRule = """
                {
                    "name": "EquivalenceRule",
                    "expression": "x * 10 + y",
                    "inputSchema": {
                        "x": "long",
                        "y": "long"
                    }
                }
                """;

        CompiledRule byteBuddyRule = compiler.compile(jsonRule, RuleCompiler.GeneratorType.BYTE_BUDDY);
        CompiledRule asmRule = compiler.compile(jsonRule, RuleCompiler.GeneratorType.ASM);

        ExecutionContext context = new ExecutionContext(Map.of("x", 5L, "y", 7L));

        ExecutionResult byteBuddyRes = byteBuddyRule.execute(context);
        ExecutionResult asmRes = asmRule.execute(context);

        assertTrue(byteBuddyRes.isSuccess());
        assertTrue(asmRes.isSuccess());
        assertEquals(57L, byteBuddyRes.getResult().orElse(null));
        assertEquals(57L, asmRes.getResult().orElse(null));
    }
}
