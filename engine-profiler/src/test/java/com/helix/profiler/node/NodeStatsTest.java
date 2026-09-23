package com.helix.profiler.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeStats Unit and Concurrency Tests")
class NodeStatsTest {

    @Test
    @DisplayName("Should initialize with zero statistics")
    void testInitialState() {
        NodeStats stats = new NodeStats("rule1", "node_1");

        assertEquals("rule1", stats.getRuleName());
        assertEquals("node_1", stats.getNodeId());
        assertEquals(0L, stats.getExecutionCount());
        assertEquals(0L, stats.getTotalDurationNanos());
        assertEquals(0L, stats.getFailureCount());
        assertEquals(0.0, stats.getAverageCostNanos());
        assertEquals(0.0, stats.getFailureRate());
        assertEquals(Double.POSITIVE_INFINITY, stats.getCostToFailureRatio());
    }

    @Test
    @DisplayName("Should accurately record single execution metrics")
    void testRecordSingleExecution() {
        NodeStats stats = new NodeStats("rule1", "node_1");

        // Record a false evaluation (short-circuit / failure) taking 150 ns
        stats.record(150L, false);

        assertEquals(1L, stats.getExecutionCount());
        assertEquals(150L, stats.getTotalDurationNanos());
        assertEquals(1L, stats.getFailureCount());
        assertEquals(150.0, stats.getAverageCostNanos());
        assertEquals(1.0, stats.getFailureRate());
        assertEquals(150.0, stats.getCostToFailureRatio()); // 150 / 1.0 = 150.0

        // Record a true evaluation taking 50 ns
        stats.record(50L, true);

        assertEquals(2L, stats.getExecutionCount());
        assertEquals(200L, stats.getTotalDurationNanos());
        assertEquals(1L, stats.getFailureCount());
        assertEquals(100.0, stats.getAverageCostNanos()); // 200 / 2 = 100.0
        assertEquals(0.5, stats.getFailureRate());        // 1 / 2 = 0.5
        assertEquals(200.0, stats.getCostToFailureRatio()); // 100.0 / 0.5 = 200.0
    }

    @Test
    @DisplayName("Should handle 100+ concurrent threads without race conditions or lost updates")
    void testConcurrentExecutionRecording() throws InterruptedException {
        NodeStats stats = new NodeStats("ruleConcur", "node_c");
        int threadCount = 64;
        int opsPerThread = 10_000;
        long durationPerOp = 25L;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final boolean result = (t % 2 == 0); // 50% true, 50% false
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        stats.record(durationPerOp, result);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long expectedTotalOps = (long) threadCount * opsPerThread;
        long expectedFailures = expectedTotalOps / 2;
        long expectedTotalNanos = expectedTotalOps * durationPerOp;

        assertEquals(expectedTotalOps, stats.getExecutionCount());
        assertEquals(expectedFailures, stats.getFailureCount());
        assertEquals(expectedTotalNanos, stats.getTotalDurationNanos());
        assertEquals(25.0, stats.getAverageCostNanos(), 0.001);
        assertEquals(0.5, stats.getFailureRate(), 0.001);
        assertEquals(50.0, stats.getCostToFailureRatio(), 0.001);
    }

    @Test
    @DisplayName("Should generate immutable Snapshot of current state")
    void testSnapshot() {
        NodeStats stats = new NodeStats("rule1", "node_snap");
        stats.record(400L, false);
        stats.record(600L, true);

        NodeStats.Snapshot snapshot = stats.snapshot();
        assertEquals("rule1", snapshot.ruleName());
        assertEquals("node_snap", snapshot.nodeId());
        assertEquals(2L, snapshot.executionCount());
        assertEquals(1000L, snapshot.totalDurationNanos());
        assertEquals(1L, snapshot.failureCount());
        assertEquals(500.0, snapshot.avgCostNanos());
        assertEquals(0.5, snapshot.failureRate());
        assertEquals(1000.0, snapshot.costToFailureRatio());

        // Subsequent records shouldn't affect snapshot
        stats.record(200L, true);
        assertEquals(2L, snapshot.executionCount());
        assertEquals(3L, stats.getExecutionCount());
    }

    @Test
    @DisplayName("Should reset statistics to zero cleanly")
    void testReset() {
        NodeStats stats = new NodeStats("rule1", "node_1");
        stats.record(500L, false);

        stats.reset();
        assertEquals(0L, stats.getExecutionCount());
        assertEquals(0L, stats.getTotalDurationNanos());
        assertEquals(0L, stats.getFailureCount());
        assertEquals(0.0, stats.getAverageCostNanos());
    }
}
