package com.helix.api.profiler;

import java.time.Instant;

/**
 * Record representing a cache event (hit, miss, put, eviction).
 */
public record CacheEvent(
        String ruleKey,
        CacheOperation operation,
        String cacheTier,
        Instant timestamp
) implements ProfileEvent {

    public enum CacheOperation {
        HIT,
        MISS,
        PUT,
        EVICT
    }

    public CacheEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
