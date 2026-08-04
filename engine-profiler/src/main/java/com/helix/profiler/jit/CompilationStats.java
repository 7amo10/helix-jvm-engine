package com.helix.profiler.jit;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Aggregated metrics and statistical summary computed from JIT compilation events.
 */
public class CompilationStats {

    private final long totalCompilations;
    private final Map<Integer, Long> tierCounts;
    private final long totalBytecodeSize;
    private final long deoptimizationCount;
    private final long osrCount;

    public CompilationStats(Collection<CompilationEvent> events) {
        if (events == null || events.isEmpty()) {
            this.totalCompilations = 0;
            this.tierCounts = Collections.emptyMap();
            this.totalBytecodeSize = 0;
            this.deoptimizationCount = 0;
            this.osrCount = 0;
            return;
        }

        this.totalCompilations = events.size();
        this.tierCounts = events.stream()
                .collect(Collectors.groupingBy(CompilationEvent::tier, Collectors.counting()));
        this.totalBytecodeSize = events.stream()
                .mapToLong(CompilationEvent::bytecodeSize)
                .sum();
        this.deoptimizationCount = events.stream()
                .filter(CompilationEvent::isDeoptimization)
                .count();
        this.osrCount = events.stream()
                .filter(CompilationEvent::isOsr)
                .count();
    }

    public long getTotalCompilations() {
        return totalCompilations;
    }

    public Map<Integer, Long> getTierCounts() {
        return Collections.unmodifiableMap(tierCounts);
    }

    public long getCountForTier(int tier) {
        return tierCounts.getOrDefault(tier, 0L);
    }

    public long getTotalBytecodeSize() {
        return totalBytecodeSize;
    }

    public double getAverageBytecodeSize() {
        return totalCompilations == 0 ? 0.0 : (double) totalBytecodeSize / totalCompilations;
    }

    public long getDeoptimizationCount() {
        return deoptimizationCount;
    }

    public long getOsrCount() {
        return osrCount;
    }

    @Override
    public String toString() {
        return String.format(
                "CompilationStats{total=%d, tierCounts=%s, totalBytecodeSize=%d, avgBytecodeSize=%.2f, deopts=%d, OSR=%d}",
                totalCompilations, tierCounts, totalBytecodeSize, getAverageBytecodeSize(), deoptimizationCount, osrCount
        );
    }
}
