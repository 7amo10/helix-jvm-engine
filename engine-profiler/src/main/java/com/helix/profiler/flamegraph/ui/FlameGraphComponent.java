package com.helix.profiler.flamegraph.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.helix.profiler.flamegraph.AsciiFlameRenderer;
import com.helix.profiler.flamegraph.FlameGraphAggregator;
import com.helix.profiler.flamegraph.StackFrameNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Interactive Lanterna GUI component that displays the Unicode box-drawing flame graph.
 * Supports keyboard navigation (Up, Down, Left, Right, Enter to zoom, Esc to reset,
 * M to toggle metric, S to export SVG/HTML).
 */
public class FlameGraphComponent extends AbstractInteractableComponent<FlameGraphComponent> {

    @FunctionalInterface
    public interface ToastCallback {
        void showToast(String title, java.util.List<String> lines, TextColor.RGB color);
    }

    private final AsciiFlameRenderer asciiRenderer;
    private FlameGraphAggregator aggregator;
    private Consumer<String> statusListener;
    private Consumer<StackFrameNode> frameSelectionListener;
    private Runnable overviewSwitchListener;
    private ToastCallback toastCallback;

    public FlameGraphComponent(AsciiFlameRenderer renderer) {
        this.asciiRenderer = Objects.requireNonNullElseGet(renderer, AsciiFlameRenderer::new);
    }

    public void setToastCallback(ToastCallback toastCallback) {
        this.toastCallback = toastCallback;
    }

    public FlameGraphComponent(AsciiFlameRenderer renderer, FlameGraphAggregator aggregator) {
        this.asciiRenderer = Objects.requireNonNullElseGet(renderer, AsciiFlameRenderer::new);
        this.aggregator = aggregator;
        if (aggregator != null) {
            this.asciiRenderer.setRootNode(aggregator.getRootNode());
        }
    }

    public AsciiFlameRenderer getAsciiRenderer() {
        return asciiRenderer;
    }

    public FlameGraphAggregator getAggregator() {
        return aggregator;
    }

    public void setAggregator(FlameGraphAggregator aggregator) {
        this.aggregator = aggregator;
        if (aggregator != null) {
            this.asciiRenderer.setRootNode(aggregator.getRootNode());
        }
        invalidate();
    }

    public void setStatusListener(Consumer<String> statusListener) {
        this.statusListener = statusListener;
    }

    public void setFrameSelectionListener(Consumer<StackFrameNode> frameSelectionListener) {
        this.frameSelectionListener = frameSelectionListener;
    }

    public void setOverviewSwitchListener(Runnable overviewSwitchListener) {
        this.overviewSwitchListener = overviewSwitchListener;
    }

    public void refreshFromAggregator() {
        if (aggregator != null) {
            StackFrameNode currentRoot = aggregator.getRootNode();
            this.asciiRenderer.setRootNode(currentRoot);
            notifySelectionChanged();
            invalidate();
        }
    }

    @Override
    protected com.googlecode.lanterna.gui2.InteractableRenderer<FlameGraphComponent> createDefaultRenderer() {
        return new com.googlecode.lanterna.gui2.InteractableRenderer<>() {
            @Override
            public TerminalPosition getCursorLocation(FlameGraphComponent component) {
                return null;
            }

            @Override
            public TerminalSize getPreferredSize(FlameGraphComponent component) {
                if (component.getTextGUI() != null && component.getTextGUI().getScreen() != null) {
                    TerminalSize term = component.getTextGUI().getScreen().getTerminalSize();
                    int cols = Math.max(60, term.getColumns() - 6);
                    int rows = Math.max(8, term.getRows() - 20);
                    return new TerminalSize(cols, rows);
                }
                return new TerminalSize(80, 14);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, FlameGraphComponent component) {
                TerminalSize size = graphics.getSize();
                asciiRenderer.renderToLanterna(graphics, 0, 0, size.getColumns(), size.getRows());
            }
        };
    }

    @Override
    public Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return Result.UNHANDLED;
        }

        int width = getSize().getColumns();
        if (width <= 0) {
            if (getTextGUI() != null && getTextGUI().getScreen() != null) {
                width = getTextGUI().getScreen().getTerminalSize().getColumns() - 6;
            } else {
                width = 80;
            }
        }

        KeyType type = keyStroke.getKeyType();
        Character ch = keyStroke.getCharacter();

        switch (type) {
            case ArrowUp -> {
                asciiRenderer.navigateUp(width);
                notifySelectionChanged();
                invalidate();
                return Result.HANDLED;
            }
            case ArrowDown -> {
                asciiRenderer.navigateDown(width);
                notifySelectionChanged();
                invalidate();
                return Result.HANDLED;
            }
            case ArrowLeft -> {
                asciiRenderer.navigateLeft(width);
                notifySelectionChanged();
                invalidate();
                return Result.HANDLED;
            }
            case ArrowRight -> {
                asciiRenderer.navigateRight(width);
                notifySelectionChanged();
                invalidate();
                return Result.HANDLED;
            }
            case Enter -> {
                asciiRenderer.zoomIntoSelected();
                notifyStatus("Zoom: " + (asciiRenderer.getZoomedNode() != null ? asciiRenderer.getZoomedNode().getName() : "root"));
                notifySelectionChanged();
                invalidate();
                return Result.HANDLED;
            }
            case Escape -> {
                asciiRenderer.zoomOut();
                notifyStatus("Zoom: " + (asciiRenderer.getZoomedNode() != null ? asciiRenderer.getZoomedNode().getName() : "root"));
                notifySelectionChanged();
                invalidate();
                return Result.HANDLED;
            }
            case Character -> {
                if (ch != null) {
                    if (ch == 'm' || ch == 'M') {
                        asciiRenderer.toggleMetricType();
                        if (aggregator != null) {
                            aggregator.setActiveMetric(asciiRenderer.getMetricType());
                            asciiRenderer.setRootNode(aggregator.getRootNode());
                        }
                        asciiRenderer.showToast("[⇄] METRIC TOGGLED", java.util.List.of(
                                "Active: " + asciiRenderer.getMetricType().getDisplayName(),
                                "Unit  : " + asciiRenderer.getMetricType().getUnit()
                        ), 2500, new TextColor.RGB(180, 100, 255));
                        notifyStatus("Metric toggled to: " + asciiRenderer.getMetricType().getDisplayName());
                        notifySelectionChanged();
                        invalidate();
                        return Result.HANDLED;
                    } else if (ch == 's' || ch == 'S') {
                        exportSnapshots();
                        invalidate();
                        return Result.HANDLED;
                    } else if (ch == 'o' || ch == 'O') {
                        if (overviewSwitchListener != null) {
                            overviewSwitchListener.run();
                            return Result.HANDLED;
                        }
                    }
                }
            }
            default -> {
            }
        }

        return super.handleKeyStroke(keyStroke);
    }

    public void exportSnapshots() {
        try {
            Path targetDir = Path.of("target", "flamegraphs");
            Path[] paths = asciiRenderer.exportBoth(targetDir, "helix-flamegraph");
            String svgName = paths[0].getFileName().toString();
            String htmlName = paths[1].getFileName().toString();

            if (toastCallback != null) {
                toastCallback.showToast("FLAME GRAPH EXPORTED", java.util.List.of(
                        "• SVG : " + svgName,
                        "• HTML: " + htmlName,
                        "Saved in: target/flamegraphs/"
                ), new TextColor.RGB(46, 204, 113));
            }

            notifyStatus("Exported SVG: " + svgName + " and HTML: " + htmlName);
        } catch (IOException e) {
            if (toastCallback != null) {
                toastCallback.showToast("EXPORT FAILED", java.util.List.of(
                        "Error: " + e.getMessage()
                ), new TextColor.RGB(230, 50, 50));
            }
            notifyStatus("Export failed: " + e.getMessage());
        }
    }

    private void notifyStatus(String message) {
        if (statusListener != null) {
            statusListener.accept(message);
        }
    }

    private void notifySelectionChanged() {
        if (frameSelectionListener != null) {
            frameSelectionListener.accept(asciiRenderer.getSelectedNode());
        }
    }
}
