package com.helix.profiler;

import com.helix.profiler.async.AsyncProfilerIntegration;
import com.helix.profiler.async.ProfileMode;
import com.helix.profiler.gc.GcEvent;
import com.helix.profiler.gc.GcLogAnalyzer;
import com.helix.profiler.gc.GcStatistics;
import com.helix.profiler.health.EngineHealthCheck;
import com.helix.profiler.health.HealthStatus;
import com.helix.profiler.interactive.LiveDashboard;
import com.helix.profiler.jfr.JfrEventRecorder;
import com.helix.profiler.jfr.JfrRecordingManager;
import com.helix.profiler.jit.CompilationEvent;
import com.helix.profiler.jit.CompilationStats;
import com.helix.profiler.jit.JitCompilationMonitor;
import com.helix.profiler.metrics.EngineMetrics;
import com.helix.profiler.metrics.MetricsSnapshot;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Sprint5VerificationTest {

    @Test
    void testFullSprint5Capabilities(@TempDir Path tempDir) throws Exception {
        // 1. JIT Compilation Monitor
        JitCompilationMonitor jitMonitor = new JitCompilationMonitor();
        String jitLog = """
               100 1       3       com.helix.RuleEngine::eval (50 bytes)
               150 2       4       com.helix.RuleEngine::eval (50 bytes)
            """;
        List<CompilationEvent> compilationEvents = jitMonitor.parseLog(jitLog);
        assertEquals(2, compilationEvents.size());
        CompilationStats jitStats = jitMonitor.getStats();
        assertEquals(2, jitStats.getTotalCompilations());

        // 2. GC Log Analyzer
        GcLogAnalyzer gcAnalyzer = new GcLogAnalyzer();
        String gcLog = "[0.100s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 16M->4M(128M) 8.500ms";
        List<GcEvent> gcEvents = gcAnalyzer.parseLog(gcLog);
        assertEquals(1, gcEvents.size());
        GcStatistics gcStats = gcAnalyzer.getStatistics();
        assertEquals(1, gcStats.getTotalCollections());

        // 3. async-profiler & HTML Flame Graph
        AsyncProfilerIntegration profiler = new AsyncProfilerIntegration();
        profiler.start(ProfileMode.CPU);
        profiler.recordSample("com.helix.RuleEngine;eval", 100);
        Path htmlFile = tempDir.resolve("verification-flamegraph.html");
        profiler.dumpHtml(htmlFile, "Verification Flame Graph");
        assertTrue(Files.exists(htmlFile));
        assertTrue(Files.readString(htmlFile).contains("Verification Flame Graph"));

        // 4. Custom JFR Events
        JfrRecordingManager jfrManager = new JfrRecordingManager();
        jfrManager.startRecording("Sprint5VerificationRecording");
        JfrEventRecorder jfrRecorder = new JfrEventRecorder();
        jfrRecorder.recordExecution("Sprint5Rule", "1.0", true, 2000000L, null);
        jfrManager.stopRecording();

        Path jfrFile = tempDir.resolve("verification-recording.jfr");
        jfrManager.dumpRecording(jfrFile);
        assertTrue(Files.exists(jfrFile));
        List<RecordedEvent> recordedEvents = RecordingFile.readAllEvents(jfrFile);
        assertTrue(recordedEvents.stream().anyMatch(e -> e.getEventType().getName().equals("com.helix.RuleExecution")));
        jfrManager.close();

        // 5. Engine Metrics & Live Dashboard
        EngineMetrics metrics = new EngineMetrics();
        metrics.incrementRulesCompiled();
        metrics.incrementRulesExecuted();
        metrics.recordExecutionDuration(1500000L);
        MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(1, snapshot.rulesCompiled());

        ByteArrayOutputStream dashboardOut = new ByteArrayOutputStream();
        LiveDashboard dashboard = new LiveDashboard(jitMonitor, gcAnalyzer, new PrintStream(dashboardOut));
        dashboard.renderOnce();
        assertTrue(dashboardOut.toString().contains("HELIX REAL-TIME JVM ENGINE & PROFILER DASHBOARD"));

        // 6. Engine Health Check
        EngineHealthCheck healthCheck = new EngineHealthCheck();
        HealthStatus status = healthCheck.checkHealth(snapshot, gcStats);
        assertTrue(status.isHealthy());
    }
}
