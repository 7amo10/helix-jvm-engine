package com.helix.profiler.flamegraph;

import com.googlecode.lanterna.TextColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlameGraphThemeTest {

    @Test
    @DisplayName("Theme guarantees high contrast dark text on light backgrounds")
    void shouldSelectDarkTextOnLightBackground() {
        // Pure White
        TextColor fg1 = FlameGraphTheme.getContrastingTextColor(255, 255, 255);
        assertTrue(fg1 instanceof TextColor.RGB);
        TextColor.RGB rgb1 = (TextColor.RGB) fg1;
        assertEquals(15, rgb1.getRed());
        assertEquals(15, rgb1.getGreen());
        assertEquals(15, rgb1.getBlue());

        // Bright Yellow
        TextColor fg2 = FlameGraphTheme.getContrastingTextColor(255, 255, 0);
        TextColor.RGB rgb2 = (TextColor.RGB) fg2;
        assertEquals(15, rgb2.getRed());

        // Light Gray / Cream
        TextColor fg3 = FlameGraphTheme.getContrastingTextColor(220, 220, 220);
        TextColor.RGB rgb3 = (TextColor.RGB) fg3;
        assertEquals(15, rgb3.getRed());
    }

    @Test
    @DisplayName("Theme guarantees high contrast bright text on dark backgrounds")
    void shouldSelectBrightTextOnDarkBackground() {
        // Pitch Black
        TextColor fg1 = FlameGraphTheme.getContrastingTextColor(0, 0, 0);
        TextColor.RGB rgb1 = (TextColor.RGB) fg1;
        assertEquals(250, rgb1.getRed());
        assertEquals(250, rgb1.getGreen());
        assertEquals(250, rgb1.getBlue());

        // Dark Blue / Navy
        TextColor fg2 = FlameGraphTheme.getContrastingTextColor(20, 30, 80);
        TextColor.RGB rgb2 = (TextColor.RGB) fg2;
        assertEquals(250, rgb2.getRed());

        // Deep Crimson Red
        TextColor fg3 = FlameGraphTheme.getContrastingTextColor(120, 20, 20);
        TextColor.RGB rgb3 = (TextColor.RGB) fg3;
        assertEquals(250, rgb3.getRed());
    }

    @Test
    @DisplayName("Theme provides distinct warm flame palette for CPU and cool ocean palette for Allocation")
    void shouldProvideDistinctPalettesPerMetric() {
        FlameGraphTheme theme = new FlameGraphTheme();

        TextColor.RGB cpuColor = theme.getFrameBackgroundColor("com.helix.core.Eval", 2, MetricType.CPU_TIME);
        TextColor.RGB allocColor = theme.getFrameBackgroundColor("com.helix.core.Eval", 2, MetricType.ALLOCATION_BYTES);

        assertNotNull(cpuColor);
        assertNotNull(allocColor);

        // CPU flame colors emphasize Red
        assertTrue(cpuColor.getRed() >= 180, "CPU color red component should be prominent");

        // Allocation colors emphasize Green / Blue
        assertTrue(allocColor.getGreen() >= 100 || allocColor.getBlue() >= 100, "Allocation color green/blue should be prominent");
        assertNotEquals(cpuColor, allocColor, "CPU and Allocation colors must differ");
    }

    @Test
    @DisplayName("Deterministic color generation produces consistent results for identical frame names")
    void shouldProduceConsistentColorsForSameFrame() {
        FlameGraphTheme theme = new FlameGraphTheme();

        TextColor.RGB c1 = theme.getFrameBackgroundColor("com.helix.rule.Engine.run", 1, MetricType.CPU_TIME);
        TextColor.RGB c2 = theme.getFrameBackgroundColor("com.helix.rule.Engine.run", 1, MetricType.CPU_TIME);

        assertEquals(c1.getRed(), c2.getRed());
        assertEquals(c1.getGreen(), c2.getGreen());
        assertEquals(c1.getBlue(), c2.getBlue());
    }

    @Test
    @DisplayName("ANSI escape generation formats valid 24-bit sequences")
    void shouldGenerateValidAnsiSequences() {
        String bg = FlameGraphTheme.ansiBg(100, 150, 200);
        String fg = FlameGraphTheme.ansiFg(250, 250, 250);
        String reset = FlameGraphTheme.ansiReset();

        assertTrue(bg.startsWith("\033[48;2;100;150;200m"));
        assertTrue(fg.startsWith("\033[38;2;250;250;250m"));
        assertEquals("\033[0m", reset);
    }
}
