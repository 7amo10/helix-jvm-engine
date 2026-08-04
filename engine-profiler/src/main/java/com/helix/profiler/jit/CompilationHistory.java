package com.helix.profiler.jit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe container holding historical JIT compilation events with filtering capabilities.
 */
public class CompilationHistory {

    private final List<CompilationEvent> events;
    private final int maxCapacity;

    public CompilationHistory() {
        this(10000);
    }

    public CompilationHistory(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be > 0");
        }
        this.maxCapacity = maxCapacity;
        this.events = new CopyOnWriteArrayList<>();
    }

    /**
     * Adds a compilation event to history. Evicts oldest entries if capacity is exceeded.
     */
    public synchronized void addEvent(CompilationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (events.size() >= maxCapacity) {
            events.remove(0);
        }
        events.add(event);
    }

    /**
     * Returns an unmodifiable list of all recorded events.
     */
    public List<CompilationEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * Returns compilation events for a specific method signature.
     */
    public List<CompilationEvent> getEventsForMethod(String method) {
        Objects.requireNonNull(method, "method must not be null");
        return events.stream()
                .filter(e -> e.method().contains(method))
                .collect(Collectors.toList());
    }

    /**
     * Returns compilation events for a specific compilation tier level.
     */
    public List<CompilationEvent> getEventsForTier(int tier) {
        return events.stream()
                .filter(e -> e.tier() == tier)
                .collect(Collectors.toList());
    }

    /**
     * Returns all deoptimization events (e.g. "made not entrant", "made zombie").
     */
    public List<CompilationEvent> getDeoptimizationEvents() {
        return events.stream()
                .filter(CompilationEvent::isDeoptimization)
                .collect(Collectors.toList());
    }

    /**
     * Returns the total count of recorded compilation events.
     */
    public int size() {
        return events.size();
    }

    /**
     * Clears all recorded compilation events.
     */
    public void clear() {
        events.clear();
    }
}
