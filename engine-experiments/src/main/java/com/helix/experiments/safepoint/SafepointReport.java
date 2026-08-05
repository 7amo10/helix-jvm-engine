package com.helix.experiments.safepoint;

/**
 * Report summarizing JVM safepoint latency, Time-To-Safepoint (TTSP), and pause duration.
 */
public record SafepointReport(
        String scenarioName,
        double timeToReachSafepointMs,
        double timeAtSafepointMs,
        double totalPauseMs,
        String safepointReason
) {

    @Override
    public String toString() {
        return String.format(
                "SafepointReport[%s] - Reason: %s | TTSP: %.3f ms | At Safepoint: %.3f ms | Total Pause: %.3f ms",
                scenarioName, safepointReason, timeToReachSafepointMs, timeAtSafepointMs, totalPauseMs
        );
    }
}
