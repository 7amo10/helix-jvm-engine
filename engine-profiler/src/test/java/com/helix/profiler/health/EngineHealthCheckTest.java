package com.helix.profiler.health;

import com.helix.profiler.gc.GcEvent;
import com.helix.profiler.gc.GcStatistics;
import com.helix.profiler.metrics.MetricsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineHealthCheckTest {

    private EngineHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        healthCheck = new EngineHealthCheck(50, 0.40, 100.0);
    }

    @Test
    void testHealthySystem() {
        MetricsSnapshot metrics = new MetricsSnapshot(
                10, 100, 80, 20, 5, 500000, 10.0, 20.0, 30.0, Instant.now()
        );
        GcEvent event = new GcEvent(100L, 0, "Pause Young", "Normal", 10000, 2000, 50000, 10.0, false, null);
        GcStatistics gcStats = new GcStatistics(List.of(event));

        HealthStatus status = healthCheck.checkHealth(metrics, gcStats);

        assertTrue(status.isHealthy());
        assertEquals(Severity.INFO, status.overallSeverity());
        assertTrue(status.issues().isEmpty());
    }

    @Test
    void testUnhealthyClassLoaderLeak() {
        MetricsSnapshot metrics = new MetricsSnapshot(
                500, 1000, 80, 20, 120, 500000, 10.0, 20.0, 30.0, Instant.now()
        );

        HealthStatus status = healthCheck.checkHealth(metrics, null);

        assertFalse(status.isHealthy());
        assertEquals(Severity.CRITICAL, status.overallSeverity());
        assertFalse(status.issues().isEmpty());
        assertTrue(status.issues().stream().anyMatch(i -> i.component().equals("ClassLoaderManager")));
    }

    @Test
    void testUnhealthyHighGcPause() {
        MetricsSnapshot metrics = new MetricsSnapshot(
                10, 100, 80, 20, 5, 500000, 10.0, 20.0, 30.0, Instant.now()
        );
        GcEvent event = new GcEvent(100L, 0, "Pause Full", "System.gc()", 100000, 20000, 500000, 600.0, true, null);
        GcStatistics gcStats = new GcStatistics(List.of(event));

        HealthStatus status = healthCheck.checkHealth(metrics, gcStats);

        assertFalse(status.isHealthy());
        assertEquals(Severity.CRITICAL, status.overallSeverity());
        assertTrue(status.issues().stream().anyMatch(i -> i.component().equals("GarbageCollector")));
    }
}
