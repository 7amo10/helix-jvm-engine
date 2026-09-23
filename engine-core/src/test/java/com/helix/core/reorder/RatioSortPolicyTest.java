package com.helix.core.reorder;

import com.helix.profiler.node.NodeStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RatioSortPolicy Unit Tests")
class RatioSortPolicyTest {

    private final RatioSortPolicy policy = new RatioSortPolicy();

    @Test
    @DisplayName("Should sort candidate nodes ascending by cost-to-failure ratio")
    void testDetermineOrderAscendingRatio() {
        NodeStats node0 = new NodeStats("TestRule", "slow_clause");
        // avg cost 50,000 ns, 50% failure rate -> ratio 100,000
        node0.record(50_000, true);
        node0.record(50_000, false);

        NodeStats node1 = new NodeStats("TestRule", "fast_clause");
        // avg cost 100 ns, 50% failure rate -> ratio 200
        node1.record(100, true);
        node1.record(100, false);

        List<Integer> order = policy.determineOrder(List.of(node0, node1));
        assertEquals(List.of(1, 0), order, "Fast clause (lower ratio) must be placed before slow clause");
    }

    @Test
    @DisplayName("Should handle 3 candidate nodes with distinct ratios")
    void testDetermineOrderThreeNodes() {
        NodeStats n0 = new NodeStats("TestRule", "clause0");
        n0.record(1000, false); // 1000 / 1.0 = 1000

        NodeStats n1 = new NodeStats("TestRule", "clause1");
        n1.record(100, false);  // 100 / 1.0 = 100 (lowest)

        NodeStats n2 = new NodeStats("TestRule", "clause2");
        n2.record(5000, false); // 5000 / 1.0 = 5000 (highest)

        List<Integer> order = policy.determineOrder(List.of(n0, n1, n2));
        assertEquals(List.of(1, 0, 2), order);
    }

    @Test
    @DisplayName("Should handle empty and single-element lists")
    void testEdgeCases() {
        assertTrue(policy.determineOrder(List.of()).isEmpty());

        NodeStats single = new NodeStats("TestRule", "single");
        assertEquals(List.of(0), policy.determineOrder(List.of(single)));
    }

    @Test
    @DisplayName("Should apply optimistic prior when failure count is zero to prevent short-circuit blindness")
    void testZeroFailureCountHandling() {
        NodeStats cheapPassing = new NodeStats("TestRule", "cheap_clause");
        // 10 passing executions at 50 ns, 0 failures
        for (int i = 0; i < 10; i++) {
            cheapPassing.record(50, true);
        }

        NodeStats expensiveShortCircuit = new NodeStats("TestRule", "expensive_clause");
        // 10 executions at 200,000 ns, 9 failures (90%) -> ratio = 200,000 / 0.9 = 222,222
        for (int i = 0; i < 9; i++) expensiveShortCircuit.record(200_000, false);
        expensiveShortCircuit.record(200_000, true);

        List<Integer> order = policy.determineOrder(List.of(expensiveShortCircuit, cheapPassing));
        assertEquals(List.of(1, 0), order, "Cheap passing clause must precede expensive clause despite 0 observed failures");
    }
}
