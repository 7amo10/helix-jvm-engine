package com.helix.cli.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * High-performance terminal utility for rendering cleanly aligned ASCII tables.
 */
public class AsciiTableRenderer {

    /**
     * Renders a key-value map as an ASCII table.
     *
     * @param title table title header (optional)
     * @param data  key-value mapping of metrics
     * @return formatted ASCII table string
     */
    public static String renderKeyValueTable(String title, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        List<String> headers = List.of("Metric", "Value");
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            rows.add(List.of(entry.getKey(), String.valueOf(entry.getValue())));
        }

        return renderTable(title, headers, rows);
    }

    /**
     * Renders headers and rows as an ASCII table with column auto-sizing and optional title.
     *
     * @param title   optional header title spanning the full table width
     * @param headers column header labels
     * @param rows    row values
     * @return formatted ASCII table string
     */
    public static String renderTable(String title, List<String> headers, List<List<String>> rows) {
        int colCount = headers.size();
        int[] colWidths = new int[colCount];

        for (int i = 0; i < colCount; i++) {
            colWidths[i] = headers.get(i).length();
        }

        for (List<String> row : rows) {
            for (int i = 0; i < Math.min(row.size(), colCount); i++) {
                String val = row.get(i) != null ? row.get(i) : "";
                if (val.length() > colWidths[i]) {
                    colWidths[i] = val.length();
                }
            }
        }

        // Add padding
        for (int i = 0; i < colCount; i++) {
            colWidths[i] += 2;
        }

        // Calculate total table width
        int totalWidth = 1; // starting '+'
        for (int w : colWidths) {
            totalWidth += w + 1; // width + separator '+'
        }

        StringBuilder sb = new StringBuilder();

        // Top border
        String border = buildBorder(colWidths);

        if (title != null && !title.isBlank()) {
            sb.append("+").append("-".repeat(Math.max(0, totalWidth - 2))).append("+\n");
            int padding = Math.max(0, totalWidth - 2 - title.length());
            int leftPad = padding / 2;
            int rightPad = padding - leftPad;
            sb.append("|").append(" ".repeat(leftPad)).append(title).append(" ".repeat(rightPad)).append("|\n");
        }

        sb.append(border).append("\n");

        // Header row
        sb.append(buildRow(headers, colWidths)).append("\n");
        sb.append(border).append("\n");

        // Data rows
        for (List<String> row : rows) {
            sb.append(buildRow(row, colWidths)).append("\n");
        }

        // Bottom border
        sb.append(border).append("\n");

        return sb.toString();
    }

    private static String buildBorder(int[] colWidths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : colWidths) {
            sb.append("-".repeat(w)).append("+");
        }
        return sb.toString();
    }

    private static String buildRow(List<String> cells, int[] colWidths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < colWidths.length; i++) {
            String cellVal = (i < cells.size() && cells.get(i) != null) ? cells.get(i) : "";
            int width = colWidths[i];
            int pad = width - cellVal.length() - 1;
            sb.append(" ").append(cellVal).append(" ".repeat(Math.max(0, pad))).append("|");
        }
        return sb.toString();
    }
}
