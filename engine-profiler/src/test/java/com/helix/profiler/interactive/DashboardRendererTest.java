package com.helix.profiler.interactive;

import com.helix.profiler.gc.GcEvent;
import com.helix.profiler.gc.GcStatistics;
import com.helix.profiler.jit.CompilationEvent;
import com.helix.profiler.jit.CompilationStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DashboardRendererTest {

    @Test
    void testRenderDashboardOutput() {
        DashboardRenderer renderer = new DashboardRenderer();

        CompilationEvent c1 = new CompilationEvent(100L, 1, 3, "com.helix.Rule::eval", 50);
        CompilationEvent c2 = new CompilationEvent(200L, 2, 4, "com.helix.Rule::eval", 50);
        CompilationStats jitStats = new CompilationStats(List.of(c1, c2));

        GcEvent g1 = new GcEvent(100L, 0, "Pause Young", "Normal", 10000, 2000, 50000, 4.5, false, null);
        GcStatistics gcStats = new GcStatistics(List.of(g1));

        String output = renderer.renderDashboard(jitStats, gcStats, 5, 12);

        assertNotNull(output);
        assertTrue(output.contains("HELIX REAL-TIME JVM ENGINE & PROFILER DASHBOARD"));
        assertTrue(output.contains("MEMORY & METASPACE"));
        assertTrue(output.contains("JIT COMPILATION MONITOR"));
        assertTrue(output.contains("GC & SAFEPOINT ANALYSIS"));
        assertTrue(output.contains("TIERED CACHE"));
        assertTrue(output.contains("Active ClassLoaders : 5"));
        assertTrue(output.contains("Active Cached Rules : 12"));
    }
}
