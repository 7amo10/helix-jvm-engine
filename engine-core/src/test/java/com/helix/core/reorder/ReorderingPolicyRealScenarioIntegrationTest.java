package com.helix.core.reorder;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.bytecode.AdaptiveAstOptimizer;
import com.helix.core.bytecode.AsmBytecodeGenerator;
import com.helix.core.bytecode.OptimizationResult;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.ml.LocalModelRegistry;
import com.helix.core.ml.OnnxModelExecutor;
import com.helix.core.ml.OnnxSessionPool;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.profiler.node.AstNodeProfiler;
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

@DisplayName("Real Scenario: ReorderingPolicy SPI (RatioSort vs NeuralAstReordering)")
class ReorderingPolicyRealScenarioIntegrationTest {

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
        // Register fraud model
        URL fraudUrl = ReorderingPolicyRealScenarioIntegrationTest.class.getResource("/models/fraud_model_v1.onnx");
        assertNotNull(fraudUrl);
        File fraudFile = new File(fraudUrl.getFile());

        OnnxModelDescriptor fraudDescriptor = OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.0.0")
                .modelPath(fraudFile.getAbsolutePath())
                .inputFeatures(FRAUD_FEATURES)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .active(true)
                .build();

        // Register AST reordering policy model
        URL policyUrl = ReorderingPolicyRealScenarioIntegrationTest.class.getResource("/models/ast_reorder_policy.onnx");
        assertNotNull(policyUrl);
        File policyFile = new File(policyUrl.getFile());

        OnnxModelDescriptor policyDescriptor = OnnxModelDescriptor.builder()
                .modelName("ast_reorder_policy")
                .version("1.0.0")
                .modelPath(policyFile.getAbsolutePath())
                .inputFeatures(List.of("observation"))
                .outputTensorName("action_logits")
                .outputIndex(0)
                .active(true)
                .build();

        registry = new LocalModelRegistry();
        registry.registerModel(fraudDescriptor);
        registry.registerModel(policyDescriptor);

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

    private ExecutionContext createTransactionContext(double amount, double vpn, boolean isFraud) {
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", amount),
                Map.entry("hour_of_day", isFraud ? 2.0 : 10.0),
                Map.entry("day_of_week", 4.0),
                Map.entry("merchant_category", isFraud ? 3.0 : 0.0),
                Map.entry("transaction_currency", isFraud ? 2.0 : 0.0),
                Map.entry("velocity_1h", isFraud ? 5.0 : 1.0),
                Map.entry("velocity_24h", isFraud ? 20.0 : 5.0),
                Map.entry("velocity_7d", isFraud ? 65.0 : 21.0),
                Map.entry("amount_deviation_30d", isFraud ? 2.09 : -1.10),
                Map.entry("unique_merchants_24h", isFraud ? 9.0 : 5.0),
                Map.entry("is_new_device", isFraud ? 1.0 : 0.0),
                Map.entry("device_risk_score", isFraud ? 0.43 : 0.35),
                Map.entry("is_vpn_or_proxy", vpn),
                Map.entry("country_mismatch", isFraud ? 1.0 : 0.0),
                Map.entry("account_age_days", isFraud ? 86.0 : 3511.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", isFraud ? 0.0 : 1.0)
        ));
    }

    @Test
    @DisplayName("Real Scenario: NeuralAstReorderingPolicy achieves superior/identical latency reduction and pushes ML to tail")
    void testRealWorldNeuralPolicyReordering() throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        // Unoptimized expression: expensive ML check first, then cheap checks
        String unoptimizedExpr = "ML('fraud_model_v1') > 0.85 && amount > 1000 && is_vpn_or_proxy == 1.0";
        ExpressionNode ast = parser.parse(unoptimizedExpr);
        Rule rule = new RuleNode("NeuralAdaptiveRule", unoptimizedExpr, Map.of(), ast);

        TieredRuleCache cache = new TieredRuleCache();
        CacheKey cacheKey = new CacheKey(rule);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator(true);
        CompiledRule unoptimizedRule = generator.generate(rule, ast);
        cache.put(cacheKey, unoptimizedRule);

        // 1,000 transactions:
        // 900 benign transactions (vpn=0, amount=50) -> short circuit early
        // 50 suspicious transactions (vpn=1, amount=500)
        // 50 actual fraud transactions (vpn=1, amount=2000)
        List<ExecutionContext> workload = new ArrayList<>(1000);
        for (int i = 0; i < 900; i++) {
            workload.add(createTransactionContext(50.0, 0.0, false));
        }
        for (int i = 0; i < 50; i++) {
            workload.add(createTransactionContext(500.0, 1.0, false));
        }
        for (int i = 0; i < 50; i++) {
            workload.add(createTransactionContext(2000.0, 1.0, true));
        }

        // --- PHASE 1: Baseline Execution (Unoptimized) ---
        List<Long> baselineLatencies = new ArrayList<>(1000);
        List<Boolean> baselineResults = new ArrayList<>(1000);

        for (ExecutionContext ctx : workload) {
            long t0 = System.nanoTime();
            ExecutionResult res = cache.get(cacheKey).orElseThrow().execute(ctx);
            baselineLatencies.add(System.nanoTime() - t0);
            baselineResults.add((Boolean) res.getResult().orElse(null));
        }

        Collections.sort(baselineLatencies);
        double baselineMean = baselineLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long baselineP99 = baselineLatencies.get(990);

        // --- PHASE 2: Dynamic Reordering via NeuralAstReorderingPolicy ---
        NeuralAstReorderingPolicy neuralPolicy = new NeuralAstReorderingPolicy(sessionPool);
        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer(neuralPolicy);

        OptimizationResult optResult = optimizer.optimizeAndHotSwap(rule, ast, cache);
        assertTrue(optResult.reordered(), "Neural policy must reorder the AST");

        // --- PHASE 3: Optimized Execution (Post Hot-Swap) ---
        List<Long> optLatencies = new ArrayList<>(1000);
        List<Boolean> optResults = new ArrayList<>(1000);

        for (ExecutionContext ctx : workload) {
            long t0 = System.nanoTime();
            ExecutionResult res = cache.get(cacheKey).orElseThrow().execute(ctx);
            optLatencies.add(System.nanoTime() - t0);
            optResults.add((Boolean) res.getResult().orElse(null));
        }

        Collections.sort(optLatencies);
        double optMean = optLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long optP99 = optLatencies.get(990);

        // --- PHASE 4: Verification of Acceptance Criteria ---
        // 1. Exact boolean logic equivalence: 1,000 / 1,000 match
        assertEquals(baselineResults, optResults, "All execution outcomes must be identical");

        // 2. Latency improvement: Mean and P99 latency must drop by >= 40%
        double meanImprovement = (baselineMean - optMean) / baselineMean;
        double p99Improvement = (double) (baselineP99 - optP99) / baselineP99;

        System.out.printf("Neural Policy Performance: Baseline Mean=%.2f us, Opt Mean=%.2f us (%.2f%% drop)%n",
                baselineMean / 1000.0, optMean / 1000.0, meanImprovement * 100.0);
        System.out.printf("Neural Policy Performance: Baseline P99=%.2f us, Opt P99=%.2f us (%.2f%% drop)%n",
                baselineP99 / 1000.0, optP99 / 1000.0, p99Improvement * 100.0);

        assertTrue(meanImprovement >= 0.40,
                String.format("Mean latency must drop by >= 40%% (actual: %.2f%%)", meanImprovement * 100.0));
        assertTrue(p99Improvement >= 0.40,
                String.format("P99 latency must drop by >= 40%% (actual: %.2f%%)", p99Improvement * 100.0));

        cache.close();
    }
}
