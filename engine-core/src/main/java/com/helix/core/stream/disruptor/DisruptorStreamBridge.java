package com.helix.core.stream.disruptor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.RuleStreamHandler;
import com.helix.api.stream.StreamBridge;
import com.helix.api.stream.StreamListener;
import com.helix.api.stream.StreamRecord;
import com.helix.api.stream.StreamResult;
import com.helix.api.stream.StreamStats;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ultra-low latency, zero-allocation in-process streaming engine backed by the
 * LMAX Disruptor ring buffer pattern with cache-line padded events and virtual thread
 * event processing.
 */
public class DisruptorStreamBridge implements StreamBridge {

    private static final Logger log = LoggerFactory.getLogger(DisruptorStreamBridge.class);

    private final DisruptorConfig config;
    private final Disruptor<RuleEventHolder> disruptor;
    private final RingBuffer<RuleEventHolder> ringBuffer;

    private final Map<String, RuleStreamHandler> topicHandlers = new ConcurrentHashMap<>();
    private final Map<String, List<StreamListener>> topicListeners = new ConcurrentHashMap<>();
    private final Map<String, CompiledRule> registeredRules = new ConcurrentHashMap<>();

    private final AtomicLong totalPublished = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);

    private final AtomicBoolean running = new AtomicBoolean(false);

    public DisruptorStreamBridge() {
        this(DisruptorConfig.defaultConfig());
    }

    public DisruptorStreamBridge(DisruptorConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.disruptor = new Disruptor<>(
                new RuleEventHolderFactory(),
                config.getRingBufferSize(),
                config.getThreadFactory(),
                config.getProducerType(),
                config.getWaitStrategy()
        );

        this.disruptor.setDefaultExceptionHandler(new ExceptionHandler<>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, RuleEventHolder event) {
                log.error("Unhandled exception processing stream event at sequence {}", sequence, ex);
                totalFailed.incrementAndGet();
                if (event != null) {
                    CompletableFuture<StreamResult> f = event.getFuture();
                    if (f != null && !f.isDone()) {
                        f.completeExceptionally(ex);
                    }
                    event.clear();
                }
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                log.error("Exception starting Disruptor stream processor", ex);
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                log.error("Exception shutting down Disruptor stream processor", ex);
            }
        });

        this.disruptor.handleEventsWith(new RuleBatchEventHandler());
        this.ringBuffer = this.disruptor.getRingBuffer();
        start();
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            disruptor.start();
            log.info("DisruptorStreamBridge started with ringBufferSize={}, waitStrategy={}",
                    config.getRingBufferSize(), config.getWaitStrategy().getClass().getSimpleName());
        }
    }

    @Override
    public void subscribe(String topic, RuleStreamHandler handler) {
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        topicHandlers.put(topic, handler);
    }

    @Override
    public void subscribeListener(String topic, StreamListener listener) {
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        topicListeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Registers a pre-compiled rule directly to evaluate events for a given topic or rule name.
     *
     * @param topicOrRuleName topic or rule name identifier
     * @param rule            pre-compiled rule instance
     */
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
        long sequence = ringBuffer.next();
        try {
            RuleEventHolder holder = ringBuffer.get(sequence);
            holder.setEvent(event, future, null, sequence);
            totalPublished.incrementAndGet();
        } finally {
            ringBuffer.publish(sequence);
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<StreamResult> publish(StreamRecord<T> record) {
        Objects.requireNonNull(record, "record cannot be null");
        RuleEvent ruleEvent = new RuleEvent(
                record.getRecordId(),
                record.getTopic(),
                record.getKey(),
                record.getContext(),
                record.getTimestamp(),
                record.getHeaders()
        );
        return publish(ruleEvent);
    }

    @Override
    public boolean tryPublish(RuleEvent event, StreamListener listener) {
        Objects.requireNonNull(event, "event cannot be null");
        if (!running.get()) {
            return false;
        }

        try {
            long sequence = ringBuffer.tryNext();
            try {
                RuleEventHolder holder = ringBuffer.get(sequence);
                holder.setEvent(event, null, listener, sequence);
                totalPublished.incrementAndGet();
                return true;
            } finally {
                ringBuffer.publish(sequence);
            }
        } catch (InsufficientCapacityException e) {
            totalDropped.incrementAndGet();
            return false;
        }
    }

    /**
     * Steady-state zero-allocation publication without intermediate RuleEvent heap allocations.
     *
     * @param topic    stream topic
     * @param ruleName rule name
     * @param context  execution context
     * @param listener optional result callback listener
     */
    public void publish(String topic, String ruleName, ExecutionContext context, StreamListener listener) {
        ensureRunning();
        long sequence = ringBuffer.next();
        try {
            RuleEventHolder holder = ringBuffer.get(sequence);
            holder.set(null, topic, ruleName, context, null, null, listener, 0L, sequence);
            totalPublished.incrementAndGet();
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public StreamStats stats() {
        return new StreamStats(
                totalPublished.get(),
                totalProcessed.get(),
                totalFailed.get(),
                totalDropped.get(),
                ringBuffer.getBufferSize(),
                ringBuffer.remainingCapacity()
        );
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public synchronized void shutdown(Duration timeout) {
        if (running.compareAndSet(true, false)) {
            long timeoutMs = timeout != null ? timeout.toMillis() : 5000;
            try {
                disruptor.shutdown(timeoutMs, TimeUnit.MILLISECONDS);
                log.info("DisruptorStreamBridge shut down cleanly");
            } catch (TimeoutException e) {
                log.warn("DisruptorStreamBridge shutdown timed out after {} ms; invoking halt", timeoutMs);
                disruptor.halt();
            }
        }
    }

    public RingBuffer<RuleEventHolder> getRingBuffer() {
        return ringBuffer;
    }

    private void ensureRunning() {
        if (!running.get()) {
            throw new IllegalStateException("DisruptorStreamBridge is stopped or shut down");
        }
    }

    private class RuleBatchEventHandler implements EventHandler<RuleEventHolder> {

        @Override
        public void onEvent(RuleEventHolder holder, long sequence, boolean endOfBatch) {
            String topic = holder.getTopic();
            CompletableFuture<StreamResult> future = holder.getFuture();
            StreamListener eventListener = holder.getListener();
            boolean needsTiming = (future != null || eventListener != null || !topicListeners.isEmpty());
            long startNanos = needsTiming ? System.nanoTime() : 0L;
            StreamResult streamResult = null;
            Throwable failure = null;

            try {
                String ruleName = holder.getRuleName();
                RuleStreamHandler handler = topicHandlers.get(topic);

                if (handler != null) {
                    streamResult = handler.handle(holder.getEvent());
                } else {
                    CompiledRule rule = null;
                    if (ruleName != null) {
                        rule = registeredRules.get(ruleName);
                    }
                    if (rule == null && topic != null) {
                        rule = registeredRules.get(topic);
                    }

                    if (rule != null) {
                        ExecutionResult exec = rule.execute(holder.getContext());
                        long duration = needsTiming ? System.nanoTime() - startNanos : 0L;
                        if (exec.isSuccess()) {
                            streamResult = StreamResult.success(
                                    holder.getEventId(), topic, ruleName, exec.getResult().orElse(null), duration);
                        } else {
                            streamResult = StreamResult.failure(
                                    holder.getEventId(), topic, ruleName,
                                    exec.getError().orElse(new RuntimeException("Rule execution failed")), duration);
                        }
                    } else {
                        long duration = needsTiming ? System.nanoTime() - startNanos : 0L;
                        streamResult = StreamResult.failure(
                                holder.getEventId(), topic, ruleName,
                                new IllegalStateException("No handler or rule registered for topic: " + topic), duration);
                    }
                }
            } catch (Throwable t) {
                failure = t;
                long duration = needsTiming ? System.nanoTime() - startNanos : 0L;
                streamResult = StreamResult.failure(holder.getEventId(), holder.getTopic(), holder.getRuleName(), t, duration);
            }

            if (streamResult != null) {
                totalProcessed.incrementAndGet();
                if (!streamResult.isSuccess()) {
                    totalFailed.incrementAndGet();
                }

                if (future != null) {
                    future.complete(streamResult);
                }

                if (eventListener != null) {
                    try {
                        eventListener.onResult(streamResult);
                    } catch (Throwable t) {
                        log.warn("Error invoking event StreamListener for topic {}", holder.getTopic(), t);
                    }
                }

                if (!topicListeners.isEmpty() && topic != null) {
                    List<StreamListener> listeners = topicListeners.get(topic);
                    if (listeners != null) {
                        for (StreamListener listener : listeners) {
                            try {
                                listener.onResult(streamResult);
                            } catch (Throwable t) {
                                log.warn("Error invoking topic StreamListener for topic {}", topic, t);
                            }
                        }
                    }
                }
            }

            holder.clear();
        }
    }
}
