package com.helix.core.executor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Extended metrics tracking execution counts, timing, active virtual thread tasks, and timeouts.
 */
public class VirtualThreadExecutorMetrics extends ExecutorMetrics {

    private final AtomicLong timedOutExecutions = new AtomicLong(0);
    private final AtomicLong activeTasks = new AtomicLong(0);

    /**
     * Records an execution that failed due to a deadline timeout.
     *
     * @param durationNanos elapsed time before timeout
     */
    public void recordTimeout(long durationNanos) {
        recordExecution(false, durationNanos);
        timedOutExecutions.incrementAndGet();
    }

    /**
     * Increments the count of currently executing tasks.
     */
    public void taskStarted() {
        activeTasks.incrementAndGet();
    }

    /**
     * Decrements the count of currently executing tasks.
     */
    public void taskFinished() {
        activeTasks.decrementAndGet();
    }

    /**
     * Returns the total count of executions that exceeded their deadline timeout.
     *
     * @return timeout count
     */
    public long getTimedOutExecutions() {
        return timedOutExecutions.get();
    }

    /**
     * Returns the number of currently active virtual-thread tasks.
     *
     * @return active task count
     */
    public long getActiveTasks() {
        return Math.max(0, activeTasks.get());
    }
}
