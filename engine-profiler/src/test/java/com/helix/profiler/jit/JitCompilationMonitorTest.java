package com.helix.profiler.jit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JitCompilationMonitorTest {

    private JitCompilationMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new JitCompilationMonitor();
    }

    @Test
    void testParseLineStandardTier3() {
        String line = "   123  1234       3       com.helix.core.RuleCompiler::compile (45 bytes)";
        CompilationEvent event = monitor.parseLine(line);

        assertNotNull(event);
        assertEquals(123L, event.timestampMs());
        assertEquals(1234, event.compileId());
        assertEquals(3, event.tier());
        assertEquals("com.helix.core.RuleCompiler::compile", event.method());
        assertEquals(45, event.bytecodeSize());
        assertFalse(event.isOsr());
        assertFalse(event.isSynchronized());
        assertFalse(event.isExceptionHolder());
        assertEquals("NORMAL", event.status());
    }

    @Test
    void testParseLineOsrAndFlags() {
        String line = "   180  1236    % ! s 4       com.helix.core.RuleCompiler::loop @ 12 (200 bytes)";
        CompilationEvent event = monitor.parseLine(line);

        assertNotNull(event);
        assertEquals(180L, event.timestampMs());
        assertEquals(1236, event.compileId());
        assertEquals(4, event.tier());
        assertEquals("com.helix.core.RuleCompiler::loop @ 12", event.method());
        assertEquals(200, event.bytecodeSize());
        assertTrue(event.isOsr());
        assertTrue(event.isSynchronized());
        assertTrue(event.isExceptionHolder());
    }

    @Test
    void testParseLineDeoptimization() {
        String line = "   200  1234       4       com.helix.core.RuleCompiler::compile (45 bytes)   made not entrant";
        CompilationEvent event = monitor.parseLine(line);

        assertNotNull(event);
        assertEquals(200L, event.timestampMs());
        assertEquals(4, event.tier());
        assertEquals("made not entrant", event.status());
        assertTrue(event.isDeoptimization());
    }

    @Test
    void testParseLogMultipleLines() {
        String sampleLog = """
               100  1001       3       com.helix.core.Parser::parse (50 bytes)
               150  1002       4       com.helix.core.Parser::parse (50 bytes)
               200  1003    %  3       com.helix.core.Executor::executeLoop @ 5 (120 bytes)
               250  1001       3       com.helix.core.Parser::parse (50 bytes)   made not entrant
            """;

        List<CompilationEvent> events = monitor.parseLog(sampleLog);
        assertEquals(4, events.size());
        assertEquals(4, monitor.getHistory().size());

        CompilationStats stats = monitor.getStats();
        assertEquals(4, stats.getTotalCompilations());
        assertEquals(3, stats.getCountForTier(3));
        assertEquals(1, stats.getCountForTier(4));
        assertEquals(1, stats.getOsrCount());
        assertEquals(1, stats.getDeoptimizationCount());
    }

    @Test
    void testEventListener() {
        List<CompilationEvent> received = new ArrayList<>();
        monitor.addListener(received::add);

        monitor.parseLog("   100  1001       3       com.helix.core.Rule::eval (10 bytes)\n");

        assertEquals(1, received.size());
        assertEquals("com.helix.core.Rule::eval", received.get(0).method());
    }
}
