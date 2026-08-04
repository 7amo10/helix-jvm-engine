package com.helix.profiler.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricRegistryTest {

    private MetricRegistry registry;

    @BeforeEach
    void setUp() {
        registry = MetricRegistry.getInstance();
        registry.reset();
    }

    @Test
    void testRegistryAccessAndReset() {
        assertNotNull(registry.getEngineMetrics());

        registry.getEngineMetrics().incrementRulesCompiled();
        registry.getEngineMetrics().recordExecutionDuration(1500L);

        MetricsSnapshot snapshot = registry.getSnapshot();
        assertEquals(1, snapshot.rulesCompiled());

        registry.reset();
        MetricsSnapshot resetSnapshot = registry.getSnapshot();
        assertEquals(0, resetSnapshot.rulesCompiled());
    }
}
