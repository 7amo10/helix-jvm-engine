package com.helix.profiler.flamegraph;

/**
 * Metric dimension for flame graph stack trace aggregation.
 */
public enum MetricType {

    /**
     * CPU execution samples / execution time.
     */
    CPU_TIME("CPU Execution Samples", "samples"),

    /**
     * Heap memory allocation in bytes.
     */
    ALLOCATION_BYTES("Heap Allocation", "bytes");

    private final String displayName;
    private final String unit;

    MetricType(String displayName, String unit) {
        this.displayName = displayName;
        this.unit = unit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnit() {
        return unit;
    }

    @Override
    public String toString() {
        return displayName + " (" + unit + ")";
    }
}
