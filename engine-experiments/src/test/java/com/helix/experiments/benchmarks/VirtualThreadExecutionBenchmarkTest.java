package com.helix.experiments.benchmarks;

import com.helix.api.ExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VirtualThreadExecutionBenchmarkTest {

    private VirtualThreadExecutionBenchmark benchmark;

    @BeforeEach
    void setUp() throws Exception {
        benchmark = new VirtualThreadExecutionBenchmark();
        benchmark.setup();
    }

    @AfterEach
    void tearDown() {
        benchmark.tearDown();
    }

    @Test
    @DisplayName("Should execute sync, thread-pool, and virtual-thread benchmark routines")
    void testBenchmarkMethods() throws Exception {
        ExecutionResult syncRes = benchmark.benchmarkSyncExecutor();
        assertNotNull(syncRes);
        assertTrue(syncRes.isSuccess());

        ExecutionResult tpRes = benchmark.benchmarkThreadPoolExecutor();
        assertNotNull(tpRes);
        assertTrue(tpRes.isSuccess());

        ExecutionResult vtRes = benchmark.benchmarkVirtualThreadExecutor();
        assertNotNull(vtRes);
        assertTrue(vtRes.isSuccess());

        List<ExecutionResult> batchRes = benchmark.benchmarkVirtualThreadStructuredFanOut();
        assertNotNull(batchRes);
        assertEquals(100, batchRes.size());
        assertTrue(batchRes.stream().allMatch(ExecutionResult::isSuccess));
    }
}
