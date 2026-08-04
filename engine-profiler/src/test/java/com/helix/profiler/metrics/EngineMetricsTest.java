package com.helix.profiler.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineMetricsTest {

    private EngineMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new EngineMetrics();
    }

    @Test
    void testCountersAndGauges() {
        metrics.incrementRulesCompiled();
        metrics.incrementRulesCompiled();

        metrics.incrementRulesExecuted();
        metrics.recordCacheHit();
        metrics.recordCacheHit();
        metrics.recordCacheMiss();

        metrics.setActiveClassLoaders(4);
        metrics.setMetaspaceUsedBytes(50 * 1024 * 1024);

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(2, snapshot.rulesCompiled());
        assertEquals(1, snapshot.rulesExecuted());
        assertEquals(2, snapshot.cacheHits());
        assertEquals(1, snapshot.cacheMisses());
        assertEquals(0.666, snapshot.getCacheHitRate(), 0.01);
        assertEquals(4, snapshot.activeClassLoaders());
        assertEquals(50 * 1024 * 1024, snapshot.metaspaceUsedBytes());
    }

    @Test
    void testPercentileCalculations() {
        for (int i = 1; i <= 100; i++) {
            metrics.recordExecutionDuration(i * 1000L);
        }

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertTrue(snapshot.p50DurationNanos() > 40000 && snapshot.p50DurationNanos() < 60000);
        assertTrue(snapshot.p95DurationNanos() > 90000);
        assertTrue(snapshot.p99DurationNanos() > 95000);
    }
}
