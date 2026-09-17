package com.helix.profiler.flamegraph;

import com.googlecode.lanterna.TextColor;

import java.util.Objects;

/**
 * Color and theme management for flame graph visualization in terminal TUIs and ANSI consoles.
 * <p>
 * Implements perceptual luminance-based contrast calculation to guarantee 100% legibility
 * on diverse terminal themes (dark, light, solarized, high contrast) by always selecting
 * an explicit, high-contrast foreground color for any background color.
 */
public class FlameGraphTheme {

    public enum Mode {
        DARK,
        LIGHT,
        HIGH_CONTRAST
    }

    private final Mode mode;

    public FlameGraphTheme() {
        this(Mode.DARK);
    }

    public FlameGraphTheme(Mode mode) {
        this.mode = Objects.requireNonNullElse(mode, Mode.DARK);
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Returns an explicit high-contrast foreground color (dark or bright)
     * based on the perceptual luminance of the background RGB.
     *
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return High-contrast TextColor (black/dark for light bg, white/bright for dark bg)
     */
    public static TextColor getContrastingTextColor(int r, int g, int b) {
        // Standard Rec. 601 perceptual luminance formula
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        if (luminance > 140.0) {
            // Light background -> Deep dark text for maximum contrast
            return new TextColor.RGB(15, 15, 15);
        } else {
            // Dark background -> Crisp bright white text
            return new TextColor.RGB(250, 250, 250);
        }
    }

    /**
     * Overload accepting Lanterna TextColor.
     */
    public static TextColor getContrastingTextColor(TextColor backgroundColor) {
        if (backgroundColor instanceof TextColor.RGB rgb) {
            return getContrastingTextColor(rgb.getRed(), rgb.getGreen(), rgb.getBlue());
        }
        if (backgroundColor instanceof TextColor.ANSI ansi) {
            return switch (ansi) {
                case YELLOW, YELLOW_BRIGHT, WHITE, WHITE_BRIGHT, CYAN_BRIGHT, GREEN_BRIGHT ->
                        new TextColor.RGB(15, 15, 15);
                default -> new TextColor.RGB(250, 250, 250);
            };
        }
        return new TextColor.RGB(250, 250, 250);
    }

    /**
     * Computes a deterministic frame background color based on frame name, depth, and metric.
     *
     * @param frameName  Fully qualified or short frame name
     * @param depth      Call stack depth level
     * @param metricType CPU_TIME or ALLOCATION_BYTES
     * @return Background TextColor.RGB
     */
    public TextColor.RGB getFrameBackgroundColor(String frameName, int depth, MetricType metricType) {
        if (frameName == null || frameName.isBlank()) {
            return new TextColor.RGB(80, 80, 80);
        }

        int hash = Math.abs(frameName.hashCode());

        if (metricType == MetricType.ALLOCATION_BYTES) {
            // Cool Memory / Allocation Palette (Cyan, Teal, Emerald, Sea Green, Aqua)
            // Hue in green/cyan/blue range: 140 - 200 deg
            int r = 20 + (hash % 45);
            int g = 110 + ((hash / 45 + depth * 15) % 110);
            int b = 130 + ((hash / 90 + depth * 10) % 115);
            return new TextColor.RGB(r, Math.min(240, g), Math.min(250, b));
        } else {
            // Warm CPU Flame Palette (Crimson, Coral, Orange, Amber, Warm Yellow)
            // Red stays high (190 - 250), Green varies (40 - 180), Blue stays low (20 - 60)
            int r = 195 + (hash % 55);
            int g = 50 + ((hash / 55 + depth * 20) % 135);
            int b = 25 + (hash % 35);
            return new TextColor.RGB(Math.min(255, r), Math.min(220, g), b);
        }
    }

    /**
     * Frame background color for the currently selected/active cursor frame.
     */
    public TextColor.RGB getSelectedFrameBackgroundColor() {
        // Bright Violet / Magenta for unmistakable focus across all themes
        return new TextColor.RGB(205, 50, 215);
    }

    /**
     * Border color for frame boxes.
     */
    public TextColor.RGB getBorderColor() {
        if (mode == Mode.LIGHT) {
            return new TextColor.RGB(60, 60, 60);
        }
        return new TextColor.RGB(170, 170, 170);
    }

    /**
     * Canvas background color for the flame graph viewport.
     */
    public TextColor.RGB getCanvasBackgroundColor() {
        if (mode == Mode.LIGHT) {
            return new TextColor.RGB(245, 245, 247);
        }
        return new TextColor.RGB(22, 24, 29);
    }

    /**
     * Generates an ANSI 24-bit escape sequence for background color.
     */
    public static String ansiBg(int r, int g, int b) {
        return String.format("\033[48;2;%d;%d;%dm", r, g, b);
    }

    /**
     * Generates an ANSI 24-bit escape sequence for foreground color.
     */
    public static String ansiFg(int r, int g, int b) {
        return String.format("\033[38;2;%d;%d;%dm", r, g, b);
    }

    /**
     * ANSI reset code.
     */
    public static String ansiReset() {
        return "\033[0m";
    }

    /**
     * ANSI bold code.
     */
    public static String ansiBold() {
        return "\033[1m";
    }
}
