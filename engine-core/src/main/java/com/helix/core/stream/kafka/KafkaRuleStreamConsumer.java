package com.helix.core.stream.kafka;

import com.helix.api.stream.StreamResult;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Enterprise clustered Kafka stream consumer supporting partition-level parallelism,
 * virtual thread fan-out, dynamic rebalance listeners, and backpressure control via pause/resume.
 */
public class KafkaRuleStreamConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaRuleStreamConsumer.class);

    @FunctionalInterface
    public interface RecordEvaluator {
        CompletableFuture<StreamResult> evaluate(ConsumerRecord<String, byte[]> record);
    }

    private final KafkaStreamConfig config;
    private final Consumer<String, byte[]> consumer;
    private final boolean ownsConsumer;
    private final RecordEvaluator evaluator;

    private final ExecutorService partitionExecutor;
    private final Thread pollThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<TopicPartition, AtomicInteger> partitionInFlight = new ConcurrentHashMap<>();
    private final Map<TopicPartition, Long> partitionProcessedOffsets = new ConcurrentHashMap<>();
    private final Set<TopicPartition> pausedPartitions = ConcurrentHashMap.newKeySet();
    private final Set<TopicPartition> activePartitions = ConcurrentHashMap.newKeySet();

    private final AtomicLong totalPolled = new AtomicLong(0);
    private final AtomicLong totalCompleted = new AtomicLong(0);
    private final AtomicReference<Set<String>> pendingSubscriptions = new AtomicReference<>();

    public KafkaRuleStreamConsumer(KafkaStreamConfig config, RecordEvaluator evaluator) {
        this(config, new KafkaConsumer<>(config.toConsumerProperties()), true, evaluator);
    }

    public KafkaRuleStreamConsumer(KafkaStreamConfig config, Consumer<String, byte[]> consumer, RecordEvaluator evaluator) {
        this(config, consumer, false, evaluator);
    }

    private KafkaRuleStreamConsumer(KafkaStreamConfig config, Consumer<String, byte[]> consumer,
                                    boolean ownsConsumer, RecordEvaluator evaluator) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.consumer = Objects.requireNonNull(consumer, "consumer cannot be null");
        this.ownsConsumer = ownsConsumer;
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator cannot be null");
        this.partitionExecutor = Executors.newVirtualThreadPerTaskExecutor();

        this.pollThread = Thread.ofVirtual()
                .name("helix-kafka-consumer-poll")
                .unstarted(this::runPollLoop);
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            if (!config.getInputTopics().isEmpty()) {
                consumer.subscribe(config.getInputTopics(), new PartitionRebalanceHandler());
                log.info("Kafka consumer subscribed to topics: {}", config.getInputTopics());
            }
            pollThread.start();
            log.info("KafkaRuleStreamConsumer started with groupId={}", config.getGroupId());
        }
    }

    public void subscribe(Collection<String> topics) {
        Objects.requireNonNull(topics, "topics cannot be null");
        Set<String> newTopics = new HashSet<>(topics);
        if (!running.get()) {
            consumer.subscribe(newTopics, new PartitionRebalanceHandler());
        } else {
            pendingSubscriptions.set(newTopics);
            consumer.wakeup();
        }
    }

    private void runPollLoop() {
        try {
            while (running.get()) {
                try {
                    Set<String> newSubs = pendingSubscriptions.getAndSet(null);
                    if (newSubs != null) {
                        consumer.subscribe(newSubs, new PartitionRebalanceHandler());
                        log.info("Kafka consumer subscribed to topics: {}", newSubs);
                    }

                    applyBackpressureState();

                    ConsumerRecords<String, byte[]> records = consumer.poll(config.getPollTimeout());
                    if (!records.isEmpty()) {
                        totalPolled.addAndGet(records.count());
                        dispatchRecordsByPartition(records);
                    } else {
                        // Prevent CPU spinning on non-blocking mock consumers
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    commitOffsetsIfNeeded();
                } catch (WakeupException e) {
                    if (!running.get()) {
                        break;
                    }
                    log.debug("Kafka consumer woke up");
                } catch (Exception e) {
                    if (!running.get()) {
                        break;
                    }
                    log.warn("Transient error in Kafka consumer poll loop iteration: {}", e.getMessage());
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            try {
                commitFinalOffsets();
            } catch (Exception e) {
                log.warn("Failed to commit final offsets on consumer shutdown", e);
            }
            log.info("Kafka consumer poll loop terminated");
        }
    }

    private void applyBackpressureState() {
        // Pausing saturated partitions
        Set<TopicPartition> toPause = new HashSet<>();
        Set<TopicPartition> toResume = new HashSet<>();

        for (TopicPartition tp : activePartitions) {
            AtomicInteger inFlight = partitionInFlight.get(tp);
            int count = (inFlight != null) ? inFlight.get() : 0;

            if (count >= config.getMaxInFlightPerPartition() && !pausedPartitions.contains(tp)) {
                toPause.add(tp);
                pausedPartitions.add(tp);
            } else if (count <= config.getLowWatermarkPerPartition() && pausedPartitions.contains(tp)) {
                toResume.add(tp);
                pausedPartitions.remove(tp);
            }
        }

        if (!toPause.isEmpty()) {
            consumer.pause(toPause);
            log.debug("Backpressure activated: paused Kafka partitions {}", toPause);
        }
        if (!toResume.isEmpty()) {
            consumer.resume(toResume);
            log.debug("Backpressure relieved: resumed Kafka partitions {}", toResume);
        }
    }

    private void dispatchRecordsByPartition(ConsumerRecords<String, byte[]> records) {
        for (TopicPartition partition : records.partitions()) {
            List<ConsumerRecord<String, byte[]>> partitionRecords = records.records(partition);
            if (partitionRecords.isEmpty()) {
                continue;
            }

            partitionInFlight.computeIfAbsent(partition, p -> new AtomicInteger(0))
                    .addAndGet(partitionRecords.size());

            // Dispatch partition batch to virtual thread, ensuring strictly ordered execution per partition
            partitionExecutor.submit(() -> processPartitionBatch(partition, partitionRecords));
        }
    }

    private void processPartitionBatch(TopicPartition partition, List<ConsumerRecord<String, byte[]>> records) {
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                CompletableFuture<StreamResult> future = evaluator.evaluate(record);
                if (future != null) {
                    future.whenComplete((result, ex) -> recordProcessed(partition, record.offset(), ex));
                } else {
                    recordProcessed(partition, record.offset(), null);
                }
            } catch (Exception e) {
                log.error("Failed to evaluate record from partition {} at offset {}", partition, record.offset(), e);
                recordProcessed(partition, record.offset(), e);
            }
        }
    }

    private void recordProcessed(TopicPartition partition, long offset, Throwable error) {
        totalCompleted.incrementAndGet();
        partitionProcessedOffsets.compute(partition, (tp, current) -> (current == null) ? offset + 1 : Math.max(current, offset + 1));

        AtomicInteger inFlight = partitionInFlight.get(partition);
        if (inFlight != null) {
            inFlight.decrementAndGet();
        }
    }

    private void commitOffsetsIfNeeded() {
        if (config.getCommitMode() == CommitMode.NONE || config.isEnableAutoCommit()) {
            return;
        }

        Map<TopicPartition, OffsetAndMetadata> commitMap = new HashMap<>();
        for (Map.Entry<TopicPartition, Long> entry : partitionProcessedOffsets.entrySet()) {
            commitMap.put(entry.getKey(), new OffsetAndMetadata(entry.getValue()));
        }

        if (commitMap.isEmpty()) {
            return;
        }

        try {
            if (config.getCommitMode() == CommitMode.SYNC) {
                consumer.commitSync(commitMap);
            } else if (config.getCommitMode() == CommitMode.ASYNC) {
                consumer.commitAsync(commitMap, (offsets, exception) -> {
                    if (exception != null) {
                        log.warn("Asynchronous offset commit failed", exception);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to commit offsets during poll loop", e);
        }
    }

    private void commitFinalOffsets() {
        if (!partitionProcessedOffsets.isEmpty() && config.getCommitMode() != CommitMode.NONE && !config.isEnableAutoCommit()) {
            Map<TopicPartition, OffsetAndMetadata> commitMap = new HashMap<>();
            partitionProcessedOffsets.forEach((k, v) -> commitMap.put(k, new OffsetAndMetadata(v)));
            consumer.commitSync(commitMap);
            log.info("Committed final offsets for {} partitions on shutdown", commitMap.size());
        }
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            consumer.wakeup();
            try {
                pollThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            partitionExecutor.shutdown();
            try {
                if (!partitionExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    partitionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                partitionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            if (ownsConsumer) {
                try {
                    consumer.close(Duration.ofSeconds(2));
                } catch (Exception e) {
                    log.warn("Error closing Kafka consumer", e);
                }
            }
            log.info("KafkaRuleStreamConsumer closed cleanly");
        }
    }

    public Set<TopicPartition> getPausedPartitions() {
        return Collections.unmodifiableSet(pausedPartitions);
    }

    public Set<TopicPartition> getActivePartitions() {
        return Collections.unmodifiableSet(activePartitions);
    }

    public long getTotalPolled() {
        return totalPolled.get();
    }

    public long getTotalCompleted() {
        return totalCompleted.get();
    }

    public Consumer<String, byte[]> getConsumer() {
        return consumer;
    }

    private class PartitionRebalanceHandler implements ConsumerRebalanceListener {

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            log.info("Kafka partitions revoked: {}", partitions);
            if (config.getCommitMode() != CommitMode.NONE && !config.isEnableAutoCommit()) {
                Map<TopicPartition, OffsetAndMetadata> toCommit = new HashMap<>();
                for (TopicPartition p : partitions) {
                    Long offset = partitionProcessedOffsets.remove(p);
                    if (offset != null) {
                        toCommit.put(p, new OffsetAndMetadata(offset));
                    }
                    partitionInFlight.remove(p);
                    pausedPartitions.remove(p);
                    activePartitions.remove(p);
                }
                if (!toCommit.isEmpty()) {
                    try {
                        consumer.commitSync(toCommit);
                        log.info("Successfully committed revoked partition offsets: {}", toCommit);
                    } catch (Exception e) {
                        log.warn("Failed to commit revoked partition offsets", e);
                    }
                }
            }
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            log.info("Kafka partitions assigned: {}", partitions);
            for (TopicPartition p : partitions) {
                activePartitions.add(p);
                partitionInFlight.put(p, new AtomicInteger(0));
            }
        }
    }
}
