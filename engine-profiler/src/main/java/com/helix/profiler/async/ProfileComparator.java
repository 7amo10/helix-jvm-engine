package com.helix.profiler.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Comparator for comparing two collapsed profiling runs (e.g. Before vs After or Baseline vs Test).
 */
public class ProfileComparator {

    /**
     * Record representing the delta comparison for a specific stack trace.
     */
    public record FrameDelta(
            String stackTrace,
            long countA,
            long countB,
            long delta,
            double percentageChange
    ) {}

    /**
     * Report holding full comparison results between two profile datasets.
     */
    public record ComparisonReport(
            long totalSamplesA,
            long totalSamplesB,
            long netSampleDelta,
            List<FrameDelta> regressions,
            List<FrameDelta> optimizations,
            List<FrameDelta> allDeltas
    ) {}

    private final FlameGraphGenerator generator = new FlameGraphGenerator();

    /**
     * Compares two collapsed profile strings and produces a detailed comparison report.
     */
    public ComparisonReport compareProfiles(String profileAData, String profileBData) {
        Map<String, Long> mapA = toMap(generator.parseCollapsedData(profileAData));
        Map<String, Long> mapB = toMap(generator.parseCollapsedData(profileBData));

        long totalA = mapA.values().stream().mapToLong(Long::longValue).sum();
        long totalB = mapB.values().stream().mapToLong(Long::longValue).sum();

        Set<String> allStacks = new TreeSet<>(mapA.keySet());
        allStacks.addAll(mapB.keySet());

        List<FrameDelta> allDeltas = new ArrayList<>();
        List<FrameDelta> regressions = new ArrayList<>();
        List<FrameDelta> optimizations = new ArrayList<>();

        for (String stack : allStacks) {
            long countA = mapA.getOrDefault(stack, 0L);
            long countB = mapB.getOrDefault(stack, 0L);
            long delta = countB - countA;

            double pctChange = 0.0;
            if (countA > 0) {
                pctChange = (delta * 100.0) / countA;
            } else if (countB > 0) {
                pctChange = 100.0;
            }

            FrameDelta frameDelta = new FrameDelta(stack, countA, countB, delta, pctChange);
            allDeltas.add(frameDelta);

            if (delta > 0) {
                regressions.add(frameDelta);
            } else if (delta < 0) {
                optimizations.add(frameDelta);
            }
        }

        return new ComparisonReport(
                totalA,
                totalB,
                totalB - totalA,
                Collections.unmodifiableList(regressions),
                Collections.unmodifiableList(optimizations),
                Collections.unmodifiableList(allDeltas)
        );
    }

    private static Map<String, Long> toMap(List<Map.Entry<String, Long>> entries) {
        Map<String, Long> map = new HashMap<>();
        for (Map.Entry<String, Long> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
