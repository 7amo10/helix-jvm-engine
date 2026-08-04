package com.helix.profiler.health;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Summary record representing system health status.
 */
public record HealthStatus(
        boolean isHealthy,
        Severity overallSeverity,
        List<HealthIssue> issues,
        Instant timestamp
) {

    public HealthStatus {
        if (overallSeverity == null) {
            overallSeverity = Severity.INFO;
        }
        if (issues == null) {
            issues = Collections.emptyList();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
