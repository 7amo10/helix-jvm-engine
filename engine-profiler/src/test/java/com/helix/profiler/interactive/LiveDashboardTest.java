package com.helix.profiler.interactive;

import com.helix.profiler.gc.GcLogAnalyzer;
import com.helix.profiler.jit.JitCompilationMonitor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class LiveDashboardTest {

    @Test
    void testLiveDashboardLifecycle() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(out);

        JitCompilationMonitor jitMonitor = new JitCompilationMonitor();
        GcLogAnalyzer gcAnalyzer = new GcLogAnalyzer();

        LiveDashboard dashboard = new LiveDashboard(jitMonitor, gcAnalyzer, printStream);
        assertFalse(dashboard.isRunning());

        dashboard.setActiveClassLoaders(3);
        dashboard.setCachedRulesCount(10);

        dashboard.renderOnce();

        String output = out.toString();
        assertTrue(output.contains("HELIX REAL-TIME JVM ENGINE & PROFILER DASHBOARD"));
        assertTrue(output.contains("Active ClassLoaders : 3"));
        assertTrue(output.contains("Active Cached Rules : 10"));

        dashboard.start(100);
        assertTrue(dashboard.isRunning());

        Thread.sleep(250);

        dashboard.stop();
        assertFalse(dashboard.isRunning());
    }
}
