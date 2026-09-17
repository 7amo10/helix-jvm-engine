package com.helix.profiler.flamegraph;

import com.helix.api.profiler.ExecutionEvent;
import com.helix.api.profiler.ProfileEvent;
import com.helix.api.profiler.ProfileEventListener;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory folded stack trace aggregation engine that transforms execution and allocation samples
 * from internal profilers, thread dumps, and JDK Flight Recorder (JFR) into hierarchical flame graph call trees.
 * <p>
 * Supports dynamic switching between CPU execution samples ({@link MetricType#CPU_TIME}) and
 * Heap memory allocation bytes ({@link MetricType#ALLOCATION_BYTES}).
 * Outputs folded stack data strictly matching Brendan Gregg's FlameGraph specification.
 */
public class FlameGraphAggregator implements ProfileEventListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FlameGraphAggregator.class);

    private final Map<MetricType, StackFrameNode> roots = new ConcurrentHashMap<>();
    private volatile MetricType activeMetric = MetricType.CPU_TIME;

    private ScheduledExecutorService samplingExecutor;
    private final AtomicBoolean samplingActive = new AtomicBoolean(false);

    public FlameGraphAggregator() {
        for (MetricType type : MetricType.values()) {
            roots.put(type, new StackFrameNode("root", 0, null));
        }
    }

    /**
     * Dynamically sets the active metric dimension.
     */
    public void setMetric(MetricType metricType) {
        this.activeMetric = Objects.requireNonNull(metricType, "metricType cannot be null");
    }

    public void setActiveMetric(MetricType metricType) {
        setMetric(metricType);
    }

    /**
     * Gets the currently active metric dimension.
     */
    public MetricType getMetric() {
        return activeMetric;
    }

    /**
     * Retrieves the root prefix trie node for the active metric.
     */
    public StackFrameNode getRootNode() {
        return getRootNode(this.activeMetric);
    }

    /**
     * Retrieves the root prefix trie node for the specified metric.
     */
    public StackFrameNode getRootNode(MetricType metricType) {
        return roots.computeIfAbsent(metricType, m -> new StackFrameNode("root", 0, null));
    }

    // =========================================================================
    // Sample Ingestion
    // =========================================================================

    /**
     * Adds a stack trace sample with the specified value to the active metric.
     *
     * @param frames ordered call stack frames (root caller at index 0, leaf callee at last index)
     * @param value  weight/count or allocation bytes
     */
    public void addSample(List<String> frames, long value) {
        addSample(this.activeMetric, frames, value);
    }

    /**
     * Adds a single count stack trace sample to the active metric.
     */
    public void addSample(List<String> frames) {
        addSample(this.activeMetric, frames, 1L);
    }

    /**
     * Adds a stack trace sample with the specified value to a target metric.
     *
     * @param metric target metric dimension
     * @param frames ordered call stack frames (root caller at index 0, leaf callee at last index)
     * @param value  weight/count or allocation bytes
     */
    public void addSample(MetricType metric, List<String> frames, long value) {
        if (frames == null || frames.isEmpty() || value <= 0) {
            return;
        }

        StackFrameNode current = getRootNode(metric);
        current.incrementTotalValue(value);

        for (String rawFrame : frames) {
            if (rawFrame == null || rawFrame.isBlank()) continue;
            String sanitized = sanitizeFrame(rawFrame);
            current = current.getOrCreateChild(sanitized);
            current.incrementTotalValue(value);
        }

        // Terminal frame records the self value
        current.incrementSelfValue(value);
    }

    /**
     * Ingests a JVM {@link StackTraceElement} array into the active metric.
     */
    public void addSample(StackTraceElement[] stackTrace, long value) {
        addSample(this.activeMetric, stackTrace, value);
    }

    /**
     * Ingests a JVM {@link StackTraceElement} array into the specified metric.
     */
    public void addSample(MetricType metric, StackTraceElement[] stackTrace, long value) {
        if (stackTrace == null || stackTrace.length == 0 || value <= 0) {
            return;
        }

        List<String> frames = new ArrayList<>(stackTrace.length);
        // StackTraceElement[] is leaf-first (index 0 is current method).
        // Flame graphs require root-first (index 0 is entry method like main/Thread.run).
        for (int i = stackTrace.length - 1; i >= 0; i--) {
            StackTraceElement elem = stackTrace[i];
            String className = elem.getClassName();
            String methodName = elem.getMethodName();
            if (!isIgnoredInternalFrame(className, methodName)) {
                frames.add(className + "." + methodName);
            }
        }

        if (!frames.isEmpty()) {
            addSample(metric, frames, value);
        }
    }

    /**
     * Ingests a thread dump snapshot (e.g. from Thread.getAllStackTraces()).
     */
    public void ingestThreadDump(Map<Thread, StackTraceElement[]> traces) {
        ingestThreadDump(this.activeMetric, traces);
    }

    /**
     * Ingests a thread dump snapshot into the specified metric.
     */
    public void ingestThreadDump(MetricType metric, Map<Thread, StackTraceElement[]> traces) {
        if (traces == null || traces.isEmpty()) {
            return;
        }

        for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stack = entry.getValue();
            if (stack != null && stack.length > 0 && thread.isAlive()) {
                addSample(metric, stack, 1L);
            }
        }
    }

    /**
     * Performs a one-shot sample of all live JVM threads and aggregates them into the active metric.
     */
    public void sampleAllThreads() {
        ingestThreadDump(this.activeMetric, Thread.getAllStackTraces());
    }

    // =========================================================================
    // JFR Ingestion
    // =========================================================================

    /**
     * Ingests events from a JDK Flight Recorder (.jfr) recording file.
     * Automatically extracts CPU samples (jdk.ExecutionSample) into {@link MetricType#CPU_TIME}
     * and heap allocations (jdk.ObjectAllocation*) into {@link MetricType#ALLOCATION_BYTES}.
     *
     * @param jfrFile path to .jfr file
     * @throws IOException if reading the file fails
     */
    public void ingestJfr(Path jfrFile) throws IOException {
        Objects.requireNonNull(jfrFile, "jfrFile cannot be null");
        if (!Files.exists(jfrFile)) {
            throw new IOException("JFR file not found: " + jfrFile);
        }

        try (RecordingFile recording = new RecordingFile(jfrFile)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                ingestJfrEvent(event);
            }
        }
    }

    /**
     * Ingests a single JFR RecordedEvent, automatically detecting CPU vs allocation metrics.
     */
    public void ingestJfrEvent(RecordedEvent event) {
        if (event == null) return;
        String eventType = event.getEventType().getName();
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null || stackTrace.getFrames().isEmpty()) {
            return;
        }

        List<String> frames = extractJfrFrames(stackTrace);
        if (frames.isEmpty()) return;

        if (eventType.equals("jdk.ExecutionSample") || eventType.equals("jdk.NativeMethodSample")) {
            long weight = event.hasField("weight") ? event.getLong("weight") : 1L;
            addSample(MetricType.CPU_TIME, frames, Math.max(1L, weight));
        } else if (eventType.equals("jdk.ObjectAllocationInNewTLAB")
                || eventType.equals("jdk.ObjectAllocationOutsideTLAB")
                || eventType.equals("jdk.ObjectAllocationSample")) {
            long allocSize = 1L;
            if (event.hasField("allocationSize")) {
                allocSize = event.getLong("allocationSize");
            } else if (event.hasField("tlabSize")) {
                allocSize = event.getLong("tlabSize");
            } else if (event.hasField("weight")) {
                allocSize = event.getLong("weight");
            }
            addSample(MetricType.ALLOCATION_BYTES, frames, Math.max(1L, allocSize));
        }
    }

    private List<String> extractJfrFrames(RecordedStackTrace stackTrace) {
        List<RecordedFrame> rawFrames = stackTrace.getFrames();
        List<String> result = new ArrayList<>(rawFrames.size());
        // JFR frames are leaf-first; reverse to root-first
        for (int i = rawFrames.size() - 1; i >= 0; i--) {
            RecordedFrame frame = rawFrames.get(i);
            RecordedMethod method = frame.getMethod();
            if (method != null) {
                String className = method.getType().getName();
                String methodName = method.getName();
                if (!isIgnoredInternalFrame(className, methodName)) {
                    result.add(className + "." + methodName);
                }
            }
        }
        return result;
    }

    // =========================================================================
    // Folded Stack Format Ingestion
    // =========================================================================

    /**
     * Parses and aggregates a single Brendan Gregg folded stack trace line (e.g. "func1;func2;func3 42").
     */
    public void addFoldedLine(String line) {
        addFoldedLine(this.activeMetric, line);
    }

    /**
     * Parses and aggregates a single Brendan Gregg folded stack trace line into the specified metric.
     */
    public void addFoldedLine(MetricType metric, String line) {
        if (line == null) return;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }

        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace > 0 && lastSpace < trimmed.length() - 1) {
            String stackPart = trimmed.substring(0, lastSpace).trim();
            String countPart = trimmed.substring(lastSpace + 1).trim();
            try {
                long count = Long.parseLong(countPart);
                if (count > 0 && !stackPart.isEmpty()) {
                    String[] rawFrames = stackPart.split(";");
                    List<String> frames = new ArrayList<>(rawFrames.length);
                    for (String f : rawFrames) {
                        String s = f.trim();
                        if (!s.isEmpty()) {
                            frames.add(s);
                        }
                    }
                    if (!frames.isEmpty()) {
                        addSample(metric, frames, count);
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * Ingests a multiline block of Brendan Gregg folded stack data.
     */
    public void ingestFoldedText(String foldedText) {
        ingestFoldedText(this.activeMetric, foldedText);
    }

    /**
     * Ingests a multiline block of Brendan Gregg folded stack data for a specific metric.
     */
    public void ingestFoldedText(MetricType metric, String foldedText) {
        if (foldedText == null || foldedText.isBlank()) return;
        String[] lines = foldedText.split("\\r?\\n");
        for (String line : lines) {
            addFoldedLine(metric, line);
        }
    }

    /**
     * Ingests folded stack data from a file.
     */
    public void ingestFoldedFile(Path path) throws IOException {
        ingestFoldedFile(this.activeMetric, path);
    }

    /**
     * Ingests folded stack data from a file for a specific metric.
     */
    public void ingestFoldedFile(MetricType metric, Path path) throws IOException {
        Objects.requireNonNull(path, "path cannot be null");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                addFoldedLine(metric, line);
            }
        }
    }

    // =========================================================================
    // Continuous Sampling Loop
    // =========================================================================

    /**
     * Starts an asynchronous background periodic thread sampling loop.
     *
     * @param interval interval between thread dump samples
     * @param unit     time unit
     */
    public synchronized void startThreadSampling(long interval, TimeUnit unit) {
        if (samplingActive.get()) {
            return;
        }

        samplingActive.set(true);
        this.samplingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Helix-FlameGraph-Sampler");
            t.setDaemon(true);
            return t;
        });

        samplingExecutor.scheduleAtFixedRate(this::sampleAllThreads, interval, interval, unit);
        log.info("Started background thread sampling loop (interval: {} {})", interval, unit);
    }

    /**
     * Stops the background thread sampling loop.
     */
    public synchronized void stopThreadSampling() {
        if (!samplingActive.get()) {
            return;
        }

        samplingActive.set(false);
        if (samplingExecutor != null) {
            samplingExecutor.shutdownNow();
            samplingExecutor = null;
        }
        log.info("Stopped background thread sampling loop.");
    }

    public boolean isThreadSamplingActive() {
        return samplingActive.get();
    }

    // =========================================================================
    // ProfileEventListener Implementation (for DefaultProfiler integration)
    // =========================================================================

    @Override
    public void onEvent(ProfileEvent event) {
        if (event instanceof StackSampleEvent sse) {
            addSample(sse.metricType(), sse.frames(), sse.value());
        } else if (event instanceof ExecutionEvent ee) {
            List<String> frames = List.of("com.helix.engine.RuleExecution", ee.ruleName());
            addSample(MetricType.CPU_TIME, frames, Math.max(1L, ee.executionTimeNanos()));
        }
    }

    // =========================================================================
    // Folded Stack Export (Brendan Gregg FlameGraph Specification)
    // =========================================================================

    /**
     * Exports the aggregated stack traces for the active metric in Brendan Gregg folded format.
     *
     * @return newline-delimited folded stack lines ("frame1;frame2;frame3 42")
     */
    public String exportFolded() {
        return exportFolded(this.activeMetric);
    }

    /**
     * Exports the aggregated stack traces for the specified metric in Brendan Gregg folded format.
     */
    public String exportFolded(MetricType metric) {
        StackFrameNode root = getRootNode(metric);
        List<String> lines = new ArrayList<>();
        for (StackFrameNode child : root.getChildrenSortedByTotal()) {
            collectFoldedLines(child, child.getName(), lines);
        }
        return String.join("\n", lines) + (lines.isEmpty() ? "" : "\n");
    }

    /**
     * Writes the folded stack traces for the active metric to the specified destination path.
     */
    public void exportFolded(Path destination) throws IOException {
        exportFolded(this.activeMetric, destination);
    }

    /**
     * Writes the folded stack traces for the specified metric to the destination path.
     */
    public void exportFolded(MetricType metric, Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination cannot be null");
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        String folded = exportFolded(metric);
        Files.writeString(destination, folded, StandardCharsets.UTF_8);
    }

    /**
     * Writes the folded stack traces to the provided Writer.
     */
    public void exportFolded(MetricType metric, Writer writer) throws IOException {
        Objects.requireNonNull(writer, "writer cannot be null");
        writer.write(exportFolded(metric));
        writer.flush();
    }

    private void collectFoldedLines(StackFrameNode node, String currentStack, List<String> lines) {
        if (node.getSelfValue() > 0) {
            lines.add(currentStack + " " + node.getSelfValue());
        }
        for (StackFrameNode child : node.getChildrenSortedByTotal()) {
            collectFoldedLines(child, currentStack + ";" + child.getName(), lines);
        }
    }

    // =========================================================================
    // Aggregator Analytics & Lifecycle
    // =========================================================================

    /**
     * Returns total samples/value aggregated for the active metric.
     */
    public long getTotalSamples() {
        return getTotalSamples(this.activeMetric);
    }

    /**
     * Returns total samples/value aggregated for the specified metric.
     */
    public long getTotalSamples(MetricType metric) {
        StackFrameNode root = roots.get(metric);
        return root != null ? root.getTotalValue() : 0L;
    }

    /**
     * Returns the count of distinct call stack paths terminating with samples.
     */
    public int getDistinctStacksCount() {
        return getDistinctStacksCount(this.activeMetric);
    }

    /**
     * Returns the count of distinct call stack paths terminating with samples for the specified metric.
     */
    public int getDistinctStacksCount(MetricType metric) {
        StackFrameNode root = roots.get(metric);
        return root != null ? countDistinctStacks(root) : 0;
    }

    private int countDistinctStacks(StackFrameNode node) {
        int count = node.getSelfValue() > 0 ? 1 : 0;
        for (StackFrameNode child : node.getChildren()) {
            count += countDistinctStacks(child);
        }
        return count;
    }

    /**
     * Returns the maximum depth of the flame graph call tree for the active metric.
     */
    public int getMaxDepth() {
        return getMaxDepth(this.activeMetric);
    }

    /**
     * Returns the maximum depth of the flame graph call tree for the specified metric.
     */
    public int getMaxDepth(MetricType metric) {
        StackFrameNode root = roots.get(metric);
        return root != null ? calculateMaxDepth(root) : 0;
    }

    private int calculateMaxDepth(StackFrameNode node) {
        int max = node.getDepth();
        for (StackFrameNode child : node.getChildren()) {
            max = Math.max(max, calculateMaxDepth(child));
        }
        return max;
    }

    /**
     * Clears aggregated data for all metrics.
     */
    public void clear() {
        for (MetricType type : MetricType.values()) {
            clear(type);
        }
    }

    /**
     * Clears aggregated data for the specified metric.
     */
    public void clear(MetricType metric) {
        roots.put(metric, new StackFrameNode("root", 0, null));
    }

    private String sanitizeFrame(String frame) {
        return frame.replace(';', ':')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }

    private boolean isIgnoredInternalFrame(String className, String methodName) {
        if (className.equals("java.lang.Thread") && (methodName.equals("dumpThreads") || methodName.equals("getStackTrace"))) {
            return true;
        }
        return className.equals(FlameGraphAggregator.class.getName());
    }

    @Override
    public void close() {
        stopThreadSampling();
    }
}
