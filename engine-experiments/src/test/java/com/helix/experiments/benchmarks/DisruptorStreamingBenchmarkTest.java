package com.helix.experiments.benchmarks;

import com.helix.api.stream.StreamResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisruptorStreamingBenchmarkTest {

    @Test
    @DisplayName("Should execute all DisruptorStreamingBenchmark operations cleanly")
    void testDisruptorStreamingBenchmarkOperations() throws Exception {
        DisruptorStreamingBenchmark benchmark = new DisruptorStreamingBenchmark();
        benchmark.setup();

        try {
            // Sustained throughput operation
            benchmark.benchmarkSustainedThroughput();
            assertTrue(benchmark.getBridge().stats().getTotalPublished() > 0,
                    "Total published events must increase");

            // Ring buffer latency operation
            benchmark.benchmarkRingBufferLatency();

            // Synchronous round-trip latency operation
            StreamResult result = benchmark.benchmarkRoundTripLatency();
            assertNotNull(result, "Round-trip result must not be null");
            assertTrue(result.isSuccess(), "Round-trip event execution must succeed");
            assertEquals(Boolean.TRUE, result.getResult().orElse(null),
                    "Rule amount > 100 && status == 'ACTIVE' must evaluate to true");
        } finally {
            benchmark.tearDown();
        }
    }
}
