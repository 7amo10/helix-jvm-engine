package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.RuleCompiler;
import com.helix.core.ml.LocalModelRegistry;
import com.helix.core.ml.OnnxModelExecutor;
import com.helix.core.ml.OnnxSessionPool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Real-World Scenario: End-to-End ASM Bytecode Rule Compilation & ML Inference")
class AsmRealScenarioIntegrationTest {

    private static LocalModelRegistry registry;
    private static OnnxSessionPool sessionPool;

    private static final List<String> FRAUD_FEATURES = List.of(
            "amount", "hour_of_day", "day_of_week", "merchant_category", "transaction_currency",
            "velocity_1h", "velocity_24h", "velocity_7d", "amount_deviation_30d", "unique_merchants_24h",
            "is_new_device", "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
            "account_age_days", "is_account_suspended", "previous_chargeback"
    );

    @BeforeAll
    static void setUp() {
        URL modelUrl = AsmRealScenarioIntegrationTest.class.getResource("/models/fraud_model_v1.onnx");
        assertNotNull(modelUrl);
        File modelFile = new File(modelUrl.getFile());

        OnnxModelDescriptor descriptor = OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.0.0")
                .modelPath(modelFile.getAbsolutePath())
                .inputFeatures(FRAUD_FEATURES)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .active(true)
                .build();

        registry = new LocalModelRegistry();
        registry.registerModel(descriptor);

        sessionPool = new OnnxSessionPool(registry, 8);
        OnnxModelExecutor.setSessionPool(sessionPool);
    }

    @AfterAll
    static void tearDown() {
        OnnxModelExecutor.reset();
        if (sessionPool != null) {
            sessionPool.close();
        }
    }

    private ExecutionContext createLegitContext(double amount) {
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", amount),
                Map.entry("hour_of_day", 10.0),
                Map.entry("day_of_week", 4.0),
                Map.entry("merchant_category", 0.0),
                Map.entry("transaction_currency", 0.0),
                Map.entry("velocity_1h", 1.0),
                Map.entry("velocity_24h", 5.0),
                Map.entry("velocity_7d", 21.0),
                Map.entry("amount_deviation_30d", -1.10),
                Map.entry("unique_merchants_24h", 5.0),
                Map.entry("is_new_device", 0.0),
                Map.entry("device_risk_score", 0.35),
                Map.entry("is_vpn_or_proxy", 0.0),
                Map.entry("country_mismatch", 0.0),
                Map.entry("account_age_days", 3511.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 1.0)
        ));
    }

    private ExecutionContext createFraudContext() {
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", 1728.70),
                Map.entry("hour_of_day", 2.0),
                Map.entry("day_of_week", 4.0),
                Map.entry("merchant_category", 3.0),
                Map.entry("transaction_currency", 2.0),
                Map.entry("velocity_1h", 5.0),
                Map.entry("velocity_24h", 20.0),
                Map.entry("velocity_7d", 65.0),
                Map.entry("amount_deviation_30d", 2.09),
                Map.entry("unique_merchants_24h", 9.0),
                Map.entry("is_new_device", 1.0),
                Map.entry("device_risk_score", 0.43),
                Map.entry("is_vpn_or_proxy", 0.0),
                Map.entry("country_mismatch", 1.0),
                Map.entry("account_age_days", 86.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 0.0)
        ));
    }

    @Test
    @DisplayName("Real Scenario: RuleCompiler(ASM) compiles compound ML rule and evaluates financial transactions accurately")
    void testCompiledAsmRuleEndToEnd() throws Exception {
        RuleCompiler compiler = new RuleCompiler(RuleCompiler.GeneratorType.ASM);

        String ruleJson = """
                {
                    "name": "HighValueMlFraudAlertRule",
                    "version": "1.0.0",
                    "expression": "ML('fraud_model_v1') > 0.85 && amount > 1000",
                    "inputSchema": {
                        "amount": "double",
                        "hour_of_day": "double",
                        "day_of_week": "double",
                        "merchant_category": "double",
                        "transaction_currency": "double",
                        "velocity_1h": "double",
                        "velocity_24h": "double",
                        "velocity_7d": "double",
                        "amount_deviation_30d": "double",
                        "unique_merchants_24h": "double",
                        "is_new_device": "double",
                        "device_risk_score": "double",
                        "is_vpn_or_proxy": "double",
                        "country_mismatch": "double",
                        "account_age_days": "double",
                        "is_account_suspended": "double",
                        "previous_chargeback": "double"
                    }
                }
                """;

        CompiledRule compiledRule = compiler.compile(ruleJson);
        assertNotNull(compiledRule);
        assertEquals("HighValueMlFraudAlertRule", compiledRule.getName());

        // 1. Benign purchase ($66.55, ML fraud prob 0.0028%)
        ExecutionResult legitResult = compiledRule.execute(createLegitContext(66.55));
        assertTrue(legitResult.isSuccess());
        assertEquals(Boolean.FALSE, legitResult.getResult().orElse(null));

        // 2. High amount benign purchase ($5,000.00, ML fraud prob 0.0028%)
        ExecutionResult highAmountLegitResult = compiledRule.execute(createLegitContext(5000.00));
        assertTrue(highAmountLegitResult.isSuccess());
        assertEquals(Boolean.FALSE, highAmountLegitResult.getResult().orElse(null));

        // 3. Real burst fraud attack ($1,728.70, ML fraud prob 99.9979%)
        ExecutionResult fraudResult = compiledRule.execute(createFraudContext());
        assertTrue(fraudResult.isSuccess());
        assertEquals(Boolean.TRUE, fraudResult.getResult().orElse(null));
    }

    @Test
    @DisplayName("Real Scenario: Compiled ASM rule executes under 0.4 ms SLA latency")
    void testCompiledAsmRuleLatency() throws Exception {
        RuleCompiler compiler = new RuleCompiler(RuleCompiler.GeneratorType.ASM);
        String ruleJson = """
                {
                    "name": "BenchmarkAsmRule",
                    "version": "1.0.0",
                    "expression": "ML('fraud_model_v1') > 0.85",
                    "inputSchema": {
                        "amount": "double",
                        "hour_of_day": "double",
                        "day_of_week": "double",
                        "merchant_category": "double",
                        "transaction_currency": "double",
                        "velocity_1h": "double",
                        "velocity_24h": "double",
                        "velocity_7d": "double",
                        "amount_deviation_30d": "double",
                        "unique_merchants_24h": "double",
                        "is_new_device": "double",
                        "device_risk_score": "double",
                        "is_vpn_or_proxy": "double",
                        "country_mismatch": "double",
                        "account_age_days": "double",
                        "is_account_suspended": "double",
                        "previous_chargeback": "double"
                    }
                }
                """;

        CompiledRule compiledRule = compiler.compile(ruleJson);
        ExecutionContext ctx = createLegitContext(100.0);

        // Warm up to allow JIT C2 optimization to compile execution path
        for (int i = 0; i < 1000; i++) {
            compiledRule.execute(ctx);
        }

        int iterations = 1000;
        List<Long> latencies = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            ExecutionResult res = compiledRule.execute(ctx);
            latencies.add(res.getExecutionTimeNanos());
        }

        Collections.sort(latencies);
        double p50Micros = latencies.get((int) (iterations * 0.50)) / 1000.0;
        double meanMicros = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000.0;

        System.out.printf("Compiled ASM Rule Execution: Mean = %.2f µs, P50 = %.2f µs%n", meanMicros, p50Micros);

        assertTrue(p50Micros < 400.0, "Compiled ASM rule P50 latency must be < 400 µs, was: " + p50Micros + " µs");
        assertTrue(meanMicros < 400.0, "Compiled ASM rule Mean latency must be < 400 µs, was: " + meanMicros + " µs");
    }
}
