package com.helix.core.stream.kafka;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionResult;
import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.RuleStreamHandler;
import com.helix.api.stream.StreamBridge;
import com.helix.api.stream.StreamListener;
import com.helix.api.stream.StreamRecord;
import com.helix.api.stream.StreamResult;
import com.helix.api.stream.StreamStats;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise clustered Kafka streaming engine implementing {@link StreamBridge}.
 * Integrates partitioned Kafka consumption with virtual-thread fan-out, rule execution,
 * dynamic offset commitment, backpressure control, and result publication.
 */
public class KafkaStreamEngine implements StreamBridge {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamEngine.class);

    private final KafkaStreamConfig config;
    private final KafkaRuleStreamProducer producer;
    private final KafkaRuleStreamConsumer consumer;

    private final Map<String, RuleStreamHandler> topicHandlers = new ConcurrentHashMap<>();
    private final Map<String, List<StreamListener>> topicListeners = new ConcurrentHashMap<>();
    private final Map<String, CompiledRule> registeredRules = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<StreamResult>> inFlightFutures = new ConcurrentHashMap<>();

    private final AtomicLong totalPublished = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);

    private final AtomicBoolean running = new AtomicBoolean(false);

    public KafkaStreamEngine(KafkaStreamConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.producer = new KafkaRuleStreamProducer(config);
        this.consumer = new KafkaRuleStreamConsumer(config, this::evaluateRecord);
        start();
    }

    public KafkaStreamEngine(KafkaStreamConfig config, Consumer<String, byte[]> consumer, Producer<String, byte[]> producer) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.producer = new KafkaRuleStreamProducer(producer);
        this.consumer = new KafkaRuleStreamConsumer(config, consumer, this::evaluateRecord);
        start();
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            consumer.start();
            log.info("KafkaStreamEngine started for group {}", config.getGroupId());
        }
    }

    @Override
    public void subscribe(String topic, RuleStreamHandler handler) {
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        topicHandlers.put(topic, handler);
        consumer.subscribe(topicHandlers.keySet());
    }

    @Override
    public void subscribeListener(String topic, StreamListener listener) {
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        topicListeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void registerRule(String topicOrRuleName, CompiledRule rule) {
        Objects.requireNonNull(topicOrRuleName, "topicOrRuleName cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");
        registeredRules.put(topicOrRuleName, rule);
    }

    @Override
    public CompletableFuture<StreamResult> publish(RuleEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        ensureRunning();

        CompletableFuture<StreamResult> future = new CompletableFuture<>();
        if (event.getEventId() != null) {
            inFlightFutures.put(event.getEventId(), future);
        }

        producer.publishEvent(event.getTopic(), event).whenComplete((meta, err) -> {
            if (err != null) {
                totalFailed.incrementAndGet();
                if (event.getEventId() != null) {
                    inFlightFutures.remove(event.getEventId());
                }
                future.completeExceptionally(err);
            } else {
                totalPublished.incrementAndGet();
            }
        });

        return future;
    }

    @Override
    public <T> CompletableFuture<StreamResult> publish(StreamRecord<T> record) {
        Objects.requireNonNull(record, "record cannot be null");
        Map<String, String> headers = new HashMap<>(record.getHeaders());
        if (record.getKey() != null) {
            headers.putIfAbsent("kafka.key", record.getKey());
        }
        RuleEvent ruleEvent = new RuleEvent(
                record.getRecordId(),
                record.getTopic(),
                null,
                record.getContext(),
                record.getTimestamp(),
                headers
        );
        return publish(ruleEvent);
    }

    @Override
    public boolean tryPublish(RuleEvent event, StreamListener listener) {
        Objects.requireNonNull(event, "event cannot be null");
        if (!running.get()) {
            return false;
        }

        producer.publishEvent(event.getTopic(), event).whenComplete((meta, err) -> {
            if (err != null) {
                totalFailed.incrementAndGet();
                totalDropped.incrementAndGet();
            } else {
                totalPublished.incrementAndGet();
            }
        });
        return true;
    }

    public CompletableFuture<StreamResult> evaluateRecord(ConsumerRecord<String, byte[]> record) {
        long startNanos = System.nanoTime();
        String topic = record.topic();
        RuleEvent event = KafkaEventCodec.deserializeEvent(topic, record.key(), record.value(), record.headers());
        String ruleName = event.getRuleName();

        StreamResult streamResult;
        try {
            RuleStreamHandler handler = topicHandlers.get(topic);
            if (handler != null) {
                streamResult = handler.handle(event);
            } else {
                CompiledRule rule = null;
                if (ruleName != null) {
                    rule = registeredRules.get(ruleName);
                }
                if (rule == null && topic != null) {
                    rule = registeredRules.get(topic);
                }

                if (rule != null) {
                    ExecutionResult exec = rule.execute(event.getContext());
                    long duration = System.nanoTime() - startNanos;
                    if (exec.isSuccess()) {
                        streamResult = StreamResult.success(event.getEventId(), topic, ruleName,
                                exec.getResult().orElse(null), duration);
                    } else {
                        streamResult = StreamResult.failure(event.getEventId(), topic, ruleName,
                                exec.getError().orElse(new RuntimeException("Rule execution failed")), duration);
                    }
                } else {
                    long duration = System.nanoTime() - startNanos;
                    streamResult = StreamResult.failure(event.getEventId(), topic, ruleName,
                            new IllegalStateException("No handler or rule registered for topic: " + topic), duration);
                }
            }
        } catch (Throwable t) {
            long duration = System.nanoTime() - startNanos;
            streamResult = StreamResult.failure(event.getEventId(), topic, ruleName, t, duration);
        }

        totalProcessed.incrementAndGet();
        if (!streamResult.isSuccess()) {
            totalFailed.incrementAndGet();
        }

        // Notify in-flight future if matched
        if (event.getEventId() != null) {
            CompletableFuture<StreamResult> future = inFlightFutures.remove(event.getEventId());
            if (future != null) {
                future.complete(streamResult);
            }
        }

        // Forward to output topic if configured
        String outputTopic = config.getOutputTopic();
        if (outputTopic != null && !outputTopic.isBlank()) {
            producer.publishResult(outputTopic, streamResult);
        }

        // Notify topic listeners
        List<StreamListener> listeners = topicListeners.get(topic);
        if (listeners != null) {
            for (StreamListener listener : listeners) {
                try {
                    listener.onResult(streamResult);
                } catch (Throwable t) {
                    log.warn("Error in topic listener for topic {}", topic, t);
                }
            }
        }

        return CompletableFuture.completedFuture(streamResult);
    }

    @Override
    public StreamStats stats() {
        return new StreamStats(
                totalPublished.get(),
                totalProcessed.get(),
                totalFailed.get(),
                totalDropped.get(),
                config.getMaxInFlightPerPartition(),
                Math.max(0, config.getMaxInFlightPerPartition() - consumer.getTotalPolled() + consumer.getTotalCompleted())
        );
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    public void shutdown() {
        shutdown(Duration.ofSeconds(5));
    }

    @Override
    public synchronized void shutdown(Duration timeout) {
        if (running.compareAndSet(true, false)) {
            consumer.close();
            producer.flush();
            producer.close();
            log.info("KafkaStreamEngine shut down cleanly");
        }
    }

    public KafkaRuleStreamConsumer getConsumer() {
        return consumer;
    }

    public KafkaRuleStreamProducer getProducer() {
        return producer;
    }

    private void ensureRunning() {
        if (!running.get()) {
            throw new IllegalStateException("KafkaStreamEngine is not running");
        }
    }
}
