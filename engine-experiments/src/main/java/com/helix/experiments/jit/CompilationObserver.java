package com.helix.experiments.jit;

import com.helix.profiler.jit.CompilationEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer that collects and analyzes HotSpot JIT compilation events during experiment runs.
 */
public class CompilationObserver {

    private final List<CompilationEvent> events;

    public CompilationObserver() {
        this.events = new CopyOnWriteArrayList<>();
    }

    public void onCompilationEvent(CompilationEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public int getHighestTierForMethod(String methodName) {
        return events.stream()
                .filter(e -> e.method().contains(methodName))
                .mapToInt(CompilationEvent::tier)
                .max()
                .orElse(0);
    }

    public int getCompilationCountForTier(int tier) {
        return (int) events.stream()
                .filter(e -> e.tier() == tier)
                .count();
    }

    public List<CompilationEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public void clear() {
        events.clear();
    }
}
