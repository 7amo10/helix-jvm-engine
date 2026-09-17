package com.helix.core;

import com.helix.api.profiler.ProfileEvent;
import com.helix.api.profiler.ProfileEventListener;
import com.helix.api.profiler.Profiler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link Profiler} tracking engine telemetry events.
 */
public class DefaultProfiler implements Profiler {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<ProfileEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ProfileEvent> recordedEvents = new CopyOnWriteArrayList<>();

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void addListener(ProfileEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(ProfileEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public void recordEvent(ProfileEvent event) {
        if (event != null && running.get()) {
            recordedEvents.add(event);
            for (ProfileEventListener listener : listeners) {
                listener.onEvent(event);
            }
        }
    }

    @Override
    public List<ProfileEvent> getRecordedEvents() {
        return List.copyOf(recordedEvents);
    }

    @Override
    public void clear() {
        recordedEvents.clear();
    }
}
