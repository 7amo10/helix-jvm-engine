package com.helix.core.events;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Event representation emitted across the engine lifecycle.
 */
public record EngineEvent(
        EventType type,
        String ruleName,
        long timestamp,
        Map<String, Object> payload
) {
    public EngineEvent(EventType type, String ruleName, Map<String, Object> payload) {
        this(Objects.requireNonNull(type, "type cannot be null"),
             ruleName,
             System.currentTimeMillis(),
             payload != null ? Map.copyOf(payload) : Collections.emptyMap());
    }

    public EngineEvent(EventType type, String ruleName) {
        this(type, ruleName, Collections.emptyMap());
    }
}
