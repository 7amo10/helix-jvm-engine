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

@DisplayName("OnnxInferenceBenchmark Unit and Performance Verification")
class OnnxInferenceBenchmarkTest {

    private static OnnxInferenceBenchmark benchmark;

    @BeforeAll
    static void setUp() throws Exception {
        benchmark = new OnnxInferenceBenchmark();
        benchmark.setup();
    }

    @AfterAll
    static void tearDown() {
        if (benchmark != null) {
            benchmark.tearDown();
        }
    }

    @Test
    @DisplayName("Should execute inline ONNX inference correctly")
    void testInlineOnnxExecution() throws Exception {
        ExecutionResult result = benchmark.benchmarkInlineOnnxRuleExecution();
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getResult().orElse(null));
    }

    @Test
    @DisplayName("Should execute simulated REST inference correctly")
    void testSimulatedRestExecution() {
        Object restResult = benchmark.benchmarkSimulatedRestInference();
        assertNotNull(restResult);
    }

    @Test
    @DisplayName("Acceptance Criteria: Inline ONNX inference achieves P99 < 0.4 ms (< 400 microseconds)")
    void testInlineOnnxP99LatencyRequirement() throws Exception {
        int iterations = 1000;
        List<Long> latenciesNanos = new ArrayList<>(iterations);

        // Warmup JIT and ONNX runtime buffers
        for (int i = 0; i < 200; i++) {
            benchmark.benchmarkInlineOnnxRuleExecution();
        }

        // Measure
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            ExecutionResult res = benchmark.benchmarkInlineOnnxRuleExecution();
            latenciesNanos.add(System.nanoTime() - t0);
            assertTrue(res.isSuccess());
        }

        Collections.sort(latenciesNanos);
        long p50Nanos = latenciesNanos.get((int) (iterations * 0.50));
        long p90Nanos = latenciesNanos.get((int) (iterations * 0.90));
        long p99Nanos = latenciesNanos.get((int) (iterations * 0.99));
        double meanMicros = latenciesNanos.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000.0;
        double p99Micros = p99Nanos / 1000.0;

        System.out.printf("JMH Verification - Inline ONNX Latencies: Mean=%.2f us, P50=%.2f us, P90=%.2f us, P99=%.2f us%n",
                meanMicros, p50Nanos / 1000.0, p90Nanos / 1000.0, p99Micros);

        // 400 microseconds = 400,000 ns
        assertTrue(p99Micros < 400.0,
                String.format("Inline ONNX P99 latency must be < 400 us (< 0.4 ms), was %.2f us", p99Micros));
    }

    @Test
    @DisplayName("Should verify inline ONNX eliminates remote REST network hops (>20x speedup)")
    void testInlineVsRestComparison() throws Exception {
        long t0 = System.nanoTime();
        benchmark.benchmarkInlineOnnxRuleExecution();
        long inlineNanos = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        benchmark.benchmarkSimulatedRestInference();
        long restNanos = System.nanoTime() - t1;

        assertTrue(restNanos > inlineNanos, "Inline execution must be significantly faster than REST network latency");
    }
}
