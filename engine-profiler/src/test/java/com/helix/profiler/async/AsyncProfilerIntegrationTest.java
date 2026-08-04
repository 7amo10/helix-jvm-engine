package com.helix.profiler.async;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AsyncProfilerIntegrationTest {

    private AsyncProfilerIntegration profiler;

    @BeforeEach
    void setUp() {
        profiler = new AsyncProfilerIntegration();
    }

    @Test
    void testStartAndStopSession() {
        assertFalse(profiler.isProfiling());

        profiler.start(ProfileMode.ALLOC);
        assertTrue(profiler.isProfiling());
        assertEquals(ProfileMode.ALLOC, profiler.getCurrentMode());

        profiler.recordSample("com.helix.core.RuleCompiler;parse", 50);

        String samples = profiler.stop();
        assertFalse(profiler.isProfiling());
        assertTrue(samples.contains("com.helix.core.RuleCompiler;parse 50"));
    }

    @Test
    void testDumpCollapsedAndHtml(@TempDir Path tempDir) throws IOException {
        profiler.start(ProfileMode.CPU);
        profiler.recordSample("com.helix.core.executor.SyncExecutor;execute", 120);

        Path collapsedFile = tempDir.resolve("cpu-profile.collapsed");
        Path htmlFile = tempDir.resolve("cpu-profile.html");

        profiler.dumpCollapsed(collapsedFile);
        profiler.dumpHtml(htmlFile, "CPU Flame Graph Test");

        assertTrue(Files.exists(collapsedFile));
        assertTrue(Files.exists(htmlFile));

        String html = Files.readString(htmlFile);
        assertTrue(html.contains("CPU Flame Graph Test"));
        assertTrue(html.contains("com.helix.core.executor.SyncExecutor;execute"));
    }
}
