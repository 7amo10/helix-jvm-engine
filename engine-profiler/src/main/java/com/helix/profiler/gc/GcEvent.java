package com.helix.profiler.gc;

import java.time.Instant;
import java.util.Objects;

/**
 * Record representing a Garbage Collection event parsed from JDK Unified GC Logging (-Xlog:gc*).
 *
 * @param timestampMs  Timestamp in milliseconds relative to JVM launch
 * @param gcId         GC collection ID
 * @param type         GC type (e.g. "Pause Young (Normal)", "Pause Full")
 * @param cause        GC cause / phase (e.g. "G1 Evacuation Pause", "System.gc()")
 * @param heapBeforeKb Heap memory used before collection in KB
 * @param heapAfterKb  Heap memory used after collection in KB
 * @param heapTotalKb  Total committed heap capacity in KB
 * @param pauseTimeMs  Duration of GC pause in milliseconds
 * @param isFullGc     True if this collection was a Full GC
 * @param timestamp    Instant when event was parsed
 */
public record GcEvent(
        long timestampMs,
        int gcId,
        String type,
        String cause,
        long heapBeforeKb,
        long heapAfterKb,
        long heapTotalKb,
        double pauseTimeMs,
        boolean isFullGc,
        Instant timestamp
) {

    public GcEvent {
        Objects.requireNonNull(type, "type must not be null");
        if (cause == null) {
            cause = "Unknown";
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Calculates memory reclaimed during this collection cycle in KB.
     */
    public long getReclaimedKb() {
        return Math.max(0, heapBeforeKb - heapAfterKb);
    }
}
