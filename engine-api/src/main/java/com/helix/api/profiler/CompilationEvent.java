package com.helix.api.profiler;

import java.time.Instant;

/**
 * Record representing a rule compilation event.
 */
public record CompilationEvent(
        String ruleName,
        String ruleVersion,
        long compilationTimeNanos,
        boolean success,
        String generatorType,
        Instant timestamp
) implements ProfileEvent {

    public CompilationEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
