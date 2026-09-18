package com.helix.api.stream;

import com.helix.api.ExecutionContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Generic record representing an item ingested into or transmitted across a stream.
 *
 * @param <T> type of the record payload
 */
public class StreamRecord<T> {

    private final String recordId;
    private final String topic;
    private final String key;
    private final T payload;
    private final ExecutionContext context;
    private final long timestamp;
    private final Map<String, String> headers;

    public StreamRecord(String topic, T payload) {
        this(UUID.randomUUID().toString(), topic, null, payload, null, System.currentTimeMillis(), Collections.emptyMap());
    }

    public StreamRecord(String topic, String key, T payload) {
        this(UUID.randomUUID().toString(), topic, key, payload, null, System.currentTimeMillis(), Collections.emptyMap());
    }

    public StreamRecord(String recordId, String topic, String key, T payload, ExecutionContext context, long timestamp, Map<String, String> headers) {
        this.recordId = recordId != null ? recordId : UUID.randomUUID().toString();
        this.topic = Objects.requireNonNull(topic, "topic cannot be null");
        this.key = key;
        this.payload = payload;
        this.context = context != null ? context : new ExecutionContext();
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Collections.emptyMap();
    }

    public static <T> StreamRecord<T> of(String topic, T payload) {
        return new StreamRecord<>(topic, payload);
    }

    public static <T> StreamRecord<T> of(String topic, String key, T payload) {
        return new StreamRecord<>(topic, key, payload);
    }

    public static <T> StreamRecord<T> of(String topic, String key, T payload, ExecutionContext context) {
        return new StreamRecord<>(UUID.randomUUID().toString(), topic, key, payload, context, System.currentTimeMillis(), Collections.emptyMap());
    }

    public String getRecordId() {
        return recordId;
    }

    public String getTopic() {
        return topic;
    }

    public String getKey() {
        return key;
    }

    public T getPayload() {
        return payload;
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
        if (!(o instanceof StreamRecord<?> that)) return false;
        return timestamp == that.timestamp &&
                Objects.equals(recordId, that.recordId) &&
                Objects.equals(topic, that.topic) &&
                Objects.equals(key, that.key) &&
                Objects.equals(payload, that.payload) &&
                Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId, topic, key, payload, timestamp, headers);
    }

    @Override
    public String toString() {
        return "StreamRecord{" +
                "recordId='" + recordId + '\'' +
                ", topic='" + topic + '\'' +
                ", key='" + key + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
