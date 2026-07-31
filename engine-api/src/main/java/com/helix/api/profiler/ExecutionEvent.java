package com.helix.api.profiler;

import java.time.Instant;

/**
 * Record representing a rule execution event.
 */
public record ExecutionEvent(
        String ruleName,
        String ruleVersion,
        long executionTimeNanos,
        boolean success,
        Instant timestamp
) implements ProfileEvent {

    public ExecutionEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
