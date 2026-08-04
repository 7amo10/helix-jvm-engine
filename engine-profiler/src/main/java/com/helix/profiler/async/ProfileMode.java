package com.helix.profiler.async;

/**
 * Supported profiling event modes for async-profiler.
 */
public enum ProfileMode {

    CPU("cpu", "CPU execution profiling"),
    ALLOC("alloc", "Memory allocation profiling"),
    LOCK("lock", "Lock contention profiling"),
    WALL("wall", "Wall-clock time profiling"),
    ITIMER("itimer", "Interval timer CPU profiling");

    private final String value;
    private final String description;

    ProfileMode(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static ProfileMode fromValue(String value) {
        if (value == null) {
            return CPU;
        }
        for (ProfileMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return CPU;
    }
}
