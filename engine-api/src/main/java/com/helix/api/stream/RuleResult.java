package com.helix.api.stream;

/**
 * Convenience subclass of {@link StreamResult} representing rule evaluation output.
 */
public class RuleResult extends StreamResult {

    public RuleResult(String eventId, String topic, String ruleName, boolean success, Object result, Throwable error, long executionTimeNanos) {
        super(eventId, topic, ruleName, success, result, error, executionTimeNanos);
    }

    public static RuleResult from(StreamResult streamResult) {
        if (streamResult instanceof RuleResult rr) {
            return rr;
        }
        return new RuleResult(
                streamResult.getEventId(),
                streamResult.getTopic(),
                streamResult.getRuleName(),
                streamResult.isSuccess(),
                streamResult.getResultRaw(),
                streamResult.getError().orElse(null),
                streamResult.getExecutionTimeNanos()
        );
    }
}
