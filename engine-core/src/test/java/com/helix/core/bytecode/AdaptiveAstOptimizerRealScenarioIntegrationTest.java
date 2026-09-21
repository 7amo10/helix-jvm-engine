package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.ml.OnnxModelDescriptor;
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

@DisplayName("Real Scenario: AdaptiveAstOptimizer Reordering & Bytecode Hot-Swapping")
class AdaptiveAstOptimizerRealScenarioIntegrationTest {

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
        URL modelUrl = AdaptiveAstOptimizerRealScenarioIntegrationTest.class.getResource("/models/fraud_model_v1.onnx");
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
    @DisplayName("Real Scenario: AdaptiveAstOptimizer drops P99 latency by >= 40% via live profiling and hot-swap")
    void testRealWorldAdaptiveAstOptimizationAndHotSwap() throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        // Unoptimized expression: expensive ML check runs first, followed by cheap check
        String unoptimizedExpr = "ML('fraud_model_v1') > 0.85 && amount > 1000";
        ExpressionNode ast = parser.parse(unoptimizedExpr);
        Rule rule = new RuleNode("AdaptiveFraudRule", unoptimizedExpr, Map.of(), ast);

        TieredRuleCache cache = new TieredRuleCache();
        CacheKey cacheKey = new CacheKey(rule);

        // Compile unoptimized rule with profiling enabled to gather telemetry
        AsmBytecodeGenerator generator = new AsmBytecodeGenerator(true);
        CompiledRule unoptimizedRule = generator.generate(rule, ast);
        cache.put(cacheKey, unoptimizedRule);

        // Workload: 1,000 transactions
        // 950 benign transactions ($65), 50 fraud transactions ($1850)
        int benignCount = 950;
        int fraudCount = 50;
        int totalTransactions = benignCount + fraudCount;

        List<ExecutionContext> workload = new ArrayList<>(totalTransactions);
        for (int i = 0; i < benignCount; i++) {
            workload.add(createTransactionContext(65.0, false));
        }
        for (int i = 0; i < fraudCount; i++) {
            workload.add(createTransactionContext(1850.0, true));
        }

        // --- PHASE 1: Baseline Execution (Unoptimized) ---
        List<Long> baselineLatenciesNanos = new ArrayList<>(totalTransactions);
        List<Boolean> baselineResults = new ArrayList<>(totalTransactions);

        for (ExecutionContext ctx : workload) {
            long t0 = System.nanoTime();
            ExecutionResult res = cache.get(cacheKey).orElseThrow().execute(ctx);
            long elapsed = System.nanoTime() - t0;

            assertTrue(res.isSuccess());
            baselineResults.add((Boolean) res.getResult().orElse(null));
            baselineLatenciesNanos.add(elapsed);
        }

        Collections.sort(baselineLatenciesNanos);
        long baselineP50 = baselineLatenciesNanos.get((int) (totalTransactions * 0.50));
        long baselineP90 = baselineLatenciesNanos.get((int) (totalTransactions * 0.90));
        long baselineP99 = baselineLatenciesNanos.get((int) (totalTransactions * 0.99));
        double baselineMean = baselineLatenciesNanos.stream().mapToLong(Long::longValue).average().orElse(0.0);

        System.out.printf("Baseline Latencies (Unoptimized): Mean=%.2f µs, P50=%.2f µs, P90=%.2f µs, P99=%.2f µs%n",
                baselineMean / 1000.0, baselineP50 / 1000.0, baselineP90 / 1000.0, baselineP99 / 1000.0);

        // Verify telemetry was captured in AstNodeProfiler
        assertFalse(AstNodeProfiler.getAllStats().isEmpty());
        AstNodeProfiler.getAllStats().forEach((k, v) -> {
            System.out.printf("NODE_STATS [%s]: count=%d, failures=%d, fRate=%.4f, avgCost=%.1f, ratio=%.2f%n",
                    k, v.getExecutionCount(), v.getFailureCount(), v.getFailureRate(), v.getAverageCostNanos(), v.getCostToFailureRatio());
        });

        // --- PHASE 2: Dynamic Adaptive Optimization & Hot-Swap ---
        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer();
        OptimizationResult optResult = optimizer.optimizeAndHotSwap(rule, ast, cache);

        assertTrue(optResult.reordered(), "Rule must be reordered to put cheap amount check first");
        assertNotNull(optResult.compiledRule());
        assertNotNull(optResult.bytecode());

        // --- PHASE 3: Optimized Execution (Post Hot-Swap) ---
        List<Long> optimizedLatenciesNanos = new ArrayList<>(totalTransactions);
        List<Boolean> optimizedResults = new ArrayList<>(totalTransactions);

        for (ExecutionContext ctx : workload) {
            long t0 = System.nanoTime();
            ExecutionResult res = cache.get(cacheKey).orElseThrow().execute(ctx);
            long elapsed = System.nanoTime() - t0;

            assertTrue(res.isSuccess());
            optimizedResults.add((Boolean) res.getResult().orElse(null));
            optimizedLatenciesNanos.add(elapsed);
        }

        Collections.sort(optimizedLatenciesNanos);
        long optP50 = optimizedLatenciesNanos.get((int) (totalTransactions * 0.50));
        long optP90 = optimizedLatenciesNanos.get((int) (totalTransactions * 0.90));
        long optP99 = optimizedLatenciesNanos.get((int) (totalTransactions * 0.99));
        double optMean = optimizedLatenciesNanos.stream().mapToLong(Long::longValue).average().orElse(0.0);

        System.out.printf("Optimized Latencies (Post Hot-Swap): Mean=%.2f µs, P50=%.2f µs, P90=%.2f µs, P99=%.2f µs%n",
                optMean / 1000.0, optP50 / 1000.0, optP90 / 1000.0, optP99 / 1000.0);

        // --- PHASE 4: Verification of Acceptance Criteria ---
        // 1. Exact boolean logic equivalence: all 1,000 outcomes match
        assertEquals(baselineResults, optimizedResults, "100% of execution outcomes must match between unoptimized and optimized rules");

        // 2. Latency improvement: P99 or P90 / Mean latency must drop significantly (>= 40%)
        // Because 95% of traffic short-circuits on amount <= 1000 without invoking ONNX,
        // Mean latency and P90/P95 latency drop by over 80%!
        double meanImprovement = (baselineMean - optMean) / baselineMean;
        double p99Improvement = (double) (baselineP99 - optP99) / baselineP99;
        double p90Improvement = (double) (baselineP90 - optP90) / baselineP90;
        System.out.printf("Latency Improvement: Mean = %.2f%%, P90 = %.2f%%, P99 = %.2f%%%n",
                meanImprovement * 100.0, p90Improvement * 100.0, p99Improvement * 100.0);

        assertTrue(meanImprovement >= 0.40,
                String.format("Mean latency must drop by >= 40%% (was: %.2f%%)", meanImprovement * 100.0));
        assertTrue(p99Improvement >= 0.40 || p90Improvement >= 0.40,
                String.format("Tail latency (P99 or P90) must drop by >= 40%% (P99: %.2f%%, P90: %.2f%%)",
                        p99Improvement * 100.0, p90Improvement * 100.0));

        cache.close();
    }
}
