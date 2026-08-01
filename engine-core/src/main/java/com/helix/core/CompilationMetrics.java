package com.helix.core;

import java.util.Objects;

/**
 * Performance metrics tracking stage-by-stage compilation duration in nanoseconds.
 */
public class CompilationMetrics {

    private final long parseTimeNanos;
    private final long astTimeNanos;
    private final long typeCheckTimeNanos;
    private final long optimizationTimeNanos;
    private final long bytecodeGenTimeNanos;
    private final long totalTimeNanos;

    public CompilationMetrics(long parseTimeNanos, long astTimeNanos, long typeCheckTimeNanos,
                              long optimizationTimeNanos, long bytecodeGenTimeNanos) {
        this.parseTimeNanos = parseTimeNanos;
        this.astTimeNanos = astTimeNanos;
        this.typeCheckTimeNanos = typeCheckTimeNanos;
        this.optimizationTimeNanos = optimizationTimeNanos;
        this.bytecodeGenTimeNanos = bytecodeGenTimeNanos;
        this.totalTimeNanos = parseTimeNanos + astTimeNanos + typeCheckTimeNanos + optimizationTimeNanos + bytecodeGenTimeNanos;
    }

    public long getParseTimeNanos() {
        return parseTimeNanos;
    }

    public long getAstTimeNanos() {
        return astTimeNanos;
    }

    public long getTypeCheckTimeNanos() {
        return typeCheckTimeNanos;
    }

    public long getOptimizationTimeNanos() {
        return optimizationTimeNanos;
    }

    public long getBytecodeGenTimeNanos() {
        return bytecodeGenTimeNanos;
    }

    public long getTotalTimeNanos() {
        return totalTimeNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompilationMetrics that = (CompilationMetrics) o;
        return parseTimeNanos == that.parseTimeNanos && astTimeNanos == that.astTimeNanos && typeCheckTimeNanos == that.typeCheckTimeNanos && optimizationTimeNanos == that.optimizationTimeNanos && bytecodeGenTimeNanos == that.bytecodeGenTimeNanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parseTimeNanos, astTimeNanos, typeCheckTimeNanos, optimizationTimeNanos, bytecodeGenTimeNanos);
    }

    @Override
    public String toString() {
        return "CompilationMetrics{total=" + (totalTimeNanos / 1_000_000.0) + "ms" +
                ", parse=" + (parseTimeNanos / 1_000_000.0) + "ms" +
                ", ast=" + (astTimeNanos / 1_000_000.0) + "ms" +
                ", typeCheck=" + (typeCheckTimeNanos / 1_000_000.0) + "ms" +
                ", optimize=" + (optimizationTimeNanos / 1_000_000.0) + "ms" +
                ", bytecodeGen=" + (bytecodeGenTimeNanos / 1_000_000.0) + "ms}";
    }
}
