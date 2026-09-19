package com.helix.api.stream;

import com.helix.api.ExecutionContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates an in-flight rule evaluation event passed across streaming pipelines.
 */
public class RuleEvent {

    private final String eventId;
    private final String topic;
    private final String ruleName;
    private final ExecutionContext context;
    private final long timestamp;
    private final Map<String, String> headers;

    public RuleEvent(String topic, String ruleName, ExecutionContext context) {
        this(UUID.randomUUID().toString(), topic, ruleName, context, System.currentTimeMillis(), Collections.emptyMap());
    }

    public RuleEvent(String eventId, String topic, String ruleName, ExecutionContext context, long timestamp, Map<String, String> headers) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.topic = Objects.requireNonNull(topic, "topic cannot be null");
        this.ruleName = ruleName;
        this.context = context != null ? context : new ExecutionContext();
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Collections.emptyMap();
    }

    public static RuleEvent of(String topic, String ruleName, ExecutionContext context) {
        return new RuleEvent(topic, ruleName, context);
    }

    public static RuleEvent of(String topic, String ruleName, Map<String, Object> variables) {
        return new RuleEvent(topic, ruleName, new ExecutionContext(variables));
    }

    public static RuleEvent of(String topic, ExecutionContext context) {
        return new RuleEvent(topic, null, context);
    }

    public static Builder builder() {
        return new Builder();
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

    public ExecutionContext getContext() {
        return context;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuleEvent ruleEvent)) return false;
        return timestamp == ruleEvent.timestamp &&
                Objects.equals(eventId, ruleEvent.eventId) &&
                Objects.equals(topic, ruleEvent.topic) &&
                Objects.equals(ruleName, ruleEvent.ruleName) &&
                Objects.equals(headers, ruleEvent.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, topic, ruleName, timestamp, headers);
    }

    @Override
    public String toString() {
        return "RuleEvent{" +
                "eventId='" + eventId + '\'' +
                ", topic='" + topic + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static class Builder {
        private String eventId;
        private String topic;
        private String ruleName;
        private ExecutionContext context;
        private long timestamp;
        private Map<String, String> headers = new HashMap<>();

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder ruleName(String ruleName) {
            this.ruleName = ruleName;
            return this;
        }

        public Builder context(ExecutionContext context) {
            this.context = context;
            return this;
        }

        public Builder variable(String key, Object value) {
            if (this.context == null) {
                this.context = new ExecutionContext();
            }
            this.context.setVariable(key, value);
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public RuleEvent build() {
            return new RuleEvent(eventId, topic, ruleName, context, timestamp, headers);
        }
    }
}
