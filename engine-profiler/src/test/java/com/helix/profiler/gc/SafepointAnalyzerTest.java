package com.helix.profiler.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafepointAnalyzerTest {

    @Test
    void testParseSafepointLine() {
        SafepointAnalyzer analyzer = new SafepointAnalyzer();
        String line = "[3.010s][info][safepoint] Safepoint \"G1CollectForAllocation\", Time since last: 1000200 ns, Reaching safepoint: 15000 ns, At safepoint: 2500000 ns, Total: 2515000 ns";

        SafepointAnalyzer.SafepointEvent event = analyzer.parseLine(line);

        assertNotNull(event);
        assertEquals(3010L, event.timestampMs());
        assertEquals("G1CollectForAllocation", event.name());
        assertEquals(1000200L, event.timeSinceLastNs());
        assertEquals(15000L, event.reachingSafepointNs());
        assertEquals(2500000L, event.atSafepointNs());
        assertEquals(2515000L, event.totalSafepointNs());
        assertEquals(2.515, event.getTotalSafepointMs(), 0.001);
        assertEquals(0.015, event.getReachingSafepointMs(), 0.001);
    }
}
