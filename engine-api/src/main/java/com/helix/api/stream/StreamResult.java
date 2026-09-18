package com.helix.api.stream;

import java.util.Objects;
import java.util.Optional;

/**
 * Encapsulates the execution result of an event processed through the streaming pipeline.
 */
public class StreamResult {

    private final String eventId;
    private final String topic;
    private final String ruleName;
    private final boolean success;
    private final Object result;
    private final Throwable error;
    private final long executionTimeNanos;

    public StreamResult(String eventId, String topic, String ruleName, boolean success, Object result, Throwable error, long executionTimeNanos) {
        this.eventId = eventId;
        this.topic = topic;
        this.ruleName = ruleName;
        this.success = success;
        this.result = result;
        this.error = error;
        this.executionTimeNanos = executionTimeNanos;
    }

    public static StreamResult success(String eventId, String topic, String ruleName, Object result, long executionTimeNanos) {
        return new StreamResult(eventId, topic, ruleName, true, result, null, executionTimeNanos);
    }

    public static StreamResult failure(String eventId, String topic, String ruleName, Throwable error, long executionTimeNanos) {
        return new StreamResult(eventId, topic, ruleName, false, null, Objects.requireNonNull(error, "error cannot be null"), executionTimeNanos);
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getRuleName() {
        return ruleName;
    }

    public boolean isSuccess() {
        return success;
    }

    public Optional<Object> getResult() {
        return Optional.ofNullable(result);
    }

    public Object getResultRaw() {
        return result;
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    public long getExecutionTimeNanos() {
        return executionTimeNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamResult that)) return false;
        return success == that.success &&
                executionTimeNanos == that.executionTimeNanos &&
                Objects.equals(eventId, that.eventId) &&
                Objects.equals(topic, that.topic) &&
                Objects.equals(ruleName, that.ruleName) &&
                Objects.equals(result, that.result) &&
                Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, topic, ruleName, success, result, error, executionTimeNanos);
    }

    @Override
    public String toString() {
        return "StreamResult{" +
                "eventId='" + eventId + '\'' +
                ", topic='" + topic + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", success=" + success +
                ", result=" + result +
                ", executionTimeNanos=" + executionTimeNanos +
                '}';
    }
}
