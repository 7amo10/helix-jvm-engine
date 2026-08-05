package com.helix.experiments.gc;

/**
 * Report summarizing GC stress experiment outcomes for Soft and Weak reference clearing.
 */
public record GcReport(
        String scenarioName,
        int initialObjectCount,
        int clearedCount,
        int retainedCount,
        double memoryAllocatedMb
) {

    @Override
    public String toString() {
        return String.format(
                "GcReport[%s] - Initial: %d | Cleared by GC: %d | Retained: %d | Memory Pressure: %.2f MB",
                scenarioName, initialObjectCount, clearedCount, retainedCount, memoryAllocatedMb
        );
    }
}
