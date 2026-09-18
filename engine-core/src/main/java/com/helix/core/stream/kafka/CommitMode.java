package com.helix.core.stream.kafka;

/**
 * Offset commitment modes for Kafka partition consumer streams.
 */
public enum CommitMode {
    /**
     * Synchronous blocking commit after processing records.
     */
    SYNC,

    /**
     * Asynchronous non-blocking commit via callback.
     */
    ASYNC,

    /**
     * Manual or external offset commitment disabled in poll loop.
     */
    NONE
}
