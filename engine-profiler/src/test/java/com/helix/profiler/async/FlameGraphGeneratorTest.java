package com.helix.profiler.async;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlameGraphGeneratorTest {

    @Test
    void testParseCollapsedData() {
        FlameGraphGenerator generator = new FlameGraphGenerator();
        String collapsed = """
               com.helix.core.RuleCompiler;parse 100
               com.helix.core.executor.SyncExecutor;execute 250
            """;

        List<Map.Entry<String, Long>> entries = generator.parseCollapsedData(collapsed);
        assertEquals(2, entries.size());
        assertEquals("com.helix.core.RuleCompiler;parse", entries.get(0).getKey());
        assertEquals(100L, entries.get(0).getValue());
        assertEquals("com.helix.core.executor.SyncExecutor;execute", entries.get(1).getKey());
        assertEquals(250L, entries.get(1).getValue());
    }

    @Test
    void testGenerateHtmlFlameGraph() {
        FlameGraphGenerator generator = new FlameGraphGenerator();
        String collapsed = "com.helix.core.RuleCompiler;parse 100\n";
        String html = generator.generateHtmlFlameGraph(collapsed, "Test Flame Graph");

        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("Test Flame Graph"));
        assertTrue(html.contains("com.helix.core.RuleCompiler;parse"));
    }

    @Test
    void testGenerateHtmlFlameGraphToFile(@TempDir Path tempDir) throws IOException {
        FlameGraphGenerator generator = new FlameGraphGenerator();
        Path collapsedFile = tempDir.resolve("samples.txt");
        Path outputFile = tempDir.resolve("flamegraph.html");

        Files.writeString(collapsedFile, "com.helix.core.RuleCompiler;parse 50\n");
        generator.generateHtmlFlameGraph(collapsedFile, outputFile, "CPU Profile");

        assertTrue(Files.exists(outputFile));
        String html = Files.readString(outputFile);
        assertTrue(html.contains("CPU Profile"));
    }
}
