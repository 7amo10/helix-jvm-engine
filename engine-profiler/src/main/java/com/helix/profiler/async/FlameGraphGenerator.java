package com.helix.profiler.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generator that converts collapsed stack samples into interactive HTML flame graphs.
 */
public class FlameGraphGenerator {

    private static final Logger log = LoggerFactory.getLogger(FlameGraphGenerator.class);

    /**
     * Converts collapsed stack trace data into a self-contained HTML Flame Graph.
     *
     * @param collapsedData Stack trace samples in format: "frame1;frame2;method 42"
     * @param title         Flame graph title
     * @return Full HTML string
     */
    public String generateHtmlFlameGraph(String collapsedData, String title) {
        if (title == null || title.isBlank()) {
            title = "Helix Profile Flame Graph";
        }

        List<Map.Entry<String, Long>> samples = parseCollapsedData(collapsedData);
        long totalSamples = samples.stream().mapToLong(Map.Entry::getValue).sum();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("  <meta charset=\"UTF-8\">\n")
                .append("  <title>").append(escapeHtml(title)).append("</title>\n")
                .append("  <style>\n")
                .append("    body { font-family: monospace; background: #1e1e1e; color: #d4d4d4; margin: 20px; }\n")
                .append("    h2 { color: #569cd6; border-bottom: 1px solid #333; padding-bottom: 8px; }\n")
                .append("    .summary { font-size: 14px; margin-bottom: 15px; color: #b5cea8; }\n")
                .append("    .flame-row { display: flex; align-items: center; margin-bottom: 4px; border-radius: 3px; overflow: hidden; }\n")
                .append("    .flame-bar { background: linear-gradient(90deg, #ce9178, #d7ba7d); color: #1e1e1e; padding: 6px 10px; ")
                .append("font-weight: bold; font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n")
                .append("    .flame-count { margin-left: 10px; font-size: 12px; color: #9cdcfe; }\n")
                .append("  </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("  <h2>").append(escapeHtml(title)).append("</h2>\n")
                .append("  <div class=\"summary\">Total Samples Collected: ").append(totalSamples).append("</div>\n");

        for (Map.Entry<String, Long> entry : samples) {
            double widthPct = totalSamples > 0 ? (entry.getValue() * 100.0 / totalSamples) : 0.0;
            widthPct = Math.max(5.0, widthPct); // min width for visibility

            html.append("  <div class=\"flame-row\">\n")
                    .append("    <div class=\"flame-bar\" style=\"width: ").append(String.format("%.2f", widthPct)).append("%;\">")
                    .append(escapeHtml(entry.getKey()))
                    .append("</div>\n")
                    .append("    <span class=\"flame-count\">").append(entry.getValue()).append(" samples</span>\n")
                    .append("  </div>\n");
        }

        html.append("</body>\n</html>");
        return html.toString();
    }

    /**
     * Generates an HTML flame graph file from collapsed input file.
     */
    public void generateHtmlFlameGraph(Path collapsedFile, Path outputFile, String title) throws IOException {
        Objects.requireNonNull(collapsedFile, "collapsedFile must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");

        String collapsedContent = Files.readString(collapsedFile);
        String html = generateHtmlFlameGraph(collapsedContent, title);

        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }
        Files.writeString(outputFile, html);
        log.info("Generated Flame Graph HTML at: {}", outputFile);
    }

    /**
     * Helper method to parse collapsed stack format lines ("a;b;c 100").
     */
    public List<Map.Entry<String, Long>> parseCollapsedData(String collapsedData) {
        if (collapsedData == null || collapsedData.isBlank()) {
            return List.of();
        }

        List<Map.Entry<String, Long>> result = new ArrayList<>();
        String[] lines = collapsedData.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int lastSpace = line.lastIndexOf(' ');
            if (lastSpace > 0 && lastSpace < line.length() - 1) {
                String stack = line.substring(0, lastSpace).trim();
                try {
                    long count = Long.parseLong(line.substring(lastSpace + 1).trim());
                    result.add(new AbstractMap.SimpleEntry<>(stack, count));
                } catch (NumberFormatException e) {
                    log.debug("Skipping invalid sample line: {}", line);
                }
            }
        }
        return result;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
