package com.helix.agent.jol;

import java.util.Objects;

/**
 * Central service facade for memory layout analysis.
 */
public class MemoryAnalyzer {

    private final ObjectLayoutInspector inspector;

    public MemoryAnalyzer() {
        this(new ObjectLayoutInspector());
    }

    public MemoryAnalyzer(ObjectLayoutInspector inspector) {
        this.inspector = Objects.requireNonNull(inspector, "inspector cannot be null");
    }

    public MemoryAnalysisReport analyze(Object object) {
        return inspector.inspect(object);
    }

    public MemoryAnalysisReport analyzeClass(Class<?> clazz) {
        return inspector.inspectClass(clazz);
    }
}
