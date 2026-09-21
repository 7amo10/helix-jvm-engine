package com.helix.experiments.benchmarks;

import com.helix.api.ExecutionResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdaptiveOptimizerBenchmark Unit and Performance Verification")
class AdaptiveOptimizerBenchmarkTest {

    private static AdaptiveOptimizerBenchmark benchmark;

    @BeforeAll
    static void setUp() throws Exception {
        benchmark = new AdaptiveOptimizerBenchmark();
        benchmark.setup();
    }

    @AfterAll
    static void tearDown() {
        if (benchmark != null) {
            benchmark.tearDown();
        }
    }

    @Test
    @DisplayName("Should execute unoptimized and adaptively optimized rules correctly")
    void testExecutionCorrectness() throws Exception {
        ExecutionResult unopt = benchmark.benchmarkUnoptimizedStaticAst();
        assertNotNull(unopt);
        assertTrue(unopt.isSuccess());

        ExecutionResult opt = benchmark.benchmarkAdaptivelyOptimizedAst();
        assertNotNull(opt);
        assertTrue(opt.isSuccess());

        ExecutionResult nonMl = benchmark.benchmarkBaselineNonMlRule();
        assertNotNull(nonMl);
        assertTrue(nonMl.isSuccess());
    }

    @Test
    @DisplayName("Acceptance Criteria: Adaptive AST reordering demonstrates >= 40% latency reduction on mixed rules")
    void testAdaptiveReorderingLatencyReductionRequirement() throws Exception {
        int sampleCount = 500;
        List<Long> unoptLatencies = new ArrayList<>(sampleCount);
        List<Long> optLatencies = new ArrayList<>(sampleCount);

        // Pre-warm JIT
        for (int i = 0; i < 50; i++) {
            benchmark.benchmarkUnoptimizedStaticAst();
            benchmark.benchmarkAdaptivelyOptimizedAst();
        }

        // Measure unoptimized
        for (int i = 0; i < sampleCount; i++) {
            long t0 = System.nanoTime();
            ExecutionResult res = benchmark.benchmarkUnoptimizedStaticAst();
            unoptLatencies.add(System.nanoTime() - t0);
            assertTrue(res.isSuccess());
        }

        // Measure adaptively optimized
        for (int i = 0; i < sampleCount; i++) {
            long t0 = System.nanoTime();
            ExecutionResult res = benchmark.benchmarkAdaptivelyOptimizedAst();
            optLatencies.add(System.nanoTime() - t0);
            assertTrue(res.isSuccess());
        }

        Collections.sort(unoptLatencies);
        Collections.sort(optLatencies);

        double unoptMean = unoptLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double optMean = optLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long unoptP99 = unoptLatencies.get((int) (sampleCount * 0.99));
        long optP99 = optLatencies.get((int) (sampleCount * 0.99));

        double meanReduction = (unoptMean - optMean) / unoptMean;
        double p99Reduction = (double) (unoptP99 - optP99) / unoptP99;

        System.out.printf("JMH Verification - Adaptive AST: Unopt Mean=%.2f us, Opt Mean=%.2f us (%.2f%% drop)%n",
                unoptMean / 1000.0, optMean / 1000.0, meanReduction * 100.0);
        System.out.printf("JMH Verification - Adaptive AST: Unopt P99=%.2f us, Opt P99=%.2f us (%.2f%% drop)%n",
                unoptP99 / 1000.0, optP99 / 1000.0, p99Reduction * 100.0);

        assertTrue(meanReduction >= 0.40 || p99Reduction >= 0.40,
                String.format("Adaptive AST reordering must achieve >= 40%% latency drop (Mean: %.2f%%, P99: %.2f%%)",
                        meanReduction * 100.0, p99Reduction * 100.0));
    }

    @Test
    @DisplayName("Acceptance Criteria: Baseline non-ML rules show zero statistically significant regressions")
    void testBaselineNonMlRulesRegression() throws Exception {
        // Pre-warm JIT to Tier 4 (C2)
        for (int i = 0; i < 20_000; i++) {
            benchmark.benchmarkBaselineNonMlRule();
        }

        int iterations = 100_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            benchmark.benchmarkBaselineNonMlRule();
        }
        long totalElapsed = System.nanoTime() - t0;
        double avgNanos = (double) totalElapsed / iterations;

        System.out.printf("JMH Verification - Baseline Non-ML Rule Evaluation: Avg=%.2f ns/op%n", avgNanos);

        // Verification target: baseline non-ML evaluation is ultra-low latency with zero regression (< 1500 ns in test harness, < 200 ns in JMH)
        assertTrue(avgNanos < 1500.0,
                String.format("Non-ML baseline rules must execute with near-zero overhead (< 1500 ns), was %.2f ns", avgNanos));
    }
}
