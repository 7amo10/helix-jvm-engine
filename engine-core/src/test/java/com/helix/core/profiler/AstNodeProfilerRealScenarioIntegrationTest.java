package com.helix.core.profiler;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.bytecode.AsmBytecodeGenerator;
import com.helix.core.ml.LocalModelRegistry;
import com.helix.core.ml.OnnxModelExecutor;
import com.helix.core.ml.OnnxSessionPool;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.profiler.jfr.JfrRecordingManager;
import com.helix.profiler.node.AstNodeProfiler;
import com.helix.profiler.node.NodeStats;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Real-World Scenario: AstNodeProfiler & JFR Telemetry for Financial ML Rules")
class AstNodeProfilerRealScenarioIntegrationTest {

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
        URL modelUrl = AstNodeProfilerRealScenarioIntegrationTest.class.getResource("/models/fraud_model_v1.onnx");
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

        AstNodeProfiler.reset();
        AstNodeProfiler.setEnabled(true);
    }

    @AfterAll
    static void tearDown() {
        AstNodeProfiler.reset();
        OnnxModelExecutor.reset();
        if (sessionPool != null) {
            sessionPool.close();
        }
    }

    private ExecutionContext createTransactionContext(double amount, boolean isFraud) {
        if (isFraud) {
            return new ExecutionContext(Map.ofEntries(
                    Map.entry("amount", amount),
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
        } else {
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
    }

    @Test
    @DisplayName("Real Scenario: AstNodeProfiler tracks cost-to-failure ratio accurately and emits JFR events")
    void testRealWorldNodeProfilingAndJfrEmission(@TempDir Path tempDir) throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        String expression = "ML('fraud_model_v1') > 0.85 && amount > 1000";
        ExpressionNode ast = parser.parse(expression);
        Rule rule = new RuleNode("FinancialFraudRule", expression, Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator(true); // profiling enabled
        CompiledRule compiledRule = generator.generate(rule, ast);

        // Start JFR Recording to capture telemetry
        JfrRecordingManager jfrManager = new JfrRecordingManager();
        jfrManager.startRecording("HelixNodeProfilingRealScenario");

        // Simulate a traffic workload of 1000 transactions:
        // - 950 benign purchases (varying amounts from $15 to $2500)
        // - 50 fraudulent burst transactions (amounts > $1000)
        int benignCount = 950;
        int fraudCount = 50;

        for (int i = 0; i < benignCount; i++) {
            double amount = (i % 5 == 0) ? 1500.0 : 65.0; // 20% high amount, 80% low amount
            ExecutionResult res = compiledRule.execute(createTransactionContext(amount, false));
            assertTrue(res.isSuccess());
            assertEquals(Boolean.FALSE, res.getResult().orElse(null));
        }

        for (int i = 0; i < fraudCount; i++) {
            ExecutionResult res = compiledRule.execute(createTransactionContext(1850.0, true));
            assertTrue(res.isSuccess());
            assertEquals(Boolean.TRUE, res.getResult().orElse(null));
        }

        // Emit JFR Events for all tracked nodes
        AstNodeProfiler.emitAllJfrEvents();

        jfrManager.stopRecording();
        Path jfrFile = tempDir.resolve("real-scenario-node-profile.jfr");
        jfrManager.dumpRecording(jfrFile);

        // Verify NodeStats telemetry accuracy
        Map<String, NodeStats> stats = AstNodeProfiler.getAllStats();
        assertFalse(stats.isEmpty(), "NodeStats should contain profiling statistics");

        // Find ML node stats
        NodeStats mlStats = stats.values().stream()
                .filter(s -> s.getNodeId().contains("fraud_model_v1") || s.getNodeId().contains("ml"))
                .findFirst()
                .orElse(null);

        assertNotNull(mlStats, "Must have tracked ML inference node stats");
        assertEquals(1000L, mlStats.getExecutionCount(), "ML node must have executed 1000 times");

        // Benign transactions have ~0.0028% fraud probability, so they fail (> 0.85 is false) 950 times
        assertEquals(950L, mlStats.getFailureCount(), "ML node failure count should equal benign count (950)");
        assertEquals(0.95, mlStats.getFailureRate(), 0.01, "ML node failure rate should be 95%");

        // ML inference average cost should reflect real ONNX execution (> 10,000 ns = > 10 µs)
        assertTrue(mlStats.getAverageCostNanos() > 10_000.0,
                "ML node average cost should be > 10 µs, was: " + mlStats.getAverageCostNanos() + " ns");

        // Verify JFR Recording parsed cleanly
        assertTrue(Files.exists(jfrFile));
        List<RecordedEvent> events = RecordingFile.readAllEvents(jfrFile);
        List<RecordedEvent> profileEvents = events.stream()
                .filter(e -> "com.helix.NodeProfile".equals(e.getEventType().getName()))
                .toList();

        assertFalse(profileEvents.isEmpty(), "JFR recording must contain com.helix.NodeProfile events");

        boolean foundMlEvent = profileEvents.stream()
                .anyMatch(e -> e.getString("nodeId").contains("fraud_model_v1") || e.getString("nodeId").contains("ml"));
        assertTrue(foundMlEvent, "JFR recording must include ML node profile telemetry");

        jfrManager.close();
    }
}
