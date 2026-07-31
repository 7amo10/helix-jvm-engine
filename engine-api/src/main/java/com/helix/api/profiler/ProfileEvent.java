package com.helix.api.profiler;

import java.time.Instant;

/**
 * Base interface for all profiler events recorded in the Helix engine.
 */
public interface ProfileEvent {

    /**
     * Gets the timestamp when the event occurred.
     *
     * @return event timestamp
     */
    Instant timestamp();
}
