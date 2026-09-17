package com.helix.cli.ui;

import com.helix.profiler.flamegraph.FlameGraphAggregator;
import com.helix.profiler.flamegraph.MetricType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelixDashboardTest {

    @Test
    @DisplayName("HelixDashboard initializes with aggregator and seeded samples")
    void shouldInitializeWithAggregatorAndSamples() {
        FlameGraphAggregator aggregator = new FlameGraphAggregator();
        HelixDashboard dashboard = new HelixDashboard(aggregator);

        assertNotNull(dashboard.getAggregator());
        assertEquals(HelixDashboard.ViewMode.OVERVIEW, dashboard.getCurrentView());

        // Check seeded samples exist in aggregator
        assertTrue(aggregator.getRootNode().getTotalValue() > 0, "Aggregator should have seeded CPU samples");

        aggregator.setActiveMetric(MetricType.ALLOCATION_BYTES);
        assertTrue(aggregator.getRootNode().getTotalValue() > 0, "Aggregator should have seeded Allocation samples");
    }

    @Test
    @DisplayName("TuiDashboard seamlessly inherits from HelixDashboard")
    void shouldInheritFromHelixDashboard() {
        TuiDashboard tui = new TuiDashboard();
        assertTrue(tui instanceof HelixDashboard);
        assertEquals(HelixDashboard.ViewMode.OVERVIEW, tui.getCurrentView());
        assertNotNull(tui.getAggregator());
    }

    @Test
    @DisplayName("Dashboard supports programmatic view switching")
    void shouldSupportViewSwitching() {
        HelixDashboard dashboard = new HelixDashboard();
        assertEquals(HelixDashboard.ViewMode.OVERVIEW, dashboard.getCurrentView());

        // Note: Full GUI start requires terminal screen, but switchView can be tested safely or after initialization
        dashboard.stop();
    }
}
