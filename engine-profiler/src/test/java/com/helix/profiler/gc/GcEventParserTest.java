package com.helix.profiler.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcEventParserTest {

    @Test
    void testParseYoungGcLine() {
        GcEventParser parser = new GcEventParser();
        String line = "[0.123s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 15M->4M(256M) 2.456ms";

        GcEvent event = parser.parseLine(line);

        assertNotNull(event);
        assertEquals(123L, event.timestampMs());
        assertEquals(0, event.gcId());
        assertEquals("Pause Young (Normal)", event.type());
        assertEquals("G1 Evacuation Pause", event.cause());
        assertEquals(15 * 1024, event.heapBeforeKb());
        assertEquals(4 * 1024, event.heapAfterKb());
        assertEquals(256 * 1024, event.heapTotalKb());
        assertEquals(2.456, event.pauseTimeMs(), 0.001);
        assertFalse(event.isFullGc());
        assertEquals(11 * 1024, event.getReclaimedKb());
    }

    @Test
    void testParseFullGcLine() {
        GcEventParser parser = new GcEventParser();
        String line = "[1.456s][info][gc] GC(1) Pause Full (System.gc()) 45M->12M(256M) 15.340ms";

        GcEvent event = parser.parseLine(line);

        assertNotNull(event);
        assertEquals(1456L, event.timestampMs());
        assertEquals(1, event.gcId());
        assertEquals("Pause Full", event.type());
        assertEquals("System.gc()", event.cause());
        assertTrue(event.isFullGc());
        assertEquals(33 * 1024, event.getReclaimedKb());
    }

    @Test
    void testParseMemoryUnits() {
        assertEquals(1024, GcEventParser.parseMemoryToKb("1M"));
        assertEquals(2048, GcEventParser.parseMemoryToKb("2048K"));
        assertEquals(1048576, GcEventParser.parseMemoryToKb("1G"));
        assertEquals(1, GcEventParser.parseMemoryToKb("1024B"));
    }
}
