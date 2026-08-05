package com.helix.cli.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

/**
 * Formats CLI command output for text, json, and csv modes.
 */
public class OutputFormatter {

    private final String format;
    private final boolean quiet;
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public OutputFormatter(String format, boolean quiet) {
        this.format = format != null ? format.toLowerCase() : "text";
        this.quiet = quiet;
    }

    public String formatResult(String title, Map<String, Object> data) {
        if (quiet && !"json".equals(format) && !"csv".equals(format)) {
            return "";
        }

        return switch (format) {
            case "json" -> formatJson(data);
            case "csv" -> formatCsv(data);
            default -> formatText(title, data);
        };
    }

    private String formatJson(Map<String, Object> data) {
        return JsonFormatter.format(data);
    }

    private String formatCsv(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Key,Value\n");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sb.append('"').append(entry.getKey()).append("\",\"")
              .append(String.valueOf(entry.getValue()).replace("\"", "\"\"")).append("\"\n");
        }
        return sb.toString();
    }

    private String formatText(String title, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================\n");
        sb.append(" ").append(title).append("\n");
        sb.append("=========================================\n");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sb.append(String.format("%-25s : %s\n", entry.getKey(), entry.getValue()));
        }
        sb.append("-----------------------------------------\n");
        return sb.toString();
    }
}
