package com.helix.profiler.health;

import java.time.Instant;
import java.util.Objects;

/**
 * Record representing a specific health issue or anomaly detected during health check.
 */
public record HealthIssue(
        String component,
        String message,
        Severity severity,
        Instant timestamp
) {

    public HealthIssue {
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (severity == null) {
            severity = Severity.INFO;
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
