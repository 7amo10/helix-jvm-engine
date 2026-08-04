package com.helix.profiler.metrics;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MetricsSnapshotTest {

    @Test
    void testCacheHitRate() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                10, 100, 80, 20, 2, 102400, 100.0, 500.0, 1000.0, Instant.now()
        );

        assertEquals(10, snapshot.rulesCompiled());
        assertEquals(100, snapshot.rulesExecuted());
        assertEquals(0.8, snapshot.getCacheHitRate(), 0.001);
    }
}
