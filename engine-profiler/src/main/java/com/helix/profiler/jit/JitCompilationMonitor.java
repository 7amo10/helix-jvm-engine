package com.helix.profiler.jit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monitor that parses HotSpot JIT compilation logs (-XX:+PrintCompilation)
 * and emits structured CompilationEvent objects.
 */
public class JitCompilationMonitor {

    private static final Logger log = LoggerFactory.getLogger(JitCompilationMonitor.class);

    /*
     * Sample PrintCompilation patterns:
     *   123  1234       4       com.helix.core.RuleCompiler::compile (45 bytes)
     *   180  1236    %  3       com.helix.core.RuleCompiler::loop @ 12 (200 bytes)
     *   200  1234       4       com.helix.core.RuleCompiler::compile (45 bytes)   made not entrant
     *   220  1237    ! s 3      com.helix.core.RuleCompiler::syncMethod (30 bytes)
     */
    private static final Pattern PRINT_COMPILATION_PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\s+(\\d+)\\s+([%s!nb\\s]*)\\s*([1-4])\\s+([^\\s]+(?:\\s+@[\\s\\d]+)?)\\s+\\((\\d+)\\s+bytes\\)(?:\\s+(.*))?$"
    );

    private final CompilationHistory history;
    private final TieredCompilationTracker tracker;
    private final List<Consumer<CompilationEvent>> listeners;
    private volatile boolean monitoring;

    public JitCompilationMonitor() {
        this(new CompilationHistory(), new TieredCompilationTracker());
    }

    public JitCompilationMonitor(CompilationHistory history, TieredCompilationTracker tracker) {
        this.history = Objects.requireNonNull(history, "history must not be null");
        this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
        this.listeners = new CopyOnWriteArrayList<>();
        this.monitoring = false;
    }

    public void addListener(Consumer<CompilationEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    public void removeListener(Consumer<CompilationEvent> listener) {
        listeners.remove(listener);
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    public void startMonitoring() {
        this.monitoring = true;
        log.info("JitCompilationMonitor started.");
    }

    public void stopMonitoring() {
        this.monitoring = false;
        log.info("JitCompilationMonitor stopped.");
    }

    /**
     * Parses a multi-line PrintCompilation log content.
     */
    public List<CompilationEvent> parseLog(String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return List.of();
        }
        return parseReader(new StringReader(logContent));
    }

    /**
     * Parses PrintCompilation logs from an InputStream.
     */
    public List<CompilationEvent> parseStream(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        return parseReader(new InputStreamReader(inputStream));
    }

    /**
     * Parses PrintCompilation logs from a file path.
     */
    public List<CompilationEvent> parseFile(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            return parseReader(br);
        }
    }

    /**
     * Internal helper to parse lines from a Reader.
     */
    private List<CompilationEvent> parseReader(Reader reader) {
        List<CompilationEvent> parsedEvents = new ArrayList<>();
        try (BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                CompilationEvent event = parseLine(line);
                if (event != null) {
                    parsedEvents.add(event);
                    processEvent(event);
                }
            }
        } catch (IOException e) {
            log.error("Error reading PrintCompilation log: {}", e.getMessage(), e);
        }
        return parsedEvents;
    }

    /**
     * Parses a single line of PrintCompilation output.
     */
    public CompilationEvent parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = PRINT_COMPILATION_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            long timestampMs = Long.parseLong(matcher.group(1));
            int compileId = Integer.parseInt(matcher.group(2));
            String flags = matcher.group(3) != null ? matcher.group(3) : "";
            int tier = Integer.parseInt(matcher.group(4));
            String method = matcher.group(5);
            int bytecodeSize = Integer.parseInt(matcher.group(6));
            String status = matcher.group(7) != null ? matcher.group(7).trim() : "NORMAL";

            boolean isOsr = flags.contains("%");
            boolean isSynchronized = flags.contains("s");
            boolean isExceptionHolder = flags.contains("!");

            return new CompilationEvent(
                    timestampMs,
                    compileId,
                    tier,
                    method,
                    bytecodeSize,
                    isOsr,
                    isSynchronized,
                    isExceptionHolder,
                    status.isEmpty() ? "NORMAL" : status,
                    java.time.Instant.now()
            );
        } catch (NumberFormatException e) {
            log.debug("Failed to parse PrintCompilation line: {}", line, e);
            return null;
        }
    }

    private void processEvent(CompilationEvent event) {
        history.addEvent(event);
        tracker.recordEvent(event);
        for (Consumer<CompilationEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error invoking compilation event listener: {}", e.getMessage(), e);
            }
        }
    }

    public CompilationHistory getHistory() {
        return history;
    }

    public TieredCompilationTracker getTracker() {
        return tracker;
    }

    public CompilationStats getStats() {
        return new CompilationStats(history.getEvents());
    }
}
