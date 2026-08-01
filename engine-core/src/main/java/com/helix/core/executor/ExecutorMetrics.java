package com.helix.core.executor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for tracking rule execution performance and counts.
 */
public class ExecutorMetrics {

    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    private final AtomicLong totalExecutionTimeNanos = new AtomicLong(0);

    public void recordExecution(boolean success, long durationNanos) {
        totalExecutions.incrementAndGet();
        if (success) {
            successfulExecutions.incrementAndGet();
        } else {
            failedExecutions.incrementAndGet();
        }
        totalExecutionTimeNanos.addAndGet(durationNanos);
    }

    public long getTotalExecutions() {
        return totalExecutions.get();
    }

    public long getSuccessfulExecutions() {
        return successfulExecutions.get();
    }

    public long getFailedExecutions() {
        return failedExecutions.get();
    }

    public long getTotalExecutionTimeNanos() {
        return totalExecutionTimeNanos.get();
    }

    public double getAverageExecutionTimeNanos() {
        long total = totalExecutions.get();
        return total == 0 ? 0.0 : (double) totalExecutionTimeNanos.get() / total;
    }
}
