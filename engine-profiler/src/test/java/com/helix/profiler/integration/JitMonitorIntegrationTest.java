package com.helix.profiler.integration;

import com.helix.profiler.jit.CompilationEvent;
import com.helix.profiler.jit.CompilationStats;
import com.helix.profiler.jit.JitCompilationMonitor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JitMonitorIntegrationTest {

    @Test
    void testJitMonitorCapturesCompilationEvents() {
        JitCompilationMonitor monitor = new JitCompilationMonitor();
        List<CompilationEvent> capturedEvents = new ArrayList<>();
        monitor.addListener(capturedEvents::add);

        String sampleLog = """
               50 1       3       com.helix.core.RuleCompiler::parse (120 bytes)
              120 2       4       com.helix.core.executor.SyncExecutor::execute (450 bytes)
              200 3 %     4       com.helix.core.RuleCompiler::parse @ 12 (120 bytes)
            """;

        List<CompilationEvent> events = monitor.parseLog(sampleLog);

        assertEquals(3, events.size());
        assertEquals("com.helix.core.RuleCompiler::parse", events.get(0).method());
        assertEquals(3, events.get(0).tier());

        assertEquals("com.helix.core.executor.SyncExecutor::execute", events.get(1).method());
        assertEquals(4, events.get(1).tier());

        assertTrue(events.get(2).isOsr());

        CompilationStats stats = monitor.getStats();
        assertEquals(3, stats.getTotalCompilations());
        assertEquals(1, stats.getOsrCount());
    }
}
