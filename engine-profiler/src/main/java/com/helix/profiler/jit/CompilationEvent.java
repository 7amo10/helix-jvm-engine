package com.helix.profiler.jit;

import java.time.Instant;
import java.util.Objects;

/**
 * Record representing a single JIT compilation event extracted from JVM PrintCompilation output.
 *
 * @param timestampMs        Timestamp in milliseconds since JVM start (or epoch ms if converted)
 * @param compileId          Unique compilation task ID assigned by HotSpot JIT
 * @param tier               Compilation tier level (1=C1 simple, 2=C1 limited, 3=C1 full, 4=C2/Server)
 * @param method             Fully qualified method signature (e.g. "com.helix.core.RuleCompiler::compile")
 * @param bytecodeSize       Size of method bytecode in bytes
 * @param isOsr              True if On-Stack Replacement (% compilation)
 * @param isSynchronized     True if synchronized method ('s')
 * @param isExceptionHolder  True if method has exception handlers ('!')
 * @param status             Status or deoptimization state (e.g. "NORMAL", "made not entrant", "made zombie")
 * @param timestamp          Instant when this event was parsed/recorded
 */
public record CompilationEvent(
        long timestampMs,
        int compileId,
        int tier,
        String method,
        int bytecodeSize,
        boolean isOsr,
        boolean isSynchronized,
        boolean isExceptionHolder,
        String status,
        Instant timestamp
) {

    public CompilationEvent {
        Objects.requireNonNull(method, "method must not be null");
        if (status == null) {
            status = "NORMAL";
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Helper constructor for standard non-OSR, non-deoptimized events.
     */
    public CompilationEvent(long timestampMs, int compileId, int tier, String method, int bytecodeSize) {
        this(timestampMs, compileId, tier, method, bytecodeSize, false, false, false, "NORMAL", Instant.now());
    }

    /**
     * Checks if this event represents a deoptimization state transition.
     */
    public boolean isDeoptimization() {
        return status.contains("not entrant") || status.contains("zombie");
    }
}
