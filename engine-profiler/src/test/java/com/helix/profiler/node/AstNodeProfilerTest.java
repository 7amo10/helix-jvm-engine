package com.helix.profiler.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AstNodeProfiler Unit, Concurrency, and Overhead Tests")
class AstNodeProfilerTest {

    @BeforeEach
    void setUp() {
        AstNodeProfiler.reset();
        AstNodeProfiler.setEnabled(true);
    }

    @Test
    @DisplayName("Should register and track node statistics via record")
    void testRecordDirectly() {
        AstNodeProfiler.record("OrderRule", "node_amount_gt_100", 45L, true);
        AstNodeProfiler.record("OrderRule", "node_amount_gt_100", 55L, false);

        NodeStats stats = AstNodeProfiler.getNodeStats("node_amount_gt_100").orElse(null);
        assertNotNull(stats);
        assertEquals("OrderRule", stats.getRuleName());
        assertEquals("node_amount_gt_100", stats.getNodeId());
        assertEquals(2L, stats.getExecutionCount());
        assertEquals(100L, stats.getTotalDurationNanos());
        assertEquals(1L, stats.getFailureCount());
        assertEquals(50.0, stats.getAverageCostNanos());
        assertEquals(0.5, stats.getFailureRate());
    }

    @Test
    @DisplayName("Should track node execution via entry and exit hooks")
    void testEntryAndExitHooks() throws InterruptedException {
        String nodeId = "clause_ml_fraud";
        AstNodeProfiler.registerNode("FraudRule", nodeId);

        AstNodeProfiler.recordEntry(nodeId);
        Thread.sleep(2); // Simulate 2ms execution
        AstNodeProfiler.recordExit(nodeId, false);

        NodeStats stats = AstNodeProfiler.getNodeStats(nodeId).orElse(null);
        assertNotNull(stats);
        assertEquals(1L, stats.getExecutionCount());
        assertEquals(1L, stats.getFailureCount());
        assertTrue(stats.getTotalDurationNanos() >= 1_500_000L, "Elapsed duration should be >= 1.5ms");
        assertEquals(1.0, stats.getFailureRate());
    }

    @Test
    @DisplayName("Should handle nested AST node execution with thread-local timing stack")
    void testNestedNodeExecution() {
        String parentNode = "binary_and_root";
        String leftChild = "clause_left";
        String rightChild = "clause_right";

        AstNodeProfiler.registerNode("RuleNested", parentNode);
        AstNodeProfiler.registerNode("RuleNested", leftChild);
        AstNodeProfiler.registerNode("RuleNested", rightChild);

        // Simulate evaluation of (leftChild && rightChild)
        AstNodeProfiler.recordEntry(parentNode);

        AstNodeProfiler.recordEntry(leftChild);
        // leftChild passes
        AstNodeProfiler.recordExit(leftChild, true);

        AstNodeProfiler.recordEntry(rightChild);
        // rightChild fails
        AstNodeProfiler.recordExit(rightChild, false);

        // parent evaluates to false
        AstNodeProfiler.recordExit(parentNode, false);

        NodeStats parentStats = AstNodeProfiler.getNodeStats(parentNode).orElseThrow();
        NodeStats leftStats = AstNodeProfiler.getNodeStats(leftChild).orElseThrow();
        NodeStats rightStats = AstNodeProfiler.getNodeStats(rightChild).orElseThrow();

        assertEquals(1L, parentStats.getExecutionCount());
        assertEquals(1L, parentStats.getFailureCount());

        assertEquals(1L, leftStats.getExecutionCount());
        assertEquals(0L, leftStats.getFailureCount());

        assertEquals(1L, rightStats.getExecutionCount());
        assertEquals(1L, rightStats.getFailureCount());
    }

    @Test
    @DisplayName("Should support massive multi-threaded concurrent recordings without contention")
    void testHighThroughputConcurrency() throws InterruptedException {
        int threads = 32;
        int iterations = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final String nodeId = "node_" + (i % 4); // 4 shared nodes
            final boolean pass = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        AstNodeProfiler.record("ConcurrentRule", nodeId, 10L, pass);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Map<String, NodeStats> statsMap = AstNodeProfiler.getStatsForRule("ConcurrentRule");
        assertEquals(4, statsMap.size());

        long totalRecorded = statsMap.values().stream().mapToLong(NodeStats::getExecutionCount).sum();
        assertEquals((long) threads * iterations, totalRecorded);
    }

    @Test
    @DisplayName("Profiler hook check overhead must remain under 5 ns per check")
    void testProfilerHookCheckOverheadSla() {
        // Warm up JIT
        for (int i = 0; i < 500_000; i++) {
            boolean active = AstNodeProfiler.isEnabled();
        }

        int iterations = 10_000_000;
        long startNanos = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            boolean active = AstNodeProfiler.isEnabled();
        }
        long totalNanos = System.nanoTime() - startNanos;
        double nsPerCheck = (double) totalNanos / iterations;

        System.out.printf("AstNodeProfiler Hook Check Overhead: %.2f ns/op (SLA: < 5.0 ns)%n", nsPerCheck);
        assertTrue(nsPerCheck < 5.0, "Profiler hook check overhead must be < 5 ns, was: " + nsPerCheck + " ns");
    }

    @Test
    @DisplayName("Should bypass recording when disabled")
    void testDisabledProfiler() {
        AstNodeProfiler.setEnabled(false);
        AstNodeProfiler.record("RuleA", "node_disabled", 100L, true);

        assertTrue(AstNodeProfiler.getNodeStats("node_disabled").isEmpty());
    }
}
