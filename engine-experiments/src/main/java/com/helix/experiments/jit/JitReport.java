package com.helix.experiments.jit;

/**
 * Report summarizing observed JIT compilation tiers, invocation counts, and inlining decisions.
 */
public record JitReport(
        String targetMethod,
        long totalInvocations,
        int highestTierAchieved,
        int c1CompilationCount,
        int c2CompilationCount,
        boolean isInlined,
        String inliningReason
) {

    @Override
    public String toString() {
        return String.format(
                "JitReport[%s] - Invocations: %d | Highest Tier: %d | C1 Counts: %d | C2 Counts: %d | Inlined: %s (%s)",
                targetMethod, totalInvocations, highestTierAchieved, c1CompilationCount, c2CompilationCount, isInlined, inliningReason
        );
    }
}
