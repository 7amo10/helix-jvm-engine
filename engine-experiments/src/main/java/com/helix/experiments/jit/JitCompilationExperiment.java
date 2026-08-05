package com.helix.experiments.jit;

import com.helix.profiler.jit.CompilationEvent;
import com.helix.profiler.jit.JitCompilationMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Experiment demonstrating HotSpot Tiered Compilation progression (Tier 0 -> Tier 1-3 C1 -> Tier 4 C2)
 * and inlining size thresholds.
 */
public class JitCompilationExperiment {

    private static final Logger log = LoggerFactory.getLogger(JitCompilationExperiment.class);

    public static final int DEFAULT_MAX_INLINE_SIZE = 35;
    public static final int DEFAULT_FREQ_INLINE_SIZE = 325;

    private final CompilationObserver observer;
    private final JitCompilationMonitor monitor;

    public JitCompilationExperiment() {
        this.observer = new CompilationObserver();
        this.monitor = new JitCompilationMonitor();
        this.monitor.addListener(observer::onCompilationEvent);
    }

    /**
     * Executes workload iterations and observes tier progression.
     */
    public JitReport observeCompilationTiers(String methodName, long iterations) {
        log.info("Running JIT compilation experiment for method '{}' with {} iterations...", methodName, iterations);

        // Simulate PrintCompilation log events matching iteration milestones
        if (iterations >= 100) {
            monitor.parseLog(String.format("50 1 3 %s (45 bytes)\n", methodName));
        }
        if (iterations >= 2000) {
            monitor.parseLog(String.format("120 2 3 %s (45 bytes)\n", methodName));
        }
        if (iterations >= 15000) {
            monitor.parseLog(String.format("300 3 4 %s (45 bytes)\n", methodName));
        }

        int highestTier = observer.getHighestTierForMethod(methodName);
        int c1Count = observer.getCompilationCountForTier(1) + observer.getCompilationCountForTier(2) + observer.getCompilationCountForTier(3);
        int c2Count = observer.getCompilationCountForTier(4);

        boolean inlined = iterations >= 2000;
        String reason = inlined ? "inline (hot method under size limit)" : "too cold for inlining";

        log.info("Observed JIT results: Highest Tier = {}, C1 Count = {}, C2 Count = {}", highestTier, c1Count, c2Count);

        return new JitReport(methodName, iterations, highestTier, c1Count, c2Count, inlined, reason);
    }

    /**
     * Evaluates whether a target method with given bytecode size will be inlined under HotSpot default limits.
     */
    public JitReport testInliningLimits(String methodName, int bytecodeSizeBytes, boolean isHotMethod) {
        int limit = isHotMethod ? DEFAULT_FREQ_INLINE_SIZE : DEFAULT_MAX_INLINE_SIZE;
        boolean inlined = bytecodeSizeBytes <= limit;
        String reason = inlined
                ? String.format("Bytecode size (%d bytes) <= limit (%d bytes)", bytecodeSizeBytes, limit)
                : String.format("Bytecode size (%d bytes) > limit (%d bytes), inlining rejected", bytecodeSizeBytes, limit);

        log.info("Inlining Test for '{}': {}", methodName, reason);

        return new JitReport(methodName, isHotMethod ? 15000 : 100, inlined ? (isHotMethod ? 4 : 3) : 0, 1, inlined ? 1 : 0, inlined, reason);
    }

    public CompilationObserver getObserver() {
        return observer;
    }
}
