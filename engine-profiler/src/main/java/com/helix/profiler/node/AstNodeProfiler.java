package com.helix.profiler.node;

import com.helix.profiler.jfr.HelixNodeProfileEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, thread-safe AST node profiler capturing per-node execution telemetry.
 *
 * <p>Emits ultra-low-overhead runtime hooks (&lt; 5 ns) invoked directly from compiled bytecode
 * to track execution frequency, execution latencies, failure/short-circuit rates,
 * and cost-to-failure ratios used by adaptive AST optimizers and JFR telemetry.</p>
 */
public final class AstNodeProfiler {

    private static final Logger log = LoggerFactory.getLogger(AstNodeProfiler.class);

    private static final ConcurrentHashMap<String, NodeStats> registry = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> nodeRuleMap = new ConcurrentHashMap<>();
    private static volatile boolean enabled = true;

    // Fast, bounded thread-local stack for nested entry/exit timestamps
    private static final ThreadLocal<FastTimestampStack> threadLocalTimestamps =
            ThreadLocal.withInitial(FastTimestampStack::new);

    private AstNodeProfiler() {
        // Utility class
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean isEnabled) {
        enabled = isEnabled;
    }

    /**
     * Pre-registers a node for a given rule.
     *
     * @param ruleName associated rule name
     * @param nodeId   unique node identifier
     * @return initialized NodeStats
     */
    public static NodeStats registerNode(String ruleName, String nodeId) {
        if (ruleName != null && nodeId != null) {
            nodeRuleMap.putIfAbsent(nodeId, ruleName);
        }
        return registry.computeIfAbsent(nodeId, k -> new NodeStats(ruleName != null ? ruleName : "unknown", k));
    }

    /**
     * Direct telemetry recording helper.
     *
     * @param ruleName     rule identifier
     * @param nodeId       node identifier
     * @param elapsedNanos elapsed duration in nanoseconds
     * @param result       evaluation outcome (false = short-circuit / failure)
     */
    public static void record(String ruleName, String nodeId, long elapsedNanos, boolean result) {
        if (!enabled) return;

        NodeStats stats = registry.get(nodeId);
        if (stats == null) {
            stats = registerNode(ruleName, nodeId);
        }
        stats.record(elapsedNanos, result);
    }

    /**
     * Entry hook invoked directly from compiled bytecode prior to node evaluation.
     *
     * @param nodeId unique node identifier
     */
    public static void recordEntry(String nodeId) {
        if (!enabled) return;
        threadLocalTimestamps.get().push(System.nanoTime());
    }

    /**
     * Exit hook invoked directly from compiled bytecode immediately following node evaluation.
     *
     * @param nodeId unique node identifier
     * @param result evaluation outcome
     */
    public static void recordExit(String nodeId, boolean result) {
        if (!enabled) return;
        long startNanos = threadLocalTimestamps.get().pop();
        long elapsedNanos = Math.max(0, System.nanoTime() - startNanos);

        NodeStats stats = registry.get(nodeId);
        if (stats == null) {
            String ruleName = nodeRuleMap.getOrDefault(nodeId, "unknown");
            stats = registerNode(ruleName, nodeId);
        }
        stats.record(elapsedNanos, result);
    }

    /**
     * Exit hook with explicit start timestamp.
     */
    public static void recordExit(String nodeId, boolean result, long startNanos) {
        if (!enabled) return;
        long elapsedNanos = Math.max(0, System.nanoTime() - startNanos);
        NodeStats stats = registry.get(nodeId);
        if (stats == null) {
            String ruleName = nodeRuleMap.getOrDefault(nodeId, "unknown");
            stats = registerNode(ruleName, nodeId);
        }
        stats.record(elapsedNanos, result);
    }

    public static Optional<NodeStats> getNodeStats(String nodeId) {
        return Optional.ofNullable(registry.get(nodeId));
    }

    public static Map<String, NodeStats> getAllStats() {
        return Collections.unmodifiableMap(registry);
    }

    public static Map<String, NodeStats> getStatsForRule(String ruleName) {
        Map<String, NodeStats> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, NodeStats> entry : registry.entrySet()) {
            if (ruleName.equals(entry.getValue().getRuleName())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Emits a custom JFR HelixNodeProfileEvent for the specified node.
     *
     * @param ruleName rule identifier
     * @param nodeId   node identifier
     */
    public static void emitJfrEvent(String ruleName, String nodeId) {
        NodeStats stats = registry.get(nodeId);
        if (stats != null) {
            try {
                HelixNodeProfileEvent event = new HelixNodeProfileEvent();
                if (event.isEnabled()) {
                    event.ruleName = stats.getRuleName();
                    event.nodeId = stats.getNodeId();
                    event.executionCount = stats.getExecutionCount();
                    event.avgCostNanos = stats.getAverageCostNanos();
                    event.failureRate = stats.getFailureRate();
                    event.ratio = stats.getCostToFailureRatio();
                    event.commit();
                }
            } catch (Exception e) {
                log.debug("Failed to commit HelixNodeProfileEvent: {}", e.getMessage());
            }
        }
    }

    /**
     * Emits custom JFR HelixNodeProfileEvents for all currently recorded nodes.
     */
    public static void emitAllJfrEvents() {
        for (NodeStats stats : registry.values()) {
            emitJfrEvent(stats.getRuleName(), stats.getNodeId());
        }
    }

    /**
     * Clears all telemetry statistics and node mappings.
     */
    public static void reset() {
        registry.clear();
        nodeRuleMap.clear();
        threadLocalTimestamps.get().reset();
    }

    /**
     * Lightweight pre-allocated timestamp stack to support nested AST evaluations without heap allocations.
     */
    static final class FastTimestampStack {
        private final long[] stack = new long[32];
        private int pointer = 0;

        void push(long time) {
            if (pointer < stack.length) {
                stack[pointer++] = time;
            }
        }

        long pop() {
            if (pointer > 0) {
                return stack[--pointer];
            }
            return System.nanoTime();
        }

        void reset() {
            pointer = 0;
        }
    }
}
