package com.helix.profiler.jit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracker that monitors JIT compilation tier transitions (C1 -> C2, deoptimizations, OSR) per method.
 */
public class TieredCompilationTracker {

    private final Map<String, List<CompilationEvent>> methodHistories = new ConcurrentHashMap<>();

    /**
     * Records a new compilation event for tier transition tracking.
     */
    public void recordEvent(CompilationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        methodHistories.computeIfAbsent(event.method(), k -> new ArrayList<>()).add(event);
    }

    /**
     * Returns the chronological list of compilation events recorded for the given method.
     */
    public List<CompilationEvent> getMethodHistory(String method) {
        Objects.requireNonNull(method, "method must not be null");
        List<CompilationEvent> history = methodHistories.get(method);
        if (history == null) {
            return Collections.emptyList();
        }
        synchronized (history) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    /**
     * Gets the current highest active compilation tier reached by the method.
     * Returns 0 if no compilation events recorded or if the method was deoptimized to zombie/not-entrant.
     */
    public int getCurrentTier(String method) {
        List<CompilationEvent> history = getMethodHistory(method);
        if (history.isEmpty()) {
            return 0;
        }

        int highestTier = 0;
        for (CompilationEvent event : history) {
            if (event.isDeoptimization()) {
                // Deoptimization reduces active tier
                highestTier = 0;
            } else {
                highestTier = Math.max(highestTier, event.tier());
            }
        }
        return highestTier;
    }

    /**
     * Returns the total count of tier transitions across all tracked methods.
     */
    public int getTotalTransitions() {
        return methodHistories.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Checks if a method reached Tier 4 (C2 Server Compiler).
     */
    public boolean isC2Compiled(String method) {
        return getCurrentTier(method) == 4;
    }

    /**
     * Resets tracking data.
     */
    public void reset() {
        methodHistories.clear();
    }
}
