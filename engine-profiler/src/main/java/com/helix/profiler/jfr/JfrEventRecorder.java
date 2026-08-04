package com.helix.profiler.jfr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for emitting custom Helix JFR events to Flight Recorder.
 */
public class JfrEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(JfrEventRecorder.class);

    public void recordExecution(String ruleName, String ruleVersion, boolean success, long durationNanos, String errorMessage) {
        try {
            RuleExecutionEvent event = new RuleExecutionEvent();
            if (event.isEnabled()) {
                event.ruleName = ruleName;
                event.ruleVersion = ruleVersion;
                event.success = success;
                event.durationNanos = durationNanos;
                event.errorMessage = errorMessage != null ? errorMessage : "";
                event.commit();
            }
        } catch (Exception e) {
            log.debug("Failed to emit RuleExecutionEvent: {}", e.getMessage());
        }
    }

    public void recordCompilation(String ruleName, String generatorType, long compilationTimeNanos, int bytecodeSizeBytes, boolean success) {
        try {
            RuleCompilationEvent event = new RuleCompilationEvent();
            if (event.isEnabled()) {
                event.ruleName = ruleName;
                event.generatorType = generatorType;
                event.compilationTimeNanos = compilationTimeNanos;
                event.bytecodeSizeBytes = bytecodeSizeBytes;
                event.success = success;
                event.commit();
            }
        } catch (Exception e) {
            log.debug("Failed to emit RuleCompilationEvent: {}", e.getMessage());
        }
    }

    public void recordClassLoaderCreated(String loaderId, String isolationMode, String parentLoaderName) {
        try {
            ClassLoaderCreatedEvent event = new ClassLoaderCreatedEvent();
            if (event.isEnabled()) {
                event.loaderId = loaderId;
                event.isolationMode = isolationMode;
                event.parentLoaderName = parentLoaderName;
                event.commit();
            }
        } catch (Exception e) {
            log.debug("Failed to emit ClassLoaderCreatedEvent: {}", e.getMessage());
        }
    }

    public void recordCacheEviction(String ruleName, String cacheTier, String evictionReason) {
        try {
            CacheEvictionEvent event = new CacheEvictionEvent();
            if (event.isEnabled()) {
                event.ruleName = ruleName;
                event.cacheTier = cacheTier;
                event.evictionReason = evictionReason;
                event.commit();
            }
        } catch (Exception e) {
            log.debug("Failed to emit CacheEvictionEvent: {}", e.getMessage());
        }
    }

    public void recordMemoryAnalysis(String className, long shallowSizeBytes, long deepSizeBytes, boolean compressedOopsEnabled) {
        try {
            MemoryAnalysisEvent event = new MemoryAnalysisEvent();
            if (event.isEnabled()) {
                event.className = className;
                event.shallowSizeBytes = shallowSizeBytes;
                event.deepSizeBytes = deepSizeBytes;
                event.compressedOopsEnabled = compressedOopsEnabled;
                event.commit();
            }
        } catch (Exception e) {
            log.debug("Failed to emit MemoryAnalysisEvent: {}", e.getMessage());
        }
    }
}
