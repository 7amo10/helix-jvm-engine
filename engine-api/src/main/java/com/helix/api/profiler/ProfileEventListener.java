package com.helix.api.profiler;

/**
 * Listener interface for receiving profiler events.
 */
@FunctionalInterface
public interface ProfileEventListener {

    /**
     * Called when a profiler event is emitted.
     *
     * @param event the profiler event
     */
    void onEvent(ProfileEvent event);
}
