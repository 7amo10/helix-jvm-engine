package com.helix.profiler.gc;

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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Main GC log analyzer that parses unified GC logs (-Xlog:gc*), tracks GC statistics,
 * safepoint pauses, and notifies listeners of GC events.
 */
public class GcLogAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GcLogAnalyzer.class);

    private final GcEventParser eventParser;
    private final SafepointAnalyzer safepointAnalyzer;
    private final List<GcEvent> events;
    private final List<SafepointAnalyzer.SafepointEvent> safepointEvents;
    private final List<Consumer<GcEvent>> listeners;

    public GcLogAnalyzer() {
        this.eventParser = new GcEventParser();
        this.safepointAnalyzer = new SafepointAnalyzer();
        this.events = new CopyOnWriteArrayList<>();
        this.safepointEvents = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public void addListener(Consumer<GcEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    public void removeListener(Consumer<GcEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * Parses a string containing multi-line GC and safepoint logs.
     */
    public List<GcEvent> parseLog(String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return Collections.emptyList();
        }
        return parseReader(new StringReader(logContent));
    }

    /**
     * Parses GC log entries from an InputStream.
     */
    public List<GcEvent> parseStream(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        return parseReader(new InputStreamReader(inputStream));
    }

    /**
     * Parses GC log entries from a file path.
     */
    public List<GcEvent> parseFile(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            return parseReader(reader);
        }
    }

    private List<GcEvent> parseReader(Reader reader) {
        List<GcEvent> newEvents = new ArrayList<>();
        try (BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                GcEvent gcEvent = eventParser.parseLine(line);
                if (gcEvent != null) {
                    events.add(gcEvent);
                    newEvents.add(gcEvent);
                    notifyListeners(gcEvent);
                } else {
                    SafepointAnalyzer.SafepointEvent spEvent = safepointAnalyzer.parseLine(line);
                    if (spEvent != null) {
                        safepointEvents.add(spEvent);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error reading GC log content: {}", e.getMessage(), e);
        }
        return Collections.unmodifiableList(newEvents);
    }

    private void notifyListeners(GcEvent gcEvent) {
        for (Consumer<GcEvent> listener : listeners) {
            try {
                listener.accept(gcEvent);
            } catch (Exception e) {
                log.error("Error in GC event listener: {}", e.getMessage(), e);
            }
        }
    }

    public List<GcEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public List<SafepointAnalyzer.SafepointEvent> getSafepointEvents() {
        return Collections.unmodifiableList(new ArrayList<>(safepointEvents));
    }

    public GcStatistics getStatistics() {
        return new GcStatistics(events);
    }

    public void clear() {
        events.clear();
        safepointEvents.clear();
    }
}
