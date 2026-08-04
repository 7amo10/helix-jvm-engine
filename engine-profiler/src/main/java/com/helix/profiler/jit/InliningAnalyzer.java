package com.helix.profiler.jit;

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
 * Analyzer that parses HotSpot inlining logs (-XX:+PrintInlining) to inspect JIT method inlining decisions.
 */
public class InliningAnalyzer {

    /**
     * Record representing a single JIT inlining decision.
     */
    public record InliningDecision(
            int bytecodeIndex,
            String targetMethod,
            int bytecodeSize,
            boolean inlined,
            String reason
    ) {}

    // Example pattern: "@ 5   com.example.Foo::bar (10 bytes)   inline (hot)"
    // Example pattern: "@ 12  com.example.Foo::baz (45 bytes)   too big"
    private static final Pattern INLINING_PATTERN = Pattern.compile(
            "^\\s*@\\s*(\\d+)\\s+([^\\s]+)\\s+\\((\\d+)\\s+bytes\\)\\s+(.+)$"
    );

    /**
     * Parses an inlining log output string into structured InliningDecision objects.
     */
    public List<InliningDecision> parseInliningLog(String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return Collections.emptyList();
        }

        List<InliningDecision> decisions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(logContent))) {
            String line;
            while ((line = reader.readLine()) != null) {
                InliningDecision decision = parseLine(line);
                if (decision != null) {
                    decisions.add(decision);
                }
            }
        } catch (IOException e) {
            // Memory reader, unlikely to throw
        }
        return Collections.unmodifiableList(decisions);
    }

    /**
     * Parses a single line from a PrintInlining log output.
     */
    public InliningDecision parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = INLINING_PATTERN.matcher(line.trim());
        if (matcher.matches()) {
            int bci = Integer.parseInt(matcher.group(1));
            String method = matcher.group(2);
            int size = Integer.parseInt(matcher.group(3));
            String reason = matcher.group(4).trim();
            boolean inlined = reason.startsWith("inline") || reason.contains("inlined");

            return new InliningDecision(bci, method, size, inlined, reason);
        }
        return null;
    }
}
