package com.helix.cli.ui;

import com.helix.profiler.flamegraph.FlameGraphAggregator;

/**
 * Backwards-compatible alias extending {@link HelixDashboard}.
 * Provides the interactive Lanterna TUI dashboard with live HotSpot JVM metrics and in-terminal flame graphs.
 */
public class TuiDashboard extends HelixDashboard {

    public TuiDashboard() {
        super();
    }

    public TuiDashboard(FlameGraphAggregator aggregator) {
        super(aggregator);
    }
}
