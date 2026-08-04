package com.helix.profiler.health;

import com.helix.profiler.gc.GcStatistics;
import com.helix.profiler.metrics.MetricsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Health check evaluator that inspects engine components for Metaspace usage, ClassLoader leaks,
 * cache effectiveness, and GC pressure.
 */
public class EngineHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(EngineHealthCheck.class);

    private final long maxClassLoaderThreshold;
    private final double minCacheHitRateThreshold;
    private final double maxGcPauseThresholdMs;

    public EngineHealthCheck() {
        this(50, 0.40, 100.0);
    }

    public EngineHealthCheck(long maxClassLoaderThreshold, double minCacheHitRateThreshold, double maxGcPauseThresholdMs) {
        this.maxClassLoaderThreshold = maxClassLoaderThreshold;
        this.minCacheHitRateThreshold = minCacheHitRateThreshold;
        this.maxGcPauseThresholdMs = maxGcPauseThresholdMs;
    }

    /**
     * Executes health checks given metrics snapshot and GC statistics.
     */
    public HealthStatus checkHealth(MetricsSnapshot metrics, GcStatistics gcStats) {
        List<HealthIssue> issues = new ArrayList<>();
        Severity highestSeverity = Severity.INFO;

        // 1. Metaspace / Non-Heap Usage Check
        MemoryUsage nonHeapUsage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long nonHeapUsedMb = nonHeapUsage.getUsed() / (1024 * 1024);
        if (nonHeapUsedMb > 250) {
            issues.add(new HealthIssue(
                    "Metaspace",
                    String.format("High Metaspace usage detected: %d MB used", nonHeapUsedMb),
                    Severity.WARNING,
                    Instant.now()
            ));
            highestSeverity = maxSeverity(highestSeverity, Severity.WARNING);
        }

        // 2. ClassLoader Leak Check
        if (metrics != null && metrics.activeClassLoaders() > maxClassLoaderThreshold) {
            Severity sev = metrics.activeClassLoaders() > (maxClassLoaderThreshold * 2) ? Severity.CRITICAL : Severity.WARNING;
            issues.add(new HealthIssue(
                    "ClassLoaderManager",
                    String.format("Potential ClassLoader leak detected: %d active ClassLoaders (Threshold: %d)", metrics.activeClassLoaders(), maxClassLoaderThreshold),
                    sev,
                    Instant.now()
            ));
            highestSeverity = maxSeverity(highestSeverity, sev);
        }

        // 3. Cache Effectiveness Check
        if (metrics != null && (metrics.cacheHits() + metrics.cacheMisses()) > 20) {
            double hitRate = metrics.getCacheHitRate();
            if (hitRate < minCacheHitRateThreshold) {
                issues.add(new HealthIssue(
                        "TieredCache",
                        String.format("Low cache hit rate: %.1f%% (Threshold: %.1f%%)", hitRate * 100, minCacheHitRateThreshold * 100),
                        Severity.WARNING,
                        Instant.now()
                ));
                highestSeverity = maxSeverity(highestSeverity, Severity.WARNING);
            }
        }

        // 4. GC Pressure & Latency Check
        if (gcStats != null && gcStats.getTotalCollections() > 0) {
            if (gcStats.getMaxPauseTimeMs() > maxGcPauseThresholdMs) {
                Severity sev = gcStats.getMaxPauseTimeMs() > (maxGcPauseThresholdMs * 5) ? Severity.CRITICAL : Severity.WARNING;
                issues.add(new HealthIssue(
                        "GarbageCollector",
                        String.format("High GC pause duration detected: %.2f ms (Threshold: %.2f ms)", gcStats.getMaxPauseTimeMs(), maxGcPauseThresholdMs),
                        sev,
                        Instant.now()
                ));
                highestSeverity = maxSeverity(highestSeverity, sev);
            }
        }

        boolean isHealthy = highestSeverity != Severity.CRITICAL;
        return new HealthStatus(isHealthy, highestSeverity, Collections.unmodifiableList(issues), Instant.now());
    }

    private static Severity maxSeverity(Severity s1, Severity s2) {
        if (s1 == Severity.CRITICAL || s2 == Severity.CRITICAL) return Severity.CRITICAL;
        if (s1 == Severity.WARNING || s2 == Severity.WARNING) return Severity.WARNING;
        return Severity.INFO;
    }
}
