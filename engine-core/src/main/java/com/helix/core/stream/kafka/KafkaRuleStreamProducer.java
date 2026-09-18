package com.helix.core.stream.kafka;

import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.StreamResult;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance Kafka producer for streaming rule events and structured evaluation results.
 */
public class KafkaRuleStreamProducer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaRuleStreamProducer.class);

    private final Producer<String, byte[]> producer;
    private final boolean ownsProducer;

    public KafkaRuleStreamProducer(KafkaStreamConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        this.producer = new KafkaProducer<>(config.toProducerProperties());
        this.ownsProducer = true;
    }

    public KafkaRuleStreamProducer(Producer<String, byte[]> producer) {
        this.producer = Objects.requireNonNull(producer, "producer cannot be null");
        this.ownsProducer = false;
    }

    public CompletableFuture<RecordMetadata> publish(String topic, String key, byte[] payload) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);
        return sendRecord(record);
    }

    public CompletableFuture<RecordMetadata> publishResult(String outputTopic, StreamResult result) {
        Objects.requireNonNull(outputTopic, "outputTopic cannot be null");
        Objects.requireNonNull(result, "result cannot be null");

        byte[] payload = KafkaEventCodec.serializeResult(result);
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(outputTopic, result.getEventId(), payload);

        if (result.getRuleName() != null) {
            record.headers().add(new RecordHeader("ruleName", result.getRuleName().getBytes(StandardCharsets.UTF_8)));
        }
        record.headers().add(new RecordHeader("success", String.valueOf(result.isSuccess()).getBytes(StandardCharsets.UTF_8)));

        return sendRecord(record);
    }

    public CompletableFuture<RecordMetadata> publishEvent(String topic, RuleEvent event) {
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(event, "event cannot be null");

        byte[] payload = KafkaEventCodec.serializeEvent(event);
        String key = (event.getHeaders() != null && event.getHeaders().containsKey("kafka.key"))
                ? event.getHeaders().get("kafka.key")
                : event.getEventId();
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);

        if (event.getRuleName() != null) {
            record.headers().add(new RecordHeader("ruleName", event.getRuleName().getBytes(StandardCharsets.UTF_8)));
        }
        if (event.getHeaders() != null) {
            event.getHeaders().forEach((k, v) -> {
                if (v != null) {
                    record.headers().add(new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8)));
                }
            });
        }

        return sendRecord(record);
    }

    private CompletableFuture<RecordMetadata> sendRecord(ProducerRecord<String, byte[]> record) {
        CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
        try {
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    future.completeExceptionally(exception);
                } else {
                    future.complete(metadata);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public void flush() {
        producer.flush();
    }

    @Override
    public void close() {
        if (ownsProducer) {
            try {
                producer.close();
                log.info("KafkaRuleStreamProducer closed successfully");
            } catch (Exception e) {
                log.warn("Error closing Kafka producer", e);
            }
        }
    }

    public Producer<String, byte[]> getProducer() {
        return producer;
    }
}
