package com.helix.profiler.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for JDK Unified GC log lines (-Xlog:gc*).
 */
public class GcEventParser {

    private static final Logger log = LoggerFactory.getLogger(GcEventParser.class);

    /*
     * Pattern matching HotSpot Unified GC log output lines:
     *   [0.123s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 15M->4M(256M) 2.456ms
     *   [1.456s][info][gc] GC(1) Pause Full (System.gc()) 45M->12M(256M) 15.340ms
     */
    private static final Pattern GC_LOG_PATTERN = Pattern.compile(
            "^\\[.*?(?:(\\d+(?:\\.\\d+)?)s)?.*?\\]\\[info\\s*\\]\\[gc\\s*\\]\\s+GC\\((\\d+)\\)\\s+(.+?)\\s+([0-9.]+[A-Za-z]+)->([0-9.]+[A-Za-z]+)\\(([0-9.]+[A-Za-z]+)\\)\\s+([0-9.]+)ms$"
    );

    /**
     * Parses a single GC log line into a GcEvent.
     */
    public GcEvent parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = GC_LOG_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String timeStr = matcher.group(1);
            long timestampMs = timeStr != null ? (long) (Double.parseDouble(timeStr) * 1000.0) : 0L;

            int gcId = Integer.parseInt(matcher.group(2));
            String fullTypeAndCause = matcher.group(3).trim();

            String type = fullTypeAndCause;
            String cause = "Normal";

            // Extract trailing cause e.g. "Pause Young (Normal) (G1 Evacuation Pause)", "Pause Full (System.gc())"
            Pattern causePattern = Pattern.compile("^(.*?)\\s*\\(([^\\(\\)]+(?:\\(\\))?)\\)$");
            Matcher causeMatcher = causePattern.matcher(fullTypeAndCause);
            if (causeMatcher.matches()) {
                type = causeMatcher.group(1).trim();
                cause = causeMatcher.group(2).trim();
            }

            long heapBeforeKb = parseMemoryToKb(matcher.group(4));
            long heapAfterKb = parseMemoryToKb(matcher.group(5));
            long heapTotalKb = parseMemoryToKb(matcher.group(6));
            double pauseTimeMs = Double.parseDouble(matcher.group(7));

            boolean isFullGc = fullTypeAndCause.toLowerCase().contains("full") || cause.toLowerCase().contains("full");

            return new GcEvent(
                    timestampMs,
                    gcId,
                    type,
                    cause,
                    heapBeforeKb,
                    heapAfterKb,
                    heapTotalKb,
                    pauseTimeMs,
                    isFullGc,
                    Instant.now()
            );
        } catch (Exception e) {
            log.debug("Failed to parse GC log line: {}", line, e);
            return null;
        }
    }

    /**
     * Converts memory strings like "15M", "4096K", "256M", "1G" into Kilobytes (KB).
     */
    public static long parseMemoryToKb(String memoryStr) {
        if (memoryStr == null || memoryStr.isBlank()) {
            return 0L;
        }

        String clean = memoryStr.trim().toUpperCase();
        char unit = clean.charAt(clean.length() - 1);
        double val;

        if (Character.isDigit(unit)) {
            val = Double.parseDouble(clean);
            return (long) (val / 1024.0);
        }

        val = Double.parseDouble(clean.substring(0, clean.length() - 1));
        return switch (unit) {
            case 'B' -> (long) (val / 1024.0);
            case 'K' -> (long) val;
            case 'M' -> (long) (val * 1024.0);
            case 'G' -> (long) (val * 1024.0 * 1024.0);
            default -> (long) val;
        };
    }
}
