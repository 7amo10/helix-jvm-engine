package com.helix.core.stream.kafka;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.StreamListener;
import com.helix.api.stream.StreamRecord;
import com.helix.api.stream.StreamResult;
import com.helix.api.stream.StreamStats;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class KafkaStreamEngineTest {

    @Test
    @DisplayName("Should validate KafkaStreamConfig and generate valid properties")
    void testConfigValidationAndDefaults() {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("helix-test-group")
                .inputTopics(Set.of("topic-a", "topic-b"))
                .outputTopic("topic-out")
                .commitMode(CommitMode.SYNC)
                .maxInFlightPerPartition(50)
                .lowWatermarkPerPartition(20)
                .pollTimeout(Duration.ofMillis(200))
                .build();

        assertEquals("localhost:9092", config.getBootstrapServers());
        assertEquals("helix-test-group", config.getGroupId());
        assertTrue(config.getInputTopics().contains("topic-a"));
        assertTrue(config.getInputTopics().contains("topic-b"));
        assertEquals("topic-out", config.getOutputTopic());
        assertEquals(CommitMode.SYNC, config.getCommitMode());
        assertEquals(50, config.getMaxInFlightPerPartition());
        assertEquals(20, config.getLowWatermarkPerPartition());
        assertEquals(Duration.ofMillis(200), config.getPollTimeout());

        var consumerProps = config.toConsumerProperties();
        assertEquals("localhost:9092", consumerProps.get("bootstrap.servers"));
        assertEquals("helix-test-group", consumerProps.get("group.id"));
        assertEquals("false", consumerProps.get("enable.auto.commit"));

        var producerProps = config.toProducerProperties();
        assertEquals("localhost:9092", producerProps.get("bootstrap.servers"));

        // Validation errors
        assertThrows(IllegalArgumentException.class, () ->
                KafkaStreamConfig.builder().bootstrapServers("").groupId("g").build());
        assertThrows(IllegalArgumentException.class, () ->
                KafkaStreamConfig.builder().bootstrapServers("loc").groupId("").build());
        assertThrows(IllegalArgumentException.class, () ->
                KafkaStreamConfig.builder().bootstrapServers("loc").groupId("g").lowWatermarkPerPartition(10).maxInFlightPerPartition(5).build());
    }

    @Test
    @DisplayName("Should encode and decode RuleEvent and StreamResult")
    void testEventCodec() {
        ExecutionContext ctx = new ExecutionContext(Map.of("score", 95, "user", "alice"));
        RuleEvent event = RuleEvent.of("fraud-topic", "FraudRule", ctx);

        byte[] eventBytes = KafkaEventCodec.serializeEvent(event);
        assertNotNull(eventBytes);

        RuleEvent deserialized = KafkaEventCodec.deserializeEvent("fraud-topic", "key-1", eventBytes, null);
        assertNotNull(deserialized);
        assertEquals("FraudRule", deserialized.getRuleName());
        assertEquals(95, ((Number) deserialized.getContext().getVariable("score").orElse(0)).intValue());
        assertEquals("alice", deserialized.getContext().getVariable("user").orElse(""));

        StreamResult successResult = StreamResult.success("ev-1", "fraud-topic", "FraudRule", "PASSED", 1500L);
        byte[] successBytes = KafkaEventCodec.serializeResult(successResult);
        StreamResult deserializedResult = KafkaEventCodec.deserializeResult(successBytes);
        assertTrue(deserializedResult.isSuccess());
        assertEquals("PASSED", deserializedResult.getResult().orElse(null));
        assertEquals("FraudRule", deserializedResult.getRuleName());

        StreamResult failResult = StreamResult.failure("ev-2", "fraud-topic", "FraudRule",
                new IllegalStateException("Validation error"), 2500L);
        byte[] failBytes = KafkaEventCodec.serializeResult(failResult);
        StreamResult deserializedFail = KafkaEventCodec.deserializeResult(failBytes);
        assertFalse(deserializedFail.isSuccess());
        assertTrue(deserializedFail.getError().isPresent());
        assertTrue(deserializedFail.getError().get().getMessage().contains("Validation error"));
    }

    @Test
    @DisplayName("Should consume from multiple partitions in order and publish rule results")
    void testMultiPartitionConsumptionAndRuleExecution() throws Exception {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-multi-part")
                .inputTopics(Set.of("orders"))
                .outputTopic("order-results")
                .commitMode(CommitMode.SYNC)
                .pollTimeout(Duration.ofMillis(20))
                .build();

        MockConsumer<String, byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        TopicPartition tp0 = new TopicPartition("orders", 0);
        TopicPartition tp1 = new TopicPartition("orders", 1);

        Map<TopicPartition, Long> beginningOffsets = new HashMap<>();
        beginningOffsets.put(tp0, 0L);
        beginningOffsets.put(tp1, 0L);
        mockConsumer.updateBeginningOffsets(beginningOffsets);

        KafkaStreamEngine engine = new KafkaStreamEngine(config, mockConsumer, mockProducer);

        // Register a compiled rule
        CompiledRule orderRule = new CompiledRule() {
            @Override
            public String getName() {
                return "OrderValidation";
            }

            @Override
            public String getVersion() {
                return "1.0";
            }

            @Override
            public ExecutionResult execute(ExecutionContext ctx) {
                int amount = ((Number) ctx.getVariable("amount").orElse(0)).intValue();
                return amount > 100 ? ExecutionResult.success("APPROVED", 50L) : ExecutionResult.success("REJECTED", 50L);
            }
        };
        engine.registerRule("orders", orderRule);

        mockConsumer.rebalance(List.of(tp0, tp1));

        // Enqueue records into partition 0 and partition 1
        ExecutionContext ctx0_1 = new ExecutionContext(Map.of("amount", 250));
        RuleEvent event0_1 = new RuleEvent("ev-0-1", "orders", "OrderValidation", ctx0_1, System.currentTimeMillis(), Collections.emptyMap());
        mockConsumer.addRecord(new ConsumerRecord<>("orders", 0, 0L, "key-0-1", KafkaEventCodec.serializeEvent(event0_1)));

        ExecutionContext ctx0_2 = new ExecutionContext(Map.of("amount", 50));
        RuleEvent event0_2 = new RuleEvent("ev-0-2", "orders", "OrderValidation", ctx0_2, System.currentTimeMillis(), Collections.emptyMap());
        mockConsumer.addRecord(new ConsumerRecord<>("orders", 0, 1L, "key-0-2", KafkaEventCodec.serializeEvent(event0_2)));

        ExecutionContext ctx1_1 = new ExecutionContext(Map.of("amount", 300));
        RuleEvent event1_1 = new RuleEvent("ev-1-1", "orders", "OrderValidation", ctx1_1, System.currentTimeMillis(), Collections.emptyMap());
        mockConsumer.addRecord(new ConsumerRecord<>("orders", 1, 0L, "key-1-1", KafkaEventCodec.serializeEvent(event1_1)));

        // Await processing
        assertTrue(waitForCondition(() -> mockProducer.history().size() >= 3, 5000), "Expected 3 produced results");

        List<ProducerRecord<String, byte[]>> history = mockProducer.history();
        assertEquals(3, history.size());

        // Verify output topic
        assertEquals("order-results", history.get(0).topic());

        // Decode first result
        StreamResult res1 = KafkaEventCodec.deserializeResult(history.get(0).value());
        assertTrue(res1.isSuccess());

        StreamStats stats = engine.stats();
        assertTrue(stats.getTotalProcessed() >= 3);

        engine.shutdown();
    }

    @Test
    @DisplayName("Should trigger backpressure pause and resume on high and low watermarks")
    void testBackpressurePauseResume() throws Exception {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-backpressure")
                .inputTopics(Set.of("sensor-stream"))
                .maxInFlightPerPartition(2)
                .lowWatermarkPerPartition(1)
                .pollTimeout(Duration.ofMillis(10))
                .build();

        MockConsumer<String, byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        TopicPartition tp = new TopicPartition("sensor-stream", 0);
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));

        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch inHandlerLatch = new CountDownLatch(1);

        KafkaStreamEngine engine = new KafkaStreamEngine(config, mockConsumer, mockProducer);

        engine.subscribe("sensor-stream", event -> {
            inHandlerLatch.countDown();
            try {
                blockLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StreamResult.success(event.getEventId(), event.getTopic(), "SensorRule", "OK", 10);
        });

        mockConsumer.rebalance(List.of(tp));

        // Add 2 records to trigger max in flight (batch size 2 >= max 2)
        for (int i = 0; i < 2; i++) {
            RuleEvent ev = RuleEvent.of("sensor-stream", "SensorRule", new ExecutionContext(Map.of("val", i)));
            mockConsumer.addRecord(new ConsumerRecord<>("sensor-stream", 0, (long) i, "k" + i, KafkaEventCodec.serializeEvent(ev)));
        }

        // Wait until first record reaches handler
        assertTrue(inHandlerLatch.await(5, TimeUnit.SECONDS), "First record should enter handler");

        // Pause should be triggered during poll loop
        assertTrue(waitForCondition(() -> mockConsumer.paused().contains(tp), 3000), "Partition should be paused under backpressure");

        // Release the latch to allow records to complete
        blockLatch.countDown();

        // Once processed, backpressure relieves and partition resumes
        assertTrue(waitForCondition(() -> !mockConsumer.paused().contains(tp), 3000), "Partition should be resumed when backpressure relieves");

        engine.shutdown();
    }

    @Test
    @DisplayName("Should commit offsets synchronously or asynchronously according to CommitMode")
    void testCommitModes() throws Exception {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-commit-sync")
                .inputTopics(Set.of("commit-topic"))
                .commitMode(CommitMode.SYNC)
                .pollTimeout(Duration.ofMillis(10))
                .build();

        MockConsumer<String, byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        TopicPartition tp = new TopicPartition("commit-topic", 0);
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));

        KafkaStreamEngine engine = new KafkaStreamEngine(config, mockConsumer, mockProducer);
        engine.subscribe("commit-topic", event ->
                StreamResult.success(event.getEventId(), event.getTopic(), "Rule", "DONE", 5));

        mockConsumer.rebalance(List.of(tp));

        RuleEvent ev = RuleEvent.of("commit-topic", "Rule", new ExecutionContext());
        mockConsumer.addRecord(new ConsumerRecord<>("commit-topic", 0, 0L, "k", KafkaEventCodec.serializeEvent(ev)));

        // Offset should be committed as 1 (next offset after 0)
        assertTrue(waitForCondition(() -> {
            var committed = mockConsumer.committed(Set.of(tp)).get(tp);
            return committed != null && committed.offset() == 1L;
        }, 5000), "Committed offset should be 1");

        engine.shutdown();
    }

    @Test
    @DisplayName("Should commit offsets asynchronously when CommitMode is ASYNC")
    void testCommitModeAsync() throws Exception {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-commit-async")
                .inputTopics(Set.of("async-commit-topic"))
                .commitMode(CommitMode.ASYNC)
                .pollTimeout(Duration.ofMillis(10))
                .build();

        MockConsumer<String, byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        TopicPartition tp = new TopicPartition("async-commit-topic", 0);
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));

        KafkaStreamEngine engine = new KafkaStreamEngine(config, mockConsumer, mockProducer);
        engine.subscribe("async-commit-topic", event ->
                StreamResult.success(event.getEventId(), event.getTopic(), "AsyncRule", "SUCCESS", 5));

        mockConsumer.rebalance(List.of(tp));

        RuleEvent ev = RuleEvent.of("async-commit-topic", "AsyncRule", new ExecutionContext());
        mockConsumer.addRecord(new ConsumerRecord<>("async-commit-topic", 0, 0L, "k", KafkaEventCodec.serializeEvent(ev)));

        assertTrue(waitForCondition(() -> {
            var committed = mockConsumer.committed(Set.of(tp)).get(tp);
            return committed != null && committed.offset() == 1L;
        }, 5000), "Committed offset should be 1 with ASYNC mode");

        engine.shutdown();
    }

    @Test
    @DisplayName("Should not commit offsets when CommitMode is NONE")
    void testCommitModeNone() throws Exception {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-commit-none")
                .inputTopics(Set.of("none-commit-topic"))
                .commitMode(CommitMode.NONE)
                .pollTimeout(Duration.ofMillis(10))
                .build();

        MockConsumer<String, byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        TopicPartition tp = new TopicPartition("none-commit-topic", 0);
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));

        KafkaStreamEngine engine = new KafkaStreamEngine(config, mockConsumer, mockProducer);
        CountDownLatch latch = new CountDownLatch(1);
        engine.subscribe("none-commit-topic", event -> {
            latch.countDown();
            return StreamResult.success(event.getEventId(), event.getTopic(), "NoneRule", "OK", 5);
        });

        mockConsumer.rebalance(List.of(tp));

        RuleEvent ev = RuleEvent.of("none-commit-topic", "NoneRule", new ExecutionContext());
        mockConsumer.addRecord(new ConsumerRecord<>("none-commit-topic", 0, 0L, "k", KafkaEventCodec.serializeEvent(ev)));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);

        var committed = mockConsumer.committed(Set.of(tp)).get(tp);
        assertNull(committed, "Offsets should not be committed when CommitMode is NONE");

        engine.shutdown();
    }

    @Test
    @DisplayName("Should forward StreamResult to subscribed StreamListeners")
    void testStreamListeners() throws Exception {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-listeners")
                .inputTopics(Set.of("events"))
                .pollTimeout(Duration.ofMillis(10))
                .build();

        MockConsumer<String, byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        TopicPartition tp = new TopicPartition("events", 0);
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));

        KafkaStreamEngine engine = new KafkaStreamEngine(config, mockConsumer, mockProducer);
        engine.subscribe("events", ev -> StreamResult.success(ev.getEventId(), "events", "R1", "VAL", 100));

        CountDownLatch listenerLatch = new CountDownLatch(1);
        AtomicReference<StreamResult> receivedResult = new AtomicReference<>();
        engine.subscribeListener("events", res -> {
            receivedResult.set(res);
            listenerLatch.countDown();
        });

        mockConsumer.rebalance(List.of(tp));

        RuleEvent ev = RuleEvent.of("events", "R1", new ExecutionContext());
        mockConsumer.addRecord(new ConsumerRecord<>("events", 0, 0L, "k", KafkaEventCodec.serializeEvent(ev)));

        assertTrue(listenerLatch.await(5, TimeUnit.SECONDS));
        assertNotNull(receivedResult.get());
        assertEquals("VAL", receivedResult.get().getResult().orElse(null));

        engine.shutdown();
    }

    @Test
    @DisplayName("Should publish StreamRecord and RuleEvent via producer")
    void testPublishAndTryPublish() throws Exception {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-publish")
                .build();

        MockConsumer<String, byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        KafkaStreamEngine engine = new KafkaStreamEngine(config, mockConsumer, mockProducer);

        // Test publishing RuleEvent
        RuleEvent event = RuleEvent.of("pub-topic", "PubRule", new ExecutionContext(Map.of("user", "bob")));
        CompletableFuture<StreamResult> pubFuture = engine.publish(event);
        assertNotNull(pubFuture);

        // Test publishing StreamRecord
        StreamRecord<String> record = StreamRecord.of("pub-topic", "key-42", "payload-data");
        CompletableFuture<StreamResult> recordFuture = engine.publish(record);
        assertNotNull(recordFuture);

        // Test tryPublish
        boolean published = engine.tryPublish(RuleEvent.of("pub-topic", "PubRule", new ExecutionContext()));
        assertTrue(published);

        assertEquals(3, mockProducer.history().size());

        engine.shutdown();
    }

    @Test
    @DisplayName("Should commit pending offsets on partition revocation during rebalance")
    void testPartitionRevocationCommitsOffsets() throws Exception {
        KafkaStreamConfig config = KafkaStreamConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-rebalance-revocation")
                .inputTopics(Set.of("rebalance-topic"))
                .commitMode(CommitMode.SYNC)
                .pollTimeout(Duration.ofMillis(10))
                .build();

        MockConsumer<String, byte[]> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        TopicPartition tp = new TopicPartition("rebalance-topic", 0);
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));

        KafkaStreamEngine engine = new KafkaStreamEngine(config, mockConsumer, mockProducer);
        engine.subscribe("rebalance-topic", ev ->
                StreamResult.success(ev.getEventId(), ev.getTopic(), "RebalRule", "PROCESSED", 10));

        mockConsumer.rebalance(List.of(tp));

        RuleEvent ev = RuleEvent.of("rebalance-topic", "RebalRule", new ExecutionContext());
        mockConsumer.addRecord(new ConsumerRecord<>("rebalance-topic", 0, 0L, "k", KafkaEventCodec.serializeEvent(ev)));

        // Wait until record is processed by engine
        assertTrue(waitForCondition(() -> engine.stats().getTotalProcessed() >= 1, 3000));

        // Trigger partition revocation by removing tp and assigning tp1
        TopicPartition tp1 = new TopicPartition("rebalance-topic", 1);
        mockConsumer.updateBeginningOffsets(Map.of(tp1, 0L));
        mockConsumer.rebalance(List.of(tp1));

        // Re-assign tp to allow MockConsumer to report committed offset
        mockConsumer.rebalance(List.of(tp, tp1));

        // Offsets should have been committed upon revocation
        var committed = mockConsumer.committed(Set.of(tp)).get(tp);
        assertNotNull(committed, "Committed offset should exist upon revocation");
        assertEquals(1L, committed.offset());

        engine.shutdown();
    }

    private boolean waitForCondition(java.util.function.BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }
}
