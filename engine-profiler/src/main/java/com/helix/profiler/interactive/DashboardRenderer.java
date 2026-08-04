package com.helix.profiler.interactive;

import com.helix.profiler.gc.GcStatistics;
import com.helix.profiler.jit.CompilationStats;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Renderer that formats JIT, GC, Cache, and Memory metrics into a clean, boxed terminal dashboard view.
 */
public class DashboardRenderer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Renders a full snapshot dashboard string from compilation, GC, and memory statistics.
     */
    public String renderDashboard(CompilationStats jitStats, GcStatistics gcStats, long activeClassLoaders, long cacheEntries) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        long heapUsedMb = heapUsage.getUsed() / (1024 * 1024);
        long heapMaxMb = heapUsage.getMax() > 0 ? heapUsage.getMax() / (1024 * 1024) : (heapUsage.getCommitted() / (1024 * 1024));
        double heapPct = heapMaxMb > 0 ? (heapUsedMb * 100.0 / heapMaxMb) : 0.0;

        long nonHeapUsedMb = nonHeapUsage.getUsed() / (1024 * 1024);

        String now = LocalDateTime.now().format(TIME_FORMATTER);

        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append(String.format("  HELIX REAL-TIME JVM ENGINE & PROFILER DASHBOARD                 [%s]\n", now));
        sb.append("================================================================================\n");
        sb.append(" [ MEMORY & METASPACE ]\n");
        sb.append(String.format("   Heap Memory Used    : %4d MB / %4d MB (%.1f%%)\n", heapUsedMb, heapMaxMb, heapPct));
        sb.append(String.format("   Non-Heap / Metaspace: %4d MB\n", nonHeapUsedMb));
        sb.append(String.format("   Active ClassLoaders : %d\n", activeClassLoaders));
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(" [ JIT COMPILATION MONITOR ]\n");
        if (jitStats != null) {
            sb.append(String.format("   Total Compilations  : %d\n", jitStats.getTotalCompilations()));
            sb.append(String.format("   C1 Compilations (T1-3): %d\n", jitStats.getCountForTier(1) + jitStats.getCountForTier(2) + jitStats.getCountForTier(3)));
            sb.append(String.format("   C2 Compilations (T4)  : %d\n", jitStats.getCountForTier(4)));
            sb.append(String.format("   OSR / Deoptimizations: %d / %d\n", jitStats.getOsrCount(), jitStats.getDeoptimizationCount()));
            sb.append(String.format("   Avg Bytecode Size    : %.1f bytes\n", jitStats.getAverageBytecodeSize()));
        } else {
            sb.append("   (JIT Monitor Inactive)\n");
        }
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(" [ GC & SAFEPOINT ANALYSIS ]\n");
        if (gcStats != null) {
            sb.append(String.format("   Total GC Collections: %d (Young: %d, Full: %d)\n", gcStats.getTotalCollections(), gcStats.getYoungGcCount(), gcStats.getFullGcCount()));
            sb.append(String.format("   Cumulative Pause    : %.2f ms\n", gcStats.getTotalPauseTimeMs()));
            sb.append(String.format("   Avg / Max GC Pause  : %.2f ms / %.2f ms\n", gcStats.getAvgPauseTimeMs(), gcStats.getMaxPauseTimeMs()));
            sb.append(String.format("   Reclaimed Memory    : %d KB\n", gcStats.getTotalReclaimedKb()));
        } else {
            sb.append("   (GC Analyzer Inactive)\n");
        }
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(" [ TIERED CACHE ]\n");
        sb.append(String.format("   Active Cached Rules : %d\n", cacheEntries));
        sb.append("================================================================================\n");

        return sb.toString();
    }
}
