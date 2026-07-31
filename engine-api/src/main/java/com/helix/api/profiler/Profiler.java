package com.helix.api.profiler;

import java.util.List;

/**
 * Core profiling interface for starting, stopping, and listening to Helix engine telemetry.
 */
public interface Profiler {

    /**
     * Starts the profiler.
     */
    void start();

    /**
     * Stops the profiler.
     */
    void stop();

    /**
     * Checks if the profiler is currently active.
     *
     * @return true if running, false otherwise
     */
    boolean isRunning();

    /**
     * Adds an event listener to receive profiling events.
     *
     * @param listener listener to register
     */
    void addListener(ProfileEventListener listener);

    /**
     * Removes an event listener.
     *
     * @param listener listener to unregister
     */
    void removeListener(ProfileEventListener listener);

    /**
     * Records a profile event manually or programmatically.
     *
     * @param event the event to record
     */
    void recordEvent(ProfileEvent event);

    /**
     * Retrieves all recorded events up to this point.
     *
     * @return snapshot list of recorded events
     */
    List<ProfileEvent> getRecordedEvents();

    /**
     * Clears all recorded event history.
     */
    void clear();
}
