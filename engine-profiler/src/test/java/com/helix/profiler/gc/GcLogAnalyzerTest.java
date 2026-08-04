package com.helix.profiler.gc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcLogAnalyzerTest {

    private GcLogAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new GcLogAnalyzer();
    }

    @Test
    void testParseLogWithGcAndSafepoints() {
        String logContent = """
               [0.100s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 20M->5M(100M) 2.500ms
               [0.500s][info][safepoint] Safepoint "G1CollectForAllocation", Time since last: 1000200 ns, Reaching safepoint: 15000 ns, At safepoint: 2500000 ns, Total: 2515000 ns
               [1.200s][info][gc] GC(1) Pause Full (System.gc()) 50M->10M(100M) 20.000ms
            """;

        List<GcEvent> gcEvents = analyzer.parseLog(logContent);
        assertEquals(2, gcEvents.size());
        assertEquals(2, analyzer.getEvents().size());
        assertEquals(1, analyzer.getSafepointEvents().size());

        GcStatistics stats = analyzer.getStatistics();
        assertEquals(2, stats.getTotalCollections());
        assertEquals(1, stats.getFullGcCount());
        assertEquals(1, stats.getYoungGcCount());
        assertEquals(22.5, stats.getTotalPauseTimeMs(), 0.001);
        assertEquals(20.0, stats.getMaxPauseTimeMs(), 0.001);
        assertEquals(11.25, stats.getAvgPauseTimeMs(), 0.001);
    }

    @Test
    void testListenerNotification() {
        List<GcEvent> received = new ArrayList<>();
        analyzer.addListener(received::add);

        analyzer.parseLog("[0.100s][info][gc] GC(0) Pause Young (Normal) 10M->2M(100M) 1.000ms\n");

        assertEquals(1, received.size());
        assertEquals(0, received.get(0).gcId());
    }
}
