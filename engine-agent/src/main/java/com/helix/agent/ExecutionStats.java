package com.helix.agent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-thread execution metrics tracking method entry times, execution counts, and duration totals.
 */
public class ExecutionStats {

    private final Deque<Long> entryTimeStack = new ArrayDeque<>();
    private long totalExecutions = 0;
    private long totalDurationNanos = 0;
    private long totalAllocations = 0;

    public void pushMethodEntry() {
        entryTimeStack.push(System.nanoTime());
        totalExecutions++;
    }

    public void popMethodExit() {
        if (!entryTimeStack.isEmpty()) {
            long startTime = entryTimeStack.pop();
            totalDurationNanos += (System.nanoTime() - startTime);
        }
    }

    public void recordAllocation() {
        totalAllocations++;
    }

    public long getTotalExecutions() {
        return totalExecutions;
    }

    public long getTotalDurationNanos() {
        return totalDurationNanos;
    }

    public long getTotalAllocations() {
        return totalAllocations;
    }

    public void reset() {
        entryTimeStack.clear();
        totalExecutions = 0;
        totalDurationNanos = 0;
        totalAllocations = 0;
    }
}
