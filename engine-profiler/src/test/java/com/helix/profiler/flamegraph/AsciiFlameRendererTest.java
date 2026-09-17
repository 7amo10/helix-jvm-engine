package com.helix.profiler.flamegraph;

import com.helix.profiler.flamegraph.ui.FlameGraphComponent;
import com.helix.profiler.flamegraph.ui.FlameGraphPanel;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AsciiFlameRendererTest {

    private FlameGraphAggregator aggregator;
    private AsciiFlameRenderer renderer;

    @BeforeEach
    void setUp() {
        aggregator = new FlameGraphAggregator();
        aggregator.addFoldedLine("main;app;eval;compiler 500");
        aggregator.addFoldedLine("main;app;eval;cache 300");
        aggregator.addFoldedLine("main;app;gc_cleanup 200");

        renderer = new AsciiFlameRenderer();
        renderer.setRootNode(aggregator.getRootNode());
    }

    @Test
    @DisplayName("Compute layout generates correct depth levels and proportional column widths on 80 cols")
    void shouldComputeProportionalLayout80Cols() {
        Map<Integer, List<AsciiFlameRenderer.FrameBar>> layout = renderer.computeLayout(80, 10);

        assertFalse(layout.isEmpty());
        assertTrue(layout.containsKey(0), "Should have root level");
        assertTrue(layout.containsKey(1), "Should have depth 1 (main)");
        assertTrue(layout.containsKey(2), "Should have depth 2 (app)");
        assertTrue(layout.containsKey(3), "Should have depth 3 (eval, gc_cleanup)");

        // Total width at level 0 should be 80
        assertEquals(80, layout.get(0).get(0).getWidth());

        // Level 3 should have eval (800 samples) and gc_cleanup (200 samples)
        List<AsciiFlameRenderer.FrameBar> depth3 = layout.get(3);
        assertEquals(2, depth3.size());
        AsciiFlameRenderer.FrameBar barEval = depth3.get(0);
        AsciiFlameRenderer.FrameBar barGc = depth3.get(1);

        assertEquals("eval", barEval.getNode().getName());
        assertEquals("gc_cleanup", barGc.getNode().getName());
        assertTrue(barEval.getWidth() > barGc.getWidth(), "eval (800) should be wider than gc_cleanup (200)");
        assertEquals(80, barEval.getWidth() + barGc.getWidth());
    }

    @Test
    @DisplayName("Compute layout adapts cleanly to wide 160+ column terminal")
    void shouldAdaptToWideTerminal160Cols() {
        Map<Integer, List<AsciiFlameRenderer.FrameBar>> layout = renderer.computeLayout(160, 10);

        assertEquals(160, layout.get(0).get(0).getWidth());
        List<AsciiFlameRenderer.FrameBar> depth3 = layout.get(3);
        assertTrue(depth3.get(0).getWidth() > 100);
    }

    @Test
    @DisplayName("Renders Unicode box-drawing bar layouts containing corners and lines")
    void shouldRenderUnicodeBoxDrawingCharacters() {
        String rendered = renderer.renderToString(80, 6, false);

        assertNotNull(rendered);
        assertTrue(rendered.contains("┌"), "Should contain top-left box corner ┌");
        assertTrue(rendered.contains("┐"), "Should contain top-right box corner ┐");
        assertTrue(rendered.contains("└"), "Should contain bottom-left box corner └");
        assertTrue(rendered.contains("┘"), "Should contain bottom-right box corner ┘");
        assertTrue(rendered.contains("─"), "Should contain horizontal box line ─");
        assertTrue(rendered.contains("│"), "Should contain vertical box separator │");
        assertTrue(rendered.contains("eval"), "Should contain frame name eval");
    }

    @Test
    @DisplayName("Smart text truncation truncates long frame signatures without overflowing columns")
    void shouldTruncateLongLabelsCleanly() {
        String longFrame = "com.helix.core.compiler.bytecode.HotSpotBytecodeGenerator.generateOptimizedBytecode";

        // Extremely wide (100 cols): full name with percentage
        String label100 = AsciiFlameRenderer.formatBarLabel(longFrame, 500, 1000, 100);
        assertEquals(100, label100.length());
        assertTrue(label100.contains("HotSpotBytecodeGenerator"));

        // Medium width (35 cols): abbreviated package
        String label35 = AsciiFlameRenderer.formatBarLabel(longFrame, 500, 1000, 35);
        assertEquals(35, label35.length());

        // Narrow width (15 cols): short class/method
        String label15 = AsciiFlameRenderer.formatBarLabel(longFrame, 500, 1000, 15);
        assertEquals(15, label15.length());

        // Very narrow width (4 cols): ellipsis dots
        String label4 = AsciiFlameRenderer.formatBarLabel(longFrame, 500, 1000, 4);
        assertEquals(4, label4.length());
    }

    @Test
    @DisplayName("Keyboard navigation traverses stack depths and siblings")
    void shouldNavigateFramesInteractively() {
        assertEquals(0, renderer.getSelectedDepth());

        renderer.navigateDown(80);
        assertEquals(1, renderer.getSelectedDepth());
        assertEquals("main", renderer.getSelectedNode().getName());

        renderer.navigateDown(80);
        assertEquals(2, renderer.getSelectedDepth());
        assertEquals("app", renderer.getSelectedNode().getName());

        renderer.navigateDown(80);
        assertEquals(3, renderer.getSelectedDepth());
        assertEquals("eval", renderer.getSelectedNode().getName());

        // Navigate right to gc_cleanup sibling
        renderer.navigateRight(80);
        assertEquals("gc_cleanup", renderer.getSelectedNode().getName());

        // Navigate left back to eval
        renderer.navigateLeft(80);
        assertEquals("eval", renderer.getSelectedNode().getName());

        // Navigate back up to depth 0
        renderer.navigateUp(80);
        assertEquals(2, renderer.getSelectedDepth());
        renderer.navigateUp(80);
        assertEquals(1, renderer.getSelectedDepth());
        renderer.navigateUp(80);
        assertEquals(0, renderer.getSelectedDepth());
    }

    @Test
    @DisplayName("Zoom into frame and reset zoom navigation operates cleanly")
    void shouldZoomAndResetZoom() {
        renderer.navigateDown(80);
        renderer.navigateDown(80);
        renderer.navigateDown(80); // eval
        assertEquals("eval", renderer.getSelectedNode().getName());

        renderer.zoomIntoSelected();
        assertEquals("eval", renderer.getZoomedNode().getName());

        // Under eval, depth 0 is now eval, depth 1 is compiler and cache
        Map<Integer, List<AsciiFlameRenderer.FrameBar>> zoomedLayout = renderer.computeLayout(80, 10);
        assertEquals("eval", zoomedLayout.get(0).get(0).getNode().getName());
        assertEquals(2, zoomedLayout.get(1).size()); // compiler (500) and cache (300)

        // Reset zoom
        renderer.resetZoom();
        assertEquals("root", renderer.getZoomedNode().getName());
    }

    @Test
    @DisplayName("Zoom out operates level-by-level via zoomHistory stack")
    void shouldZoomOutLevelByLevel() {
        renderer.navigateDown(80); // main
        renderer.zoomIntoSelected();
        assertEquals("main", renderer.getZoomedNode().getName());

        renderer.navigateDown(80); // app
        renderer.zoomIntoSelected();
        assertEquals("app", renderer.getZoomedNode().getName());

        // Zoom out one level to main
        renderer.zoomOut();
        assertEquals("main", renderer.getZoomedNode().getName());

        // Zoom out to root
        renderer.zoomOut();
        assertEquals("root", renderer.getZoomedNode().getName());
    }

    @Test
    @DisplayName("Top-right toast notification generates and expires correctly")
    void shouldManageToastNotifications() {
        renderer.showToast("TEST TOAST", List.of("Line 1", "Line 2"), 3000, null);
        assertNotNull(renderer.getActiveToast());
        assertEquals("TEST TOAST", renderer.getActiveToast().getTitle());
        assertEquals(2, renderer.getActiveToast().getLines().size());
        assertFalse(renderer.getActiveToast().isExpired());

        renderer.clearToast();
        assertNull(renderer.getActiveToast());
    }

    @Test
    @DisplayName("Toggle metric switches between CPU_TIME and ALLOCATION_BYTES")
    void shouldToggleMetric() {
        assertEquals(MetricType.CPU_TIME, renderer.getMetricType());

        renderer.toggleMetricType();
        assertEquals(MetricType.ALLOCATION_BYTES, renderer.getMetricType());

        renderer.toggleMetricType();
        assertEquals(MetricType.CPU_TIME, renderer.getMetricType());
    }

    @Test
    @DisplayName("Export SVG generates valid vector graphics file")
    void shouldExportSvg(@TempDir Path tempDir) throws IOException {
        Path svgFile = tempDir.resolve("test-flamegraph.svg");
        renderer.exportSvg(svgFile, "Test SVG Flame Graph");

        assertTrue(Files.exists(svgFile));
        String content = Files.readString(svgFile);
        assertTrue(content.contains("<svg"));
        assertTrue(content.contains("</svg>"));
        assertTrue(content.contains("<rect"));
        assertTrue(content.contains("Test SVG Flame Graph"));
        assertTrue(content.contains("eval"));
    }

    @Test
    @DisplayName("Export HTML generates valid interactive flame graph file")
    void shouldExportHtml(@TempDir Path tempDir) throws IOException {
        Path htmlFile = tempDir.resolve("test-flamegraph.html");
        renderer.exportHtml(htmlFile, "Test HTML Flame Graph");

        assertTrue(Files.exists(htmlFile));
        String content = Files.readString(htmlFile);
        assertTrue(content.contains("<!DOCTYPE html>"));
        assertTrue(content.contains("Test HTML Flame Graph"));
        assertTrue(content.contains("flame-bar"));
    }

    @Test
    @DisplayName("ExportBoth creates timestamped SVG and HTML files simultaneously")
    void shouldExportBoth(@TempDir Path tempDir) throws IOException {
        Path[] exported = renderer.exportBoth(tempDir, "helix-export");

        assertEquals(2, exported.length);
        assertTrue(Files.exists(exported[0]));
        assertTrue(Files.exists(exported[1]));
        assertTrue(exported[0].toString().endsWith(".svg"));
        assertTrue(exported[1].toString().endsWith(".html"));
    }

    @Test
    @DisplayName("FlameGraphComponent and FlameGraphPanel handle user key strokes and actions")
    void shouldHandleComponentKeyStrokes() {
        FlameGraphPanel panel = new FlameGraphPanel(renderer, aggregator);
        FlameGraphComponent comp = panel.getFlameComponent();

        assertNotNull(comp);
        assertNotNull(comp.getAsciiRenderer());

        // Test arrow key stroke
        comp.handleKeyStroke(new KeyStroke(KeyType.ArrowDown));
        assertEquals(1, comp.getAsciiRenderer().getSelectedDepth());

        // Test Enter to zoom
        comp.handleKeyStroke(new KeyStroke(KeyType.Enter));

        // Test Esc to reset
        comp.handleKeyStroke(new KeyStroke(KeyType.Escape));
        assertEquals("root", comp.getAsciiRenderer().getZoomedNode().getName());

        // Test 'm' to toggle metric
        comp.handleKeyStroke(new KeyStroke('m', false, false));
        assertEquals(MetricType.ALLOCATION_BYTES, comp.getAsciiRenderer().getMetricType());

        // Test 's' to export snapshot
        comp.handleKeyStroke(new KeyStroke('s', false, false));
    }
}
