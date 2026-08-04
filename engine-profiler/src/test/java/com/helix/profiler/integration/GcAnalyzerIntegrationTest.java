package com.helix.profiler.integration;

import com.helix.profiler.gc.GcEvent;
import com.helix.profiler.gc.GcLogAnalyzer;
import com.helix.profiler.gc.GcStatistics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcAnalyzerIntegrationTest {

    @Test
    void testGcAnalyzerParsesGcLogs() {
        GcLogAnalyzer analyzer = new GcLogAnalyzer();
        List<GcEvent> capturedEvents = new ArrayList<>();
        analyzer.addListener(capturedEvents::add);

        String sampleGcLog = """
            [0.105s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 12M->4M(128M) 5.230ms
            [0.450s][info][gc] GC(1) Pause Full (System.gc()) 32M->8M(128M) 45.100ms
            """;

        List<GcEvent> events = analyzer.parseLog(sampleGcLog);

        assertEquals(2, events.size());

        GcEvent event1 = events.get(0);
        assertEquals(0, event1.gcId());
        assertEquals("Pause Young (Normal)", event1.type());
        assertEquals(5.230, event1.pauseTimeMs(), 0.001);

        GcEvent event2 = events.get(1);
        assertEquals(1, event2.gcId());
        assertEquals("Pause Full", event2.type());
        assertTrue(event2.isFullGc());

        GcStatistics stats = analyzer.getStatistics();
        assertEquals(2, stats.getTotalCollections());
        assertEquals(1, stats.getYoungGcCount());
        assertEquals(1, stats.getFullGcCount());
        assertEquals(50.330, stats.getTotalPauseTimeMs(), 0.01);
    }
}
