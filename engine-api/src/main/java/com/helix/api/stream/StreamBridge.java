package com.helix.api.stream;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance streaming SPI bridge for in-process event ingestion,
 * rule evaluation, and result dispatching.
 */
public interface StreamBridge extends AutoCloseable {

    /**
     * Subscribes a functional rule stream handler to a given topic.
     *
     * @param topic   stream topic
     * @param handler handler invoked for incoming events on the topic
     */
    void subscribe(String topic, RuleStreamHandler handler);

    /**
     * Subscribes an asynchronous result listener to a given topic.
     *
     * @param topic    stream topic
     * @param listener listener invoked when events on the topic complete evaluation
     */
    void subscribeListener(String topic, StreamListener listener);

    /**
     * Alias for {@link #subscribeListener(String, StreamListener)}.
     *
     * @param topic    stream topic
     * @param listener listener invoked when events on the topic complete evaluation
     */
    default void addListener(String topic, StreamListener listener) {
        subscribeListener(topic, listener);
    }

    /**
     * Publishes a rule evaluation event into the stream and returns a CompletableFuture for the result.
     *
     * @param event the rule event to publish
     * @return CompletableFuture completing with the evaluation result
     */
    CompletableFuture<StreamResult> publish(RuleEvent event);

    /**
     * Publishes a generic stream record into the stream and returns a CompletableFuture for the result.
     *
     * @param record the stream record to publish
     * @param <T>    payload type
     * @return CompletableFuture completing with the evaluation result
     */
    <T> CompletableFuture<StreamResult> publish(StreamRecord<T> record);

    /**
     * Fast-path non-blocking publish of a rule event with a completion listener, avoiding Future allocation.
     *
     * @param event    rule event
     * @param listener optional listener for evaluation result
     * @return true if published onto the ring buffer, false if saturated
     */
    boolean tryPublish(RuleEvent event, StreamListener listener);

    /**
     * Fast-path fire-and-forget publish of an event.
     *
     * @param event rule event
     * @return true if accepted, false if saturated
     */
    default boolean tryPublish(RuleEvent event) {
        return tryPublish(event, null);
    }

    /**
     * Retrieves current streaming engine metrics and capacity stats.
     *
     * @return runtime stats snapshot
     */
    StreamStats stats();

    /**
     * Checks if the stream bridge is active and accepting events.
     *
     * @return true if running, false if stopped or shut down
     */
    boolean isRunning();

    /**
     * Gracefully shuts down the stream bridge within the specified timeout duration.
     *
     * @param timeout maximum time to await in-flight events draining
     */
    void shutdown(Duration timeout);

    @Override
    default void close() {
        shutdown(Duration.ofSeconds(5));
    }
}
