package com.helix.experiments.benchmarks;

import com.helix.api.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionBenchmarkTest {

    @Test
    void testExecutionBenchmarkMethods() throws Exception {
        ExecutionBenchmark benchmark = new ExecutionBenchmark();
        benchmark.setup();

        ExecutionResult hotResult = benchmark.benchmarkHotRuleExecution();
        assertNotNull(hotResult);
        assertTrue(hotResult.isSuccess());
        assertEquals(Boolean.TRUE, hotResult.getResult().orElse(null));

        ExecutionResult concurrentResult = benchmark.benchmarkConcurrentRuleExecution();
        assertNotNull(concurrentResult);
        assertTrue(concurrentResult.isSuccess());

        ExecutionResult coldResult = benchmark.benchmarkColdRuleExecution();
        assertNotNull(coldResult);
        assertTrue(coldResult.isSuccess());
    }
}
