package com.helix.profiler.node;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe statistics tracker for an individual AST expression node.
 *
 * <p>Tracks total execution count, cumulative execution duration in nanoseconds,
 * and failure/short-circuit count using high-throughput striped {@link LongAdder} counters
 * to ensure zero lock contention in highly concurrent execution paths.</p>
 */
public class NodeStats {

    private final String ruleName;
    private final String nodeId;
    private final LongAdder executionCount = new LongAdder();
    private final LongAdder totalDurationNanos = new LongAdder();
    private final LongAdder failureCount = new LongAdder();

    public NodeStats(String ruleName, String nodeId) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
    }

    /**
     * Records a single node execution.
     *
     * @param elapsedNanos elapsed duration in nanoseconds
     * @param result       evaluation outcome (false represents failure/short-circuit)
     */
    public void record(long elapsedNanos, boolean result) {
        executionCount.increment();
        totalDurationNanos.add(elapsedNanos);
        if (!result) {
            failureCount.increment();
        }
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getNodeId() {
        return nodeId;
    }

    public long getExecutionCount() {
        return executionCount.sum();
    }

    public long getTotalDurationNanos() {
        return totalDurationNanos.sum();
    }

    public long getFailureCount() {
        return failureCount.sum();
    }

    /**
     * Calculates the mean execution cost in nanoseconds.
     *
     * @return average cost in nanoseconds, or 0.0 if never executed
     */
    public double getAverageCostNanos() {
        long count = executionCount.sum();
        return count == 0 ? 0.0 : (double) totalDurationNanos.sum() / count;
    }

    /**
     * Calculates the failure / short-circuit probability.
     *
     * @return failure rate in [0.0, 1.0], or 0.0 if never executed
     */
    public double getFailureRate() {
        long count = executionCount.sum();
        return count == 0 ? 0.0 : (double) failureCount.sum() / count;
    }

    /**
     * Calculates the cost-to-failure ratio: C_i / F_i.
     *
     * <p>Used by adaptive AST optimizers to sort boolean predicate chains such that
     * nodes with the lowest ratio (cheapest to fail) are evaluated first.</p>
     *
     * @return cost-to-failure ratio, or POSITIVE_INFINITY if failure rate is zero
     */
    public double getCostToFailureRatio() {
        double failureRate = getFailureRate();
        if (failureRate <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return getAverageCostNanos() / failureRate;
    }

    /**
     * Resets all internal counters to zero.
     */
    public void reset() {
        executionCount.reset();
        totalDurationNanos.reset();
        failureCount.reset();
    }

    /**
     * Creates an immutable point-in-time snapshot of this node's telemetry.
     *
     * @return immutable snapshot
     */
    public Snapshot snapshot() {
        long count = executionCount.sum();
        long totalDuration = totalDurationNanos.sum();
        long failures = failureCount.sum();
        double avgCost = count == 0 ? 0.0 : (double) totalDuration / count;
        double fRate = count == 0 ? 0.0 : (double) failures / count;
        double ratio = fRate <= 0.0 ? Double.POSITIVE_INFINITY : avgCost / fRate;

        return new Snapshot(ruleName, nodeId, count, totalDuration, failures, avgCost, fRate, ratio);
    }

    public record Snapshot(
            String ruleName,
            String nodeId,
            long executionCount,
            long totalDurationNanos,
            long failureCount,
            double avgCostNanos,
            double failureRate,
            double costToFailureRatio
    ) {}

    @Override
    public String toString() {
        return "NodeStats{" +
                "ruleName='" + ruleName + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", count=" + getExecutionCount() +
                ", avgCost=" + String.format("%.2f ns", getAverageCostNanos()) +
                ", failureRate=" + String.format("%.2f%%", getFailureRate() * 100) +
                ", ratio=" + String.format("%.2f", getCostToFailureRatio()) +
                '}';
    }
}
