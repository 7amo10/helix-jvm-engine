package com.helix.agent.transformer;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry and metric collector for tracking object allocations across instrumented classes.
 */
public class AllocationTracker {

    private static final AllocationTracker INSTANCE = new AllocationTracker();

    private final AtomicLong totalAllocations = new AtomicLong(0);
    private final Map<String, AtomicLong> allocationsByClass = new ConcurrentHashMap<>();

    public static AllocationTracker getInstance() {
        return INSTANCE;
    }

    public void recordAllocation(String className) {
        totalAllocations.incrementAndGet();
        if (className != null && !className.isBlank()) {
            allocationsByClass.computeIfAbsent(className, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public long getTotalAllocations() {
        return totalAllocations.get();
    }

    public long getAllocationsForClass(String className) {
        AtomicLong count = allocationsByClass.get(className);
        return count != null ? count.get() : 0L;
    }

    public Map<String, Long> getAllocationsByClass() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        allocationsByClass.forEach((k, v) -> snapshot.put(k, v.get()));
        return Collections.unmodifiableMap(snapshot);
    }

    public void reset() {
        totalAllocations.set(0);
        allocationsByClass.clear();
    }
}
