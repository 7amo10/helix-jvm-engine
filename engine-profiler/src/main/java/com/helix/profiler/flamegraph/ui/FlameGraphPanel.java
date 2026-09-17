package com.helix.profiler.flamegraph.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.helix.profiler.flamegraph.AsciiFlameRenderer;
import com.helix.profiler.flamegraph.FlameGraphAggregator;
import com.helix.profiler.flamegraph.MetricType;
import com.helix.profiler.flamegraph.StackFrameNode;

import java.util.Objects;

/**
 * Complete interactive Lanterna Flame Graph panel containing the canvas,
 * breadcrumb navigation, frame statistics inspector, and keyboard control legend.
 */
public class FlameGraphPanel extends Panel {

    private final FlameGraphComponent flameComponent;
    private final Label breadcrumbLabel;
    private final Label metricLabel;
    private final Label frameNameLabel;
    private final Label frameStatsLabel;
    private final Label legendLabel;

    public FlameGraphPanel(FlameGraphAggregator aggregator) {
        this(new AsciiFlameRenderer(), aggregator);
    }

    public FlameGraphPanel(AsciiFlameRenderer renderer, FlameGraphAggregator aggregator) {
        super(new LinearLayout(Direction.VERTICAL));

        this.flameComponent = new FlameGraphComponent(renderer, aggregator);

        // Header Panel: Breadcrumb and Metric indicator
        Panel headerPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        this.breadcrumbLabel = new Label("Path: root");
        this.breadcrumbLabel.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);

        this.metricLabel = new Label(" [ Metric: CPU_TIME (samples) ] ");
        this.metricLabel.setForegroundColor(TextColor.ANSI.YELLOW_BRIGHT);

        headerPanel.addComponent(breadcrumbLabel);
        headerPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        headerPanel.addComponent(metricLabel);
        addComponent(headerPanel);

        // Flame Graph Canvas inside border
        Panel canvasWrapper = new Panel(new LinearLayout(Direction.VERTICAL));
        canvasWrapper.addComponent(flameComponent);
        addComponent(canvasWrapper.withBorder(Borders.singleLine("Call Stack Hierarchy (Unicode Flame Graph)")));

        // Frame Inspector Details (clean 2-line layout)
        Panel detailsPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        this.frameNameLabel = new Label("Frame: (root)");
        this.frameNameLabel.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        detailsPanel.addComponent(frameNameLabel);

        this.frameStatsLabel = new Label("Total: 0 | Self: 0% | Depth: 0 | Children: 0");
        this.frameStatsLabel.setForegroundColor(TextColor.ANSI.RED);
        detailsPanel.addComponent(frameStatsLabel);

        addComponent(detailsPanel.withBorder(Borders.singleLine("Frame Inspector & Telemetry")));

        // Controls / Legend Bar
        Panel controlsPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        this.legendLabel = new Label(" [↑/↓] Depth  [←/→] Sibling  [Enter] Zoom In  [Esc] Zoom Out  [M] Toggle Metric  [S] Export  [O] Overview ");
        this.legendLabel.setForegroundColor(TextColor.ANSI.WHITE);
        this.legendLabel.setBackgroundColor(TextColor.ANSI.BLACK);
        controlsPanel.addComponent(legendLabel);
        addComponent(controlsPanel);

        // Wire up callbacks
        flameComponent.setStatusListener(msg -> updateHeaderLabels());

        flameComponent.setFrameSelectionListener(this::updateDetails);

        updateHeaderLabels();
        updateDetails(renderer.getSelectedNode());
    }

    public FlameGraphComponent getFlameComponent() {
        return flameComponent;
    }

    public void setOverviewSwitchListener(Runnable listener) {
        flameComponent.setOverviewSwitchListener(listener);
    }

    public void setToastCallback(FlameGraphComponent.ToastCallback callback) {
        flameComponent.setToastCallback(callback);
    }

    public void refresh() {
        flameComponent.refreshFromAggregator();
        updateHeaderLabels();
        updateDetails(flameComponent.getAsciiRenderer().getSelectedNode());
    }

    private void updateHeaderLabels() {
        StackFrameNode zoomed = flameComponent.getAsciiRenderer().getZoomedNode();
        String path = (zoomed != null && !zoomed.isRoot()) ? zoomed.getName() : "root";
        breadcrumbLabel.setText("Path: " + truncateWithEllipsis(path, 45));

        MetricType m = flameComponent.getAsciiRenderer().getMetricType();
        metricLabel.setText(String.format(" [ Metric: %s (%s) ] ", m.getDisplayName(), m.getUnit()));
        if (m == MetricType.ALLOCATION_BYTES) {
            metricLabel.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        } else {
            metricLabel.setForegroundColor(TextColor.ANSI.YELLOW_BRIGHT);
        }
    }

    private void updateDetails(StackFrameNode node) {
        if (node == null) {
            frameNameLabel.setText("Frame: (none selected)");
            frameStatsLabel.setText("Total: 0 | Self: 0% | Depth: 0");
            return;
        }

        StackFrameNode root = flameComponent.getAsciiRenderer().getRootNode();
        long rootTotal = root != null ? root.getTotalValue() : 1;
        if (rootTotal <= 0) rootTotal = 1;

        double cumPct = (node.getTotalValue() * 100.0 / rootTotal);
        double selfPct = (node.getSelfValue() * 100.0 / rootTotal);
        MetricType m = flameComponent.getAsciiRenderer().getMetricType();

        frameNameLabel.setText("Frame: " + truncateWithEllipsis(node.getName(), 70));
        frameStatsLabel.setText(String.format("Total: %,d %s (%.1f%%) | Self: %,d (%.1f%%) | Depth: %d | Children: %d",
                node.getTotalValue(),
                m.getUnit(),
                cumPct,
                node.getSelfValue(),
                selfPct,
                node.getDepth(),
                node.getChildren().size()));
    }

    private static String truncateWithEllipsis(String text, int maxCols) {
        if (text == null) return "";
        if (text.length() <= maxCols) return text;
        return text.substring(0, Math.max(0, maxCols - 3)) + "...";
    }
}
