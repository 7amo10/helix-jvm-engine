package com.helix.api.agent;

import java.time.Instant;

/**
 * Record representing object layout and memory analysis output (e.g., via JOL).
 */
public record MemoryAnalysisReport(
        String className,
        long instanceSizeBytes,
        long headerSizeBytes,
        int fieldCount,
        String layoutDetails,
        Instant timestamp
) {
    public MemoryAnalysisReport {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
