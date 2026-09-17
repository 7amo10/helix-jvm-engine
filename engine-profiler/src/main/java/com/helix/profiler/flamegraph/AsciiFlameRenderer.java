package com.helix.profiler.flamegraph;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.helix.profiler.async.FlameGraphGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * High-performance Unicode ASCII Flame Graph Renderer.
 * <p>
 * Translates hierarchical prefix tries ({@link StackFrameNode}) into Unicode box-drawing
 * bar layouts (┌, ┐, └, ┘, ─, │, ┬, ┴, ┼). Dynamically adapts bar widths to terminal columns,
 * applies smart text truncation, and renders interactive, theme-safe colors.
 * <p>
 * Also provides standalone vector SVG and interactive HTML export capabilities.
 */
public class AsciiFlameRenderer {

    private static final Logger log = LoggerFactory.getLogger(AsciiFlameRenderer.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final FlameGraphTheme theme;
    private MetricType metricType;
    private StackFrameNode rootNode;
    private StackFrameNode zoomedNode;
    private StackFrameNode selectedNode;
    private int selectedDepth;
    private int selectedIndex;
    private final Deque<StackFrameNode> zoomHistory = new ArrayDeque<>();
    private volatile ToastNotification activeToast;

    /**
     * Modern floating toast notification anchored to the top-right corner of the terminal.
     */
    public static class ToastNotification {
        private final String title;
        private final List<String> lines;
        private final long expiresAt;
        private final TextColor.RGB borderColor;

        public ToastNotification(String title, List<String> lines, long durationMs, TextColor.RGB borderColor) {
            this.title = title != null ? title : "";
            this.lines = lines != null ? lines : List.of();
            this.expiresAt = System.currentTimeMillis() + durationMs;
            this.borderColor = borderColor != null ? borderColor : new TextColor.RGB(46, 204, 113);
        }

        public String getTitle() {
            return title;
        }

        public List<String> getLines() {
            return lines;
        }

        public TextColor.RGB getBorderColor() {
            return borderColor;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Represents a single positioned frame bar in the terminal layout.
     */
    public static class FrameBar {
        private final StackFrameNode node;
        private final int depth;
        private final int startCol;
        private final int width;
        private final boolean selected;
        private final boolean zoomed;

        public FrameBar(StackFrameNode node, int depth, int startCol, int width, boolean selected, boolean zoomed) {
            this.node = node;
            this.depth = depth;
            this.startCol = startCol;
            this.width = width;
            this.selected = selected;
            this.zoomed = zoomed;
        }

        public StackFrameNode getNode() {
            return node;
        }

        public int getDepth() {
            return depth;
        }

        public int getStartCol() {
            return startCol;
        }

        public int getWidth() {
            return width;
        }

        public int getEndCol() {
            return startCol + width;
        }

        public boolean isSelected() {
            return selected;
        }

        public boolean isZoomed() {
            return zoomed;
        }
    }

    public AsciiFlameRenderer() {
        this(new FlameGraphTheme(), MetricType.CPU_TIME);
    }

    public AsciiFlameRenderer(FlameGraphTheme theme, MetricType metricType) {
        this.theme = Objects.requireNonNullElseGet(theme, FlameGraphTheme::new);
        this.metricType = Objects.requireNonNullElse(metricType, MetricType.CPU_TIME);
        this.selectedDepth = 0;
        this.selectedIndex = 0;
    }

    public FlameGraphTheme getTheme() {
        return theme;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public void setMetricType(MetricType metricType) {
        this.metricType = Objects.requireNonNullElse(metricType, MetricType.CPU_TIME);
    }

    public void toggleMetricType() {
        this.metricType = (this.metricType == MetricType.CPU_TIME)
                ? MetricType.ALLOCATION_BYTES
                : MetricType.CPU_TIME;
    }

    public StackFrameNode getRootNode() {
        return rootNode;
    }

    public void setRootNode(StackFrameNode rootNode) {
        this.rootNode = rootNode;
        if (this.zoomedNode == null) {
            this.zoomedNode = rootNode;
        }
        if (this.selectedNode == null) {
            this.selectedNode = rootNode;
        }
    }

    public StackFrameNode getZoomedNode() {
        return zoomedNode != null ? zoomedNode : rootNode;
    }

    public void setZoomedNode(StackFrameNode zoomedNode) {
        this.zoomedNode = zoomedNode;
        this.selectedNode = zoomedNode;
        this.selectedDepth = 0;
        this.selectedIndex = 0;
    }

    public StackFrameNode getSelectedNode() {
        return selectedNode;
    }

    public int getSelectedDepth() {
        return selectedDepth;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    // =========================================================================
    // Layout Computation
    // =========================================================================

    /**
     * Computes the 2D layout of flame graph bars for the current zoomed frame.
     *
     * @param totalWidth Total available terminal columns (e.g. 80 - 160+)
     * @param maxDepths  Maximum call depth levels to include
     * @return Map of depth level (0-based) to list of positioned FrameBar instances
     */
    public Map<Integer, List<FrameBar>> computeLayout(int totalWidth, int maxDepths) {
        Map<Integer, List<FrameBar>> layout = new LinkedHashMap<>();
        StackFrameNode effectiveRoot = getZoomedNode();
        if (effectiveRoot == null || totalWidth <= 2) {
            return layout;
        }

        long rootTotal = effectiveRoot.getTotalValue();
        if (rootTotal <= 0) {
            rootTotal = 1;
        }

        // Depth 0: Effective Root / Zoom Node
        boolean isRootSelected = (selectedNode == null || selectedNode == effectiveRoot);
        FrameBar rootBar = new FrameBar(effectiveRoot, 0, 0, totalWidth, isRootSelected, effectiveRoot == zoomedNode);
        List<FrameBar> level0 = new ArrayList<>();
        level0.add(rootBar);
        layout.put(0, level0);

        if (maxDepths <= 1) {
            return layout;
        }

        // BFS / Queue to lay out subsequent levels proportionally
        List<FrameBar> currentLevel = level0;
        for (int depth = 1; depth < maxDepths; depth++) {
            List<FrameBar> nextLevel = new ArrayList<>();
            for (FrameBar parentBar : currentLevel) {
                StackFrameNode parent = parentBar.getNode();
                Collection<StackFrameNode> children = parent.getChildren();
                if (children.isEmpty() || parentBar.getWidth() < 2) {
                    continue;
                }

                long parentTotal = parent.getTotalValue();
                if (parentTotal <= 0) {
                    continue;
                }

                int availableParentWidth = parentBar.getWidth();
                int currentOffset = parentBar.getStartCol();

                // Sort children descending by total value for clean presentation
                List<StackFrameNode> sortedChildren = new ArrayList<>(children);
                sortedChildren.sort(Comparator.comparingLong(StackFrameNode::getTotalValue).reversed());

                for (StackFrameNode child : sortedChildren) {
                    long childVal = child.getTotalValue();
                    if (childVal <= 0) {
                        continue;
                    }

                    int remaining = parentBar.getEndCol() - currentOffset;
                    if (remaining <= 0) {
                        break;
                    }

                    double ratio = (double) childVal / (double) parentTotal;
                    int childWidth = (int) Math.round(ratio * availableParentWidth);
                    if (childWidth < 2 && sortedChildren.size() > 2) {
                        continue;
                    }
                    if (childWidth < 1) {
                        childWidth = 1;
                    }
                    if (childWidth > remaining) {
                        childWidth = remaining;
                    }

                    boolean isSelected = (selectedNode == child);
                    FrameBar childBar = new FrameBar(child, depth, currentOffset, childWidth, isSelected, false);
                    nextLevel.add(childBar);
                    currentOffset += childWidth;
                }
            }

            if (nextLevel.isEmpty()) {
                break;
            }
            layout.put(depth, nextLevel);
            currentLevel = nextLevel;
        }

        return layout;
    }

    // =========================================================================
    // Text Truncation & Labeling
    // =========================================================================

    /**
     * Smartly formats and truncates a frame label to fit within the specified column width.
     *
     * @param frameName  Full method/frame signature (e.g. "com.helix.core.RuleEvaluator.evaluate")
     * @param totalValue Cumulative samples/bytes
     * @param rootTotal  Root cumulative samples/bytes
     * @param width      Available width (columns)
     * @return Truncated, readable label
     */
    public static String formatBarLabel(String frameName, long totalValue, long rootTotal, int width) {
        if (width <= 0) {
            return "";
        }
        if (width <= 2) {
            return ".".repeat(width);
        }
        if (width <= 4) {
            return " " + ".".repeat(Math.max(1, width - 2)) + " ";
        }

        double pct = rootTotal > 0 ? (totalValue * 100.0 / rootTotal) : 0.0;
        String pctStr = String.format("%.1f%%", pct);

        // Full display: "Package.Class.method (XX.X%)"
        String fullLabel = frameName + " (" + pctStr + ")";
        if (fullLabel.length() <= width - 2) {
            return centerOrPad(fullLabel, width);
        }

        // Try shortened package name: "c.h.core.Class.method (XX.X%)"
        String shortPackage = abbreviatePackage(frameName);
        String shortWithPct = shortPackage + " (" + pctStr + ")";
        if (shortWithPct.length() <= width - 2) {
            return centerOrPad(shortWithPct, width);
        }

        // Try simple Class.method: "Class.method (XX.X%)"
        String simpleName = getSimpleFrameName(frameName);
        String simpleWithPct = simpleName + " (" + pctStr + ")";
        if (simpleWithPct.length() <= width - 2) {
            return centerOrPad(simpleWithPct, width);
        }

        // Try just Class.method without percentage
        if (simpleName.length() <= width - 2) {
            return centerOrPad(simpleName, width);
        }

        // Truncate method name with ellipsis
        int usable = width - 2;
        if (usable > 4) {
            String truncated = simpleName.substring(0, usable - 2) + "..";
            return centerOrPad(truncated, width);
        }

        return centerOrPad("..", width);
    }

    private static String abbreviatePackage(String frameName) {
        if (frameName == null) return "";
        String[] parts = frameName.split("\\.");
        if (parts.length <= 2) return frameName;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 2; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(parts[i].charAt(0)).append(".");
            }
        }
        sb.append(parts[parts.length - 2]).append(".").append(parts[parts.length - 1]);
        return sb.toString();
    }

    private static String getSimpleFrameName(String frameName) {
        if (frameName == null) return "";
        int lastDot = frameName.lastIndexOf('.');
        if (lastDot <= 0) return frameName;
        int secondLastDot = frameName.lastIndexOf('.', lastDot - 1);
        if (secondLastDot < 0) return frameName;
        return frameName.substring(secondLastDot + 1);
    }

    private static String centerOrPad(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int remaining = width - text.length();
        int left = remaining / 2;
        int right = remaining - left;
        return " ".repeat(left) + text + " ".repeat(right);
    }

    // =========================================================================
    // In-Terminal Unicode Box-Drawing Text Rendering
    // =========================================================================

    /**
     * Renders a multi-level Unicode box-drawing flame graph to a String.
     * Uses box-drawing characters: ┌, ┐, └, ┘, ─, │, ┬, ┴.
     *
     * @param totalWidth Total width in columns (e.g. 80 to 160+)
     * @param maxDepths  Maximum stack depths to render
     * @param withAnsi   Whether to embed 24-bit ANSI colors
     * @return Rendered flame graph string
     */
    public String renderToString(int totalWidth, int maxDepths, boolean withAnsi) {
        Map<Integer, List<FrameBar>> layout = computeLayout(totalWidth, maxDepths);
        if (layout.isEmpty()) {
            return "[No stack samples collected yet]\n";
        }

        StringBuilder sb = new StringBuilder();
        long rootTotal = getZoomedNode() != null ? getZoomedNode().getTotalValue() : 1;
        if (rootTotal <= 0) rootTotal = 1;

        // Render each depth level with top border, content bar, and bottom border
        for (Map.Entry<Integer, List<FrameBar>> entry : layout.entrySet()) {
            int depth = entry.getKey();
            List<FrameBar> bars = entry.getValue();
            if (bars.isEmpty()) continue;

            // Top Border: ┌─────┬─────┐
            StringBuilder topBorder = new StringBuilder();
            int currentX = 0;
            for (FrameBar bar : bars) {
                while (currentX < bar.getStartCol()) {
                    topBorder.append(' ');
                    currentX++;
                }
                int barWidth = bar.getWidth();
                if (barWidth == 1) {
                    topBorder.append('┬');
                } else {
                    topBorder.append('┌');
                    topBorder.append("─".repeat(Math.max(0, barWidth - 2)));
                    topBorder.append('┐');
                }
                currentX += barWidth;
            }
            while (currentX < totalWidth) {
                topBorder.append(' ');
                currentX++;
            }
            sb.append(topBorder).append('\n');

            // Bar Content: │ FrameName (42%) │
            StringBuilder contentLine = new StringBuilder();
            currentX = 0;
            for (FrameBar bar : bars) {
                while (currentX < bar.getStartCol()) {
                    contentLine.append(' ');
                    currentX++;
                }

                String label = formatBarLabel(bar.getNode().getFrameName(), bar.getNode().getTotalValue(), rootTotal, bar.getWidth());
                if (withAnsi) {
                    TextColor.RGB bg = bar.isSelected()
                            ? theme.getSelectedFrameBackgroundColor()
                            : theme.getFrameBackgroundColor(bar.getNode().getFrameName(), depth, metricType);
                    TextColor fg = FlameGraphTheme.getContrastingTextColor(bg);
                    int fgR = (fg instanceof TextColor.RGB r) ? r.getRed() : 255;
                    int fgG = (fg instanceof TextColor.RGB r) ? r.getGreen() : 255;
                    int fgB = (fg instanceof TextColor.RGB r) ? r.getBlue() : 255;

                    contentLine.append(FlameGraphTheme.ansiBg(bg.getRed(), bg.getGreen(), bg.getBlue()))
                            .append(FlameGraphTheme.ansiFg(fgR, fgG, fgB));

                    if (bar.isSelected()) {
                        contentLine.append(FlameGraphTheme.ansiBold());
                    }

                    if (label.length() >= 2 && label.startsWith(" ") && label.endsWith(" ")) {
                        contentLine.append('│').append(label, 1, label.length() - 1).append('│');
                    } else {
                        contentLine.append(label);
                    }
                    contentLine.append(FlameGraphTheme.ansiReset());
                } else {
                    if (label.length() >= 2 && label.startsWith(" ") && label.endsWith(" ")) {
                        contentLine.append('│').append(label, 1, label.length() - 1).append('│');
                    } else {
                        contentLine.append(label);
                    }
                }
                currentX += bar.getWidth();
            }
            while (currentX < totalWidth) {
                contentLine.append(' ');
                currentX++;
            }
            sb.append(contentLine).append('\n');

            // Bottom Border: └─────┴─────┘
            StringBuilder bottomBorder = new StringBuilder();
            currentX = 0;
            for (FrameBar bar : bars) {
                while (currentX < bar.getStartCol()) {
                    bottomBorder.append(' ');
                    currentX++;
                }
                int barWidth = bar.getWidth();
                if (barWidth == 1) {
                    bottomBorder.append('┴');
                } else {
                    bottomBorder.append('└');
                    bottomBorder.append("─".repeat(Math.max(0, barWidth - 2)));
                    bottomBorder.append('┘');
                }
                currentX += barWidth;
            }
            while (currentX < totalWidth) {
                bottomBorder.append(' ');
                currentX++;
            }
            sb.append(bottomBorder).append('\n');
        }

        return sb.toString();
    }

    // =========================================================================
    // Lanterna TextGraphics Drawing
    // =========================================================================

    /**
     * Renders the flame graph directly onto a Lanterna {@link TextGraphics} viewport.
     * Guarantees 100% legibility on any terminal theme with explicit foreground &amp; background colors.
     *
     * @param graphics Lanterna TextGraphics instance
     * @param originX  Top-left column offset
     * @param originY  Top-left row offset
     * @param width    Available columns
     * @param height   Available rows
     */
    public void renderToLanterna(TextGraphics graphics, int originX, int originY, int width, int height) {
        if (graphics == null || width <= 2 || height <= 2) {
            return;
        }

        // Fill background canvas safely
        TextColor.RGB canvasBg = theme.getCanvasBackgroundColor();
        graphics.setBackgroundColor(canvasBg);
        graphics.fillRectangle(new TerminalPosition(originX, originY), new TerminalSize(width, height), ' ');

        // Each depth level takes 2 vertical rows (top border + content) or 1 compact row if tight
        boolean compact = (height < 14);
        int rowsPerDepth = compact ? 1 : 2;
        int maxDepths = Math.max(1, height / rowsPerDepth);

        Map<Integer, List<FrameBar>> layout = computeLayout(width, maxDepths);
        if (layout.isEmpty()) {
            graphics.setForegroundColor(new TextColor.RGB(180, 180, 180));
            graphics.putString(originX + 2, originY + 2, "[ No execution samples collected yet - run rules to generate profile data ]");
            return;
        }

        long rootTotal = getZoomedNode() != null ? getZoomedNode().getTotalValue() : 1;
        if (rootTotal <= 0) rootTotal = 1;

        int currentY = originY;
        for (Map.Entry<Integer, List<FrameBar>> entry : layout.entrySet()) {
            if (currentY >= originY + height) {
                break;
            }

            int depth = entry.getKey();
            List<FrameBar> bars = entry.getValue();

            // 1. Optional Top Border Row in non-compact mode
            if (!compact && currentY + 1 < originY + height) {
                for (FrameBar bar : bars) {
                    int bx = originX + bar.getStartCol();
                    int bw = bar.getWidth();
                    TextColor.RGB borderCol = bar.isSelected()
                            ? new TextColor.RGB(255, 255, 100)
                            : theme.getBorderColor();
                    graphics.setForegroundColor(borderCol);
                    graphics.setBackgroundColor(canvasBg);

                    if (bw == 1) {
                        graphics.setCharacter(bx, currentY, '┬');
                    } else {
                        graphics.setCharacter(bx, currentY, '┌');
                        for (int c = 1; c < bw - 1; c++) {
                            graphics.setCharacter(bx + c, currentY, '─');
                        }
                        graphics.setCharacter(bx + bw - 1, currentY, '┐');
                    }
                }
                currentY++;
            }

            // 2. Bar Content Row
            if (currentY < originY + height) {
                for (FrameBar bar : bars) {
                    int bx = originX + bar.getStartCol();
                    int bw = bar.getWidth();

                    TextColor.RGB bg = bar.isSelected()
                            ? theme.getSelectedFrameBackgroundColor()
                            : theme.getFrameBackgroundColor(bar.getNode().getFrameName(), depth, metricType);
                    TextColor fg = FlameGraphTheme.getContrastingTextColor(bg);

                    graphics.setBackgroundColor(bg);
                    graphics.setForegroundColor(fg);

                    String label = formatBarLabel(bar.getNode().getFrameName(), bar.getNode().getTotalValue(), rootTotal, bw);
                    if (bar.isSelected() && label.length() >= 4) {
                        // Mark active selection clearly
                        label = "►" + label.substring(1, label.length() - 1) + "◄";
                    }

                    for (int c = 0; c < bw; c++) {
                        char ch = (c < label.length()) ? label.charAt(c) : ' ';
                        graphics.setCharacter(bx + c, currentY, ch);
                    }
                }
                currentY++;
            }
        }

        // 3. Render Top-Right Floating Toast Notification (nvim/emacs style)
        ToastNotification toast = getActiveToast();
        if (toast != null) {
            renderToast(graphics, originX, originY, width, height, toast);
        }
    }

    private void renderToast(TextGraphics graphics, int originX, int originY, int width, int height, ToastNotification toast) {
        int maxLineLen = toast.getTitle().length();
        for (String line : toast.getLines()) {
            if (line.length() > maxLineLen) {
                maxLineLen = line.length();
            }
        }

        int boxWidth = Math.min(width - 4, Math.max(36, maxLineLen + 6));
        int boxHeight = 2 + toast.getLines().size() + 2; // top border, title, divider, lines, bottom border

        int toastX = originX + width - boxWidth - 1;
        int toastY = originY + 1;

        if (toastX < originX || toastY + boxHeight > originY + height) {
            return;
        }

        TextColor.RGB toastBg = new TextColor.RGB(22, 27, 34); // deep dark charcoal slate
        TextColor.RGB toastBorder = toast.getBorderColor();
        TextColor.RGB titleFg = new TextColor.RGB(255, 255, 255);
        TextColor.RGB textFg = new TextColor.RGB(235, 235, 235);

        // Top border: ┌────────────────────┐
        graphics.setBackgroundColor(toastBg);
        graphics.setForegroundColor(toastBorder);
        graphics.setCharacter(toastX, toastY, '┌');
        for (int c = 1; c < boxWidth - 1; c++) {
            graphics.setCharacter(toastX + c, toastY, '─');
        }
        graphics.setCharacter(toastX + boxWidth - 1, toastY, '┐');

        // Title row: │ [✓] TITLE          │
        int row = toastY + 1;
        graphics.setCharacter(toastX, row, '│');
        graphics.setForegroundColor(titleFg);
        String titleStr = " " + toast.getTitle();
        for (int c = 1; c < boxWidth - 1; c++) {
            char ch = (c - 1 < titleStr.length()) ? titleStr.charAt(c - 1) : ' ';
            graphics.setCharacter(toastX + c, row, ch);
        }
        graphics.setForegroundColor(toastBorder);
        graphics.setCharacter(toastX + boxWidth - 1, row, '│');

        // Divider: ├────────────────────┤
        row++;
        graphics.setCharacter(toastX, row, '├');
        for (int c = 1; c < boxWidth - 1; c++) {
            graphics.setCharacter(toastX + c, row, '─');
        }
        graphics.setCharacter(toastX + boxWidth - 1, row, '┤');

        // Content lines: │  • SVG: ...       │
        for (String line : toast.getLines()) {
            row++;
            if (row >= originY + height) break;
            graphics.setForegroundColor(toastBorder);
            graphics.setCharacter(toastX, row, '│');

            graphics.setForegroundColor(textFg);
            String content = " " + line;
            for (int c = 1; c < boxWidth - 1; c++) {
                char ch = (c - 1 < content.length()) ? content.charAt(c - 1) : ' ';
                graphics.setCharacter(toastX + c, row, ch);
            }
            graphics.setForegroundColor(toastBorder);
            graphics.setCharacter(toastX + boxWidth - 1, row, '│');
        }

        // Bottom border: └────────────────────┘
        row++;
        if (row < originY + height) {
            graphics.setForegroundColor(toastBorder);
            graphics.setCharacter(toastX, row, '└');
            for (int c = 1; c < boxWidth - 1; c++) {
                graphics.setCharacter(toastX + c, row, '─');
            }
            graphics.setCharacter(toastX + boxWidth - 1, row, '┘');
        }
    }

    public void showToast(String title, List<String> lines, long durationMs, TextColor.RGB borderColor) {
        this.activeToast = new ToastNotification(title, lines, durationMs, borderColor);
    }

    public void clearToast() {
        this.activeToast = null;
    }

    public ToastNotification getActiveToast() {
        if (activeToast != null && activeToast.isExpired()) {
            activeToast = null;
        }
        return activeToast;
    }

    // =========================================================================
    // Keyboard Navigation & Traversal
    // =========================================================================

    public void navigateUp(int terminalWidth) {
        if (selectedNode == null || selectedNode == getZoomedNode()) {
            return;
        }
        StackFrameNode parent = selectedNode.getParent();
        if (parent != null) {
            selectedNode = parent;
            updateCoordinatesFromSelectedNode(terminalWidth);
        }
    }

    public void navigateDown(int terminalWidth) {
        if (selectedNode == null) {
            selectedNode = getZoomedNode();
            selectedDepth = 0;
            selectedIndex = 0;
            return;
        }
        Collection<StackFrameNode> children = selectedNode.getChildren();
        if (!children.isEmpty()) {
            List<StackFrameNode> sorted = new ArrayList<>(children);
            sorted.sort(Comparator.comparingLong(StackFrameNode::getTotalValue).reversed());
            selectedNode = sorted.get(0);
            updateCoordinatesFromSelectedNode(terminalWidth);
        }
    }

    public void navigateLeft(int terminalWidth) {
        Map<Integer, List<FrameBar>> layout = computeLayout(terminalWidth, 30);
        List<FrameBar> bars = layout.get(selectedDepth);
        if (bars != null && selectedIndex > 0) {
            selectedIndex--;
            selectedNode = bars.get(selectedIndex).getNode();
        }
    }

    public void navigateRight(int terminalWidth) {
        Map<Integer, List<FrameBar>> layout = computeLayout(terminalWidth, 30);
        List<FrameBar> bars = layout.get(selectedDepth);
        if (bars != null && selectedIndex < bars.size() - 1) {
            selectedIndex++;
            selectedNode = bars.get(selectedIndex).getNode();
        }
    }

    public void zoomIntoSelected() {
        if (selectedNode != null && selectedNode != getZoomedNode() && !selectedNode.getChildren().isEmpty()) {
            zoomHistory.push(getZoomedNode());
            setZoomedNode(selectedNode);
            showToast("[✓] ZOOMED IN", List.of(
                    "Frame: " + selectedNode.getName(),
                    "Depth: " + selectedNode.getDepth(),
                    "Press [Esc] to zoom out"
            ), 3000, new TextColor.RGB(0, 210, 255));
        }
    }

    public void zoomOut() {
        if (!zoomHistory.isEmpty()) {
            StackFrameNode previous = zoomHistory.pop();
            setZoomedNode(previous);
            showToast("[-] ZOOMED OUT", List.of(
                    "Current: " + (previous.isRoot() ? "root" : previous.getName())
            ), 2500, new TextColor.RGB(255, 180, 50));
        } else {
            resetZoom();
        }
    }

    public void resetZoom() {
        zoomHistory.clear();
        this.zoomedNode = rootNode;
        this.selectedNode = rootNode;
        this.selectedDepth = 0;
        this.selectedIndex = 0;
        showToast("[↺] ZOOM RESET", List.of(
                "Returned to root call stack"
        ), 2500, new TextColor.RGB(255, 180, 50));
    }

    private void updateCoordinatesFromSelectedNode(int terminalWidth) {
        Map<Integer, List<FrameBar>> layout = computeLayout(terminalWidth, 30);
        for (Map.Entry<Integer, List<FrameBar>> entry : layout.entrySet()) {
            List<FrameBar> bars = entry.getValue();
            for (int i = 0; i < bars.size(); i++) {
                if (bars.get(i).getNode() == selectedNode) {
                    selectedDepth = entry.getKey();
                    selectedIndex = i;
                    return;
                }
            }
        }
    }

    // =========================================================================
    // Standalone SVG & HTML Export
    // =========================================================================

    /**
     * Exports the current frame hierarchy to a standalone vector SVG flame graph file.
     *
     * @param outputPath Path to output SVG file
     * @param title      Chart title
     * @throws IOException on I/O error
     */
    public void exportSvg(Path outputPath, String title) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        String svgContent = generateSvg(title);
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, svgContent);
        log.debug("Flame graph exported to SVG: {}", outputPath);
    }

    /**
     * Generates a self-contained, interactive vector SVG string.
     */
    public String generateSvg(String title) {
        if (title == null || title.isBlank()) {
            title = "Helix Profile Flame Graph (" + metricType.getDisplayName() + ")";
        }

        StackFrameNode effectiveRoot = getZoomedNode();
        if (effectiveRoot == null) {
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"100\"><text x=\"20\" y=\"40\">No profile samples</text></svg>";
        }

        int svgWidth = 1200;
        int rowHeight = 24;
        Map<Integer, List<FrameBar>> layout = computeLayout(svgWidth - 40, 30);
        int totalRows = Math.max(3, layout.size());
        int svgHeight = 80 + (totalRows * (rowHeight + 2));

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(svgWidth)
                .append("\" height=\"").append(svgHeight).append("\" viewBox=\"0 0 ")
                .append(svgWidth).append(" ").append(svgHeight).append("\">\n")
                .append("<style>\n")
                .append("  rect { stroke: #2a2a2a; stroke-width: 0.5; rx: 2; ry: 2; cursor: pointer; transition: opacity 0.15s; }\n")
                .append("  rect:hover { opacity: 0.85; stroke: #fff; stroke-width: 1.5; }\n")
                .append("  text { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace; font-size: 11px; fill: #111; pointer-events: none; }\n")
                .append("  .title { font-size: 16px; font-weight: bold; fill: #e0e0e0; }\n")
                .append("  .subtitle { font-size: 12px; fill: #999; }\n")
                .append("</style>\n")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#1e1e24\" />\n")
                .append("<text class=\"title\" x=\"20\" y=\"32\">").append(escapeXml(title)).append("</text>\n")
                .append("<text class=\"subtitle\" x=\"20\" y=\"52\">Total Samples: ")
                .append(effectiveRoot.getTotalValue()).append(" ").append(metricType.getUnit())
                .append(" | Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("</text>\n");

        long rootTotal = effectiveRoot.getTotalValue() > 0 ? effectiveRoot.getTotalValue() : 1;

        for (Map.Entry<Integer, List<FrameBar>> entry : layout.entrySet()) {
            int depth = entry.getKey();
            int y = 70 + (depth * (rowHeight + 2));
            List<FrameBar> bars = entry.getValue();

            for (FrameBar bar : bars) {
                int x = 20 + bar.getStartCol();
                int w = Math.max(2, bar.getWidth());
                TextColor.RGB bg = theme.getFrameBackgroundColor(bar.getNode().getFrameName(), depth, metricType);
                String fillHex = String.format("#%02x%02x%02x", bg.getRed(), bg.getGreen(), bg.getBlue());
                TextColor fg = FlameGraphTheme.getContrastingTextColor(bg);
                String textFill = (fg instanceof TextColor.RGB r && r.getRed() > 100) ? "#ffffff" : "#111111";

                double pct = (bar.getNode().getTotalValue() * 100.0 / rootTotal);
                String tooltip = String.format("%s (%d %s, %.1f%%)",
                        bar.getNode().getFrameName(), bar.getNode().getTotalValue(), metricType.getUnit(), pct);

                svg.append("<g>\n")
                        .append("  <title>").append(escapeXml(tooltip)).append("</title>\n")
                        .append("  <rect x=\"").append(x).append("\" y=\"").append(y)
                        .append("\" width=\"").append(w).append("\" height=\"").append(rowHeight)
                        .append("\" fill=\"").append(fillHex).append("\" />\n");

                if (w >= 30) {
                    String label = formatBarLabel(bar.getNode().getFrameName(), bar.getNode().getTotalValue(), rootTotal, w / 7);
                    svg.append("  <text x=\"").append(x + 5).append("\" y=\"").append(y + 16)
                            .append("\" fill=\"").append(textFill).append("\">")
                            .append(escapeXml(label.trim())).append("</text>\n");
                }
                svg.append("</g>\n");
            }
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    /**
     * Exports the current frame hierarchy to a standalone interactive HTML flame graph file.
     *
     * @param outputPath Path to output HTML file
     * @param title      Chart title
     * @throws IOException on I/O error
     */
    public void exportHtml(Path outputPath, String title) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        StackFrameNode effectiveRoot = getZoomedNode();
        if (effectiveRoot == null) {
            throw new IllegalStateException("Cannot export empty flame graph");
        }

        // Generate folded traces from current subtree
        StringBuilder folded = new StringBuilder();
        exportSubtreeFolded(effectiveRoot, new ArrayList<>(), folded);

        FlameGraphGenerator generator = new FlameGraphGenerator();
        generator.generateHtmlFlameGraph(folded.toString(), outputPath, title);
    }

    /**
     * Exports both SVG and HTML flame graph snapshots with timestamped filenames.
     *
     * @param outputDirectory Directory to write files
     * @param fileBaseName    Base name prefix
     * @return Array of [svgPath, htmlPath]
     * @throws IOException on I/O error
     */
    public Path[] exportBoth(Path outputDirectory, String fileBaseName) throws IOException {
        if (outputDirectory == null) {
            outputDirectory = Path.of("target", "flamegraphs");
        }
        Files.createDirectories(outputDirectory);

        String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
        String base = (fileBaseName != null && !fileBaseName.isBlank()) ? fileBaseName : "helix-flamegraph";

        Path svgPath = outputDirectory.resolve(base + "-" + timestamp + ".svg");
        Path htmlPath = outputDirectory.resolve(base + "-" + timestamp + ".html");

        String title = "Helix Flame Graph - " + metricType.getDisplayName();
        exportSvg(svgPath, title);
        exportHtml(htmlPath, title);

        return new Path[]{svgPath, htmlPath};
    }

    private void exportSubtreeFolded(StackFrameNode node, List<String> callStack, StringBuilder out) {
        if (!node.isRoot()) {
            callStack.add(node.getFrameName());
        }

        if (node.getSelfValue() > 0) {
            if (!callStack.isEmpty()) {
                out.append(String.join(";", callStack)).append(' ').append(node.getSelfValue()).append('\n');
            } else {
                out.append("root").append(' ').append(node.getSelfValue()).append('\n');
            }
        }

        for (StackFrameNode child : node.getChildren()) {
            exportSubtreeFolded(child, callStack, out);
        }

        if (!node.isRoot()) {
            callStack.remove(callStack.size() - 1);
        }
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
