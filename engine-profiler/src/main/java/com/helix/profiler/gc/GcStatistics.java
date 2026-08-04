package com.helix.profiler.gc;

import java.util.Collection;

/**
 * Summary statistics computed from GC events.
 */
public class GcStatistics {

    private final long totalCollections;
    private final long fullGcCount;
    private final long youngGcCount;
    private final double totalPauseTimeMs;
    private final double maxPauseTimeMs;
    private final double avgPauseTimeMs;
    private final long totalReclaimedKb;

    public GcStatistics(Collection<GcEvent> events) {
        if (events == null || events.isEmpty()) {
            this.totalCollections = 0;
            this.fullGcCount = 0;
            this.youngGcCount = 0;
            this.totalPauseTimeMs = 0.0;
            this.maxPauseTimeMs = 0.0;
            this.avgPauseTimeMs = 0.0;
            this.totalReclaimedKb = 0;
            return;
        }

        this.totalCollections = events.size();
        this.fullGcCount = events.stream().filter(GcEvent::isFullGc).count();
        this.youngGcCount = totalCollections - fullGcCount;

        this.totalPauseTimeMs = events.stream().mapToDouble(GcEvent::pauseTimeMs).sum();
        this.maxPauseTimeMs = events.stream().mapToDouble(GcEvent::pauseTimeMs).max().orElse(0.0);
        this.avgPauseTimeMs = totalCollections == 0 ? 0.0 : totalPauseTimeMs / totalCollections;

        this.totalReclaimedKb = events.stream().mapToLong(GcEvent::getReclaimedKb).sum();
    }

    public long getTotalCollections() {
        return totalCollections;
    }

    public long getFullGcCount() {
        return fullGcCount;
    }

    public long getYoungGcCount() {
        return youngGcCount;
    }

    public double getTotalPauseTimeMs() {
        return totalPauseTimeMs;
    }

    public double getMaxPauseTimeMs() {
        return maxPauseTimeMs;
    }

    public double getAvgPauseTimeMs() {
        return avgPauseTimeMs;
    }

    public long getTotalReclaimedKb() {
        return totalReclaimedKb;
    }

    /**
     * Computes application execution throughput percentage given total runtime in milliseconds.
     */
    public double getThroughputPercentage(long uptimeMs) {
        if (uptimeMs <= 0) {
            return 100.0;
        }
        double appTimeMs = Math.max(0.0, uptimeMs - totalPauseTimeMs);
        return (appTimeMs / uptimeMs) * 100.0;
    }

    @Override
    public String toString() {
        return String.format(
                "GcStatistics{collections=%d, fullGc=%d, totalPause=%.2fms, maxPause=%.2fms, avgPause=%.2fms, reclaimed=%dKB}",
                totalCollections, fullGcCount, totalPauseTimeMs, maxPauseTimeMs, avgPauseTimeMs, totalReclaimedKb
        );
    }
}
