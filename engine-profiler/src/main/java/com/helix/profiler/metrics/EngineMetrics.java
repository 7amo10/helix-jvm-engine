package com.helix.profiler.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe collector for core engine operational metrics (counters, gauges, and timers).
 */
public class EngineMetrics {

    private final AtomicLong rulesCompiled = new AtomicLong(0);
    private final AtomicLong rulesExecuted = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong activeClassLoaders = new AtomicLong(1);
    private final AtomicLong metaspaceUsedBytes = new AtomicLong(0);

    private final List<Long> executionDurationsNanos = new CopyOnWriteArrayList<>();
    private final int maxTimerSamples;

    public EngineMetrics() {
        this(5000);
    }

    public EngineMetrics(int maxTimerSamples) {
        this.maxTimerSamples = maxTimerSamples;
    }

    public void incrementRulesCompiled() {
        rulesCompiled.incrementAndGet();
    }

    public void incrementRulesExecuted() {
        rulesExecuted.incrementAndGet();
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void setActiveClassLoaders(long count) {
        activeClassLoaders.set(Math.max(0, count));
    }

    public void setMetaspaceUsedBytes(long bytes) {
        metaspaceUsedBytes.set(Math.max(0, bytes));
    }

    public void recordExecutionDuration(long durationNanos) {
        if (executionDurationsNanos.size() >= maxTimerSamples) {
            executionDurationsNanos.remove(0);
        }
        executionDurationsNanos.add(durationNanos);
    }

    public MetricsSnapshot getSnapshot() {
        List<Long> durations = new ArrayList<>(executionDurationsNanos);
        Collections.sort(durations);

        double p50 = getPercentile(durations, 50.0);
        double p95 = getPercentile(durations, 95.0);
        double p99 = getPercentile(durations, 99.0);

        return new MetricsSnapshot(
                rulesCompiled.get(),
                rulesExecuted.get(),
                cacheHits.get(),
                cacheMisses.get(),
                activeClassLoaders.get(),
                metaspaceUsedBytes.get(),
                p50,
                p95,
                p99,
                Instant.now()
        );
    }

    private static double getPercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0.0;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        double rank = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIdx = (int) Math.floor(rank);
        int upperIdx = (int) Math.ceil(rank);
        if (lowerIdx == upperIdx) {
            return sortedValues.get(lowerIdx);
        }
        double weight = rank - lowerIdx;
        return sortedValues.get(lowerIdx) + weight * (sortedValues.get(upperIdx) - sortedValues.get(lowerIdx));
    }

    public void reset() {
        rulesCompiled.set(0);
        rulesExecuted.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        activeClassLoaders.set(1);
        metaspaceUsedBytes.set(0);
        executionDurationsNanos.clear();
    }
}
