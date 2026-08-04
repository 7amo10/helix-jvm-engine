package com.helix.profiler.metrics;

import java.util.Objects;

/**
 * Registry holding global operational metrics for the Helix engine.
 */
public class MetricRegistry {

    private static final MetricRegistry INSTANCE = new MetricRegistry();

    private final EngineMetrics engineMetrics;

    public MetricRegistry() {
        this.engineMetrics = new EngineMetrics();
    }

    public static MetricRegistry getInstance() {
        return INSTANCE;
    }

    public EngineMetrics getEngineMetrics() {
        return engineMetrics;
    }

    public MetricsSnapshot getSnapshot() {
        return engineMetrics.getSnapshot();
    }

    public void reset() {
        engineMetrics.reset();
    }
}
