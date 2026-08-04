package com.helix.profiler.metrics;

import java.time.Instant;

/**
 * Immutable snapshot of engine performance metrics at a specific point in time.
 */
public record MetricsSnapshot(
        long rulesCompiled,
        long rulesExecuted,
        long cacheHits,
        long cacheMisses,
        long activeClassLoaders,
        long metaspaceUsedBytes,
        double p50DurationNanos,
        double p95DurationNanos,
        double p99DurationNanos,
        Instant timestamp
) {

    public double getCacheHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0 ? 0.0 : (double) cacheHits / total;
    }
}
