package com.helix.profiler.flamegraph;

import com.helix.api.profiler.ProfileEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Profiling telemetry event carrying a recorded execution stack trace sample.
 */
public record StackSampleEvent(
        MetricType metricType,
        List<String> frames,
        long value,
        Instant timestamp
) implements ProfileEvent {

    public StackSampleEvent {
        Objects.requireNonNull(metricType, "metricType cannot be null");
        Objects.requireNonNull(frames, "frames cannot be null");
        frames = Collections.unmodifiableList(List.copyOf(frames));
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public StackSampleEvent(MetricType metricType, List<String> frames, long value) {
        this(metricType, frames, value, Instant.now());
    }
}
