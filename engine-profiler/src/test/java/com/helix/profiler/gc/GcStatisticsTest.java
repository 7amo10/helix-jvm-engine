package com.helix.profiler.gc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcStatisticsTest {

    @Test
    void testStatisticsCalculation() {
        GcEvent e1 = new GcEvent(100L, 0, "Pause Young", "Normal", 10000, 2000, 50000, 5.0, false, null);
        GcEvent e2 = new GcEvent(200L, 1, "Pause Full", "System.gc()", 20000, 5000, 50000, 15.0, true, null);

        GcStatistics stats = new GcStatistics(List.of(e1, e2));

        assertEquals(2, stats.getTotalCollections());
        assertEquals(1, stats.getYoungGcCount());
        assertEquals(1, stats.getFullGcCount());
        assertEquals(20.0, stats.getTotalPauseTimeMs(), 0.001);
        assertEquals(15.0, stats.getMaxPauseTimeMs(), 0.001);
        assertEquals(10.0, stats.getAvgPauseTimeMs(), 0.001);
        assertEquals(23000, stats.getTotalReclaimedKb());

        // Uptime 1000ms, pause 20ms -> throughput 98%
        assertEquals(98.0, stats.getThroughputPercentage(1000), 0.001);
    }
}
