package com.helix.profiler.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzer that parses JVM safepoint log lines (-Xlog:safepoint) to monitor safepoint pauses and latency.
 */
public class SafepointAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SafepointAnalyzer.class);

    /**
     * Record representing a single JVM safepoint event.
     */
    public record SafepointEvent(
            long timestampMs,
            String name,
            long timeSinceLastNs,
            long reachingSafepointNs,
            long atSafepointNs,
            long totalSafepointNs
    ) {

        public double getTotalSafepointMs() {
            return totalSafepointNs / 1_000_000.0;
        }

        public double getReachingSafepointMs() {
            return reachingSafepointNs / 1_000_000.0;
        }
    }

    /*
     * Pattern:
     *   [3.010s][info][safepoint] Safepoint "G1CollectForAllocation", Time since last: 1000200 ns, Reaching safepoint: 15000 ns, At safepoint: 2500000 ns, Total: 2515000 ns
     */
    private static final Pattern SAFEPOINT_PATTERN = Pattern.compile(
            "^\\[(?:[^\\]]*?(\\d+(?:\\.\\d+)?)s)?.*?\\]\\[info\\s*\\]\\[safepoint\\s*\\]\\s+Safepoint\\s+\"([^\"]+)\",\\s+Time since last:\\s+(\\d+)\\s+ns,\\s+Reaching safepoint:\\s+(\\d+)\\s+ns,\\s+At safepoint:\\s+(\\d+)\\s+ns,\\s+Total:\\s+(\\d+)\\s+ns$"
    );

    /**
     * Parses a multi-line safepoint log into a list of SafepointEvent objects.
     */
    public List<SafepointEvent> parseLog(String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return Collections.emptyList();
        }

        List<SafepointEvent> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(logContent))) {
            String line;
            while ((line = reader.readLine()) != null) {
                SafepointEvent event = parseLine(line);
                if (event != null) {
                    events.add(event);
                }
            }
        } catch (IOException e) {
            log.error("Error reading safepoint log content: {}", e.getMessage(), e);
        }
        return Collections.unmodifiableList(events);
    }

    /**
     * Parses a single safepoint log line.
     */
    public SafepointEvent parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = SAFEPOINT_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String timeStr = matcher.group(1);
            long timestampMs = timeStr != null ? (long) (Double.parseDouble(timeStr) * 1000.0) : 0L;
            String name = matcher.group(2);
            long timeSinceLast = Long.parseLong(matcher.group(3));
            long reachingNs = Long.parseLong(matcher.group(4));
            long atSafepointNs = Long.parseLong(matcher.group(5));
            long totalNs = Long.parseLong(matcher.group(6));

            return new SafepointEvent(timestampMs, name, timeSinceLast, reachingNs, atSafepointNs, totalNs);
        } catch (Exception e) {
            log.debug("Failed to parse safepoint line: {}", line, e);
            return null;
        }
    }
}
