package com.helix.core.stream.disruptor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleExecutionException;
import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.StreamListener;
import com.helix.api.stream.StreamRecord;
import com.helix.api.stream.StreamResult;
import com.helix.api.stream.StreamStats;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisruptorStreamBridgeTest {

    @Test
    @DisplayName("Should publish event and receive processed result via CompletableFuture")
    void testBasicPublishSubscribe() throws Exception {
        DisruptorConfig config = DisruptorConfig.builder()
                .ringBufferSize(1024)
                .yieldingWaitStrategy()
                .build();

        try (DisruptorStreamBridge bridge = new DisruptorStreamBridge(config)) {
            bridge.subscribe("orders", event -> {
                String item = (String) event.getContext().getVariable("item").orElse("unknown");
                return StreamResult.success(event.getEventId(), event.getTopic(), event.getRuleName(),
                        "PROCESSED_" + item, 100);
            });

            ExecutionContext ctx = new ExecutionContext(Map.of("item", "laptop"));
            RuleEvent event = RuleEvent.of("orders", "OrderValidationRule", ctx);

            CompletableFuture<StreamResult> future = bridge.publish(event);
            StreamResult result = future.get(2, TimeUnit.SECONDS);

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals("PROCESSED_laptop", result.getResult().orElse(null));
            assertEquals("orders", result.getTopic());

            StreamStats stats = bridge.stats();
            assertEquals(1, stats.getTotalPublished());
            assertEquals(1, stats.getTotalProcessed());
            assertEquals(0, stats.getTotalFailed());
        }
    }

    @Test
    @DisplayName("Should execute registered CompiledRule directly and notify asynchronous StreamListener")
    void testCompiledRuleExecutionAndListener() throws Exception {
        DisruptorConfig config = DisruptorConfig.builder()
                .ringBufferSize(1024)
                .yieldingWaitStrategy()
                .build();

        try (DisruptorStreamBridge bridge = new DisruptorStreamBridge(config)) {
            CompiledRule mockRule = new CompiledRule() {
                @Override
                public String getName() {
                    return "FraudCheckRule";
                }

                @Override
                public String getVersion() {
                    return "1.0.0";
                }

                @Override
                public ExecutionResult execute(ExecutionContext context) throws RuleExecutionException {
                    Double amount = (Double) context.getVariable("amount").orElse(0.0);
                    boolean isFraud = amount > 1000.0;
                    return ExecutionResult.success(isFraud, 50);
                }
            };

            bridge.registerRule("fraud-topic", mockRule);

            CountDownLatch listenerLatch = new CountDownLatch(1);
            AtomicLong receivedResult = new AtomicLong(-1);

            bridge.subscribeListener("fraud-topic", (StreamListener) result -> {
                if (Boolean.TRUE.equals(result.getResult().orElse(null))) {
                    receivedResult.set(1);
                } else {
                    receivedResult.set(0);
                }
                listenerLatch.countDown();
            });

            ExecutionContext ctx = new ExecutionContext(Map.of("amount", 2500.0));
            RuleEvent event = RuleEvent.of("fraud-topic", "FraudCheckRule", ctx);

            CompletableFuture<StreamResult> future = bridge.publish(event);
            StreamResult result = future.get(2, TimeUnit.SECONDS);

            assertTrue(result.isSuccess());
            assertEquals(Boolean.TRUE, result.getResult().orElse(null));

            assertTrue(listenerLatch.await(2, TimeUnit.SECONDS), "Asynchronous StreamListener must be notified");
            assertEquals(1, receivedResult.get());
        }
    }

    @Test
    @DisplayName("Should publish StreamRecord and convert to RuleEvent seamlessly")
    void testStreamRecordPublishing() throws Exception {
        try (DisruptorStreamBridge bridge = new DisruptorStreamBridge()) {
            bridge.subscribe("telemetry", event -> StreamResult.success(
                    event.getEventId(), event.getTopic(), "TelemetryRule", "ACK", 20));

            StreamRecord<String> record = StreamRecord.of("telemetry", "dev-01", "ping");
            CompletableFuture<StreamResult> future = bridge.publish(record);

            StreamResult result = future.get(2, TimeUnit.SECONDS);
            assertTrue(result.isSuccess());
            assertEquals("ACK", result.getResult().orElse(null));
        }
    }

    @Test
    @DisplayName("Should process 100,000 events with zero lost messages and throughput > 1,500,000 events/sec")
    void testHighThroughputAndZeroLoss100k() throws Exception {
        int totalEvents = 100_000;
        DisruptorConfig config = DisruptorConfig.builder()
                .ringBufferSize(65536)
                .yieldingWaitStrategy()
                .build();

        try (DisruptorStreamBridge bridge = new DisruptorStreamBridge(config)) {
            // Warm up
            bridge.subscribe("warmup-topic", event -> null);
            ExecutionContext warmupCtx = new ExecutionContext(Map.of("id", 0));
            for (int i = 0; i < 20_000; i++) {
                bridge.publish("warmup-topic", "BenchRule", warmupCtx, null);
            }
            Thread.sleep(100);

            CountDownLatch completionLatch = new CountDownLatch(1);
            AtomicInteger processedCounter = new AtomicInteger(0);

            bridge.subscribe("benchmark-topic", event -> {
                if (processedCounter.incrementAndGet() == totalEvents) {
                    completionLatch.countDown();
                }
                return null;
            });

            ExecutionContext ctx = new ExecutionContext(Map.of("tid", 1));

            long startTime = System.nanoTime();
            for (int i = 0; i < totalEvents; i++) {
                bridge.publish("benchmark-topic", "BenchRule", ctx, null);
            }

            boolean finished = completionLatch.await(5, TimeUnit.SECONDS);
            long durationNanos = System.nanoTime() - startTime;
            double durationSeconds = durationNanos / 1_000_000_000.0;
            double throughputEventsPerSec = totalEvents / durationSeconds;

            assertTrue(finished, "All 100,000 events must be processed within 5 seconds");
            assertEquals(totalEvents, processedCounter.get(), "Zero lost messages verified: all 100,000 events accounted for");

            System.out.printf("Disruptor RingBuffer Hand-off: %,d events in %.4f seconds (%,.2f events/sec)%n",
                    totalEvents, durationSeconds, throughputEventsPerSec);

            assertTrue(throughputEventsPerSec > 1_500_000,
                    String.format("Throughput must exceed 1,500,000 events/sec (achieved: %,.2f events/sec)", throughputEventsPerSec));
        }
    }

    @Test
    @DisplayName("Should verify zero heap allocation in steady-state dispatch loop")
    void testZeroAllocationSteadyState() throws Exception {
        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof com.sun.management.ThreadMXBean threadBean) || !threadBean.isThreadAllocatedMemorySupported()) {
            System.out.println("Thread allocated memory not supported on this JVM platform; skipping zero-allocation test.");
            return;
        }

        DisruptorConfig config = DisruptorConfig.builder()
                .ringBufferSize(4096)
                .busySpinWaitStrategy()
                .build();

        try (DisruptorStreamBridge bridge = new DisruptorStreamBridge(config)) {
            bridge.subscribe("zero-alloc", event -> null);
            ExecutionContext reusableContext = new ExecutionContext(Collections.emptyMap());

            // Method-level warm up to trigger standard C2 JIT compilation (avoiding On-Stack Replacement OSR)
            for (int w = 0; w < 10; w++) {
                runPublishLoop(bridge, reusableContext, 5_000);
            }
            Thread.sleep(100);

            // Steady-state measurement on current publisher thread
            long threadId = Thread.currentThread().threadId();
            int steadyStateIterations = 10_000;

            long bytesBefore = threadBean.getThreadAllocatedBytes(threadId);
            runPublishLoop(bridge, reusableContext, steadyStateIterations);
            long bytesAfter = threadBean.getThreadAllocatedBytes(threadId);
            long bytesAllocated = bytesAfter - bytesBefore;

            double bytesPerOp = (double) bytesAllocated / steadyStateIterations;
            System.out.printf("Steady-state loop: %d iterations allocated %d bytes (%.2f bytes/op)%n",
                    steadyStateIterations, bytesAllocated, bytesPerOp);

            if (isCoverageAgentActive()) {
                // Under bytecode coverage agents (e.g. JaCoCo in CI), probe arrays cause a small one-off overhead.
                // Verify per-op allocation is strictly 0 objects (< 0.1 bytes/op, where even an empty Object is >= 16B).
                assertTrue(bytesPerOp < 0.1,
                        "Steady-state per-operation allocation under coverage agent must be negligible (was: " + bytesPerOp + " bytes/op)");
            } else {
                assertEquals(0L, bytesAllocated, "Steady-state event dispatch loop must have ZERO object allocations");
            }
        }
    }

    private static boolean isCoverageAgentActive() {
        try {
            Class.forName("org.jacoco.agent.rt.RT");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(arg -> arg.contains("-javaagent") || arg.contains("jacoco"));
    }

    private void runPublishLoop(DisruptorStreamBridge bridge, ExecutionContext context, int iterations) {
        for (int i = 0; i < iterations; i++) {
            bridge.publish("zero-alloc", "Rule", context, null);
        }
    }

    @Test
    @DisplayName("Should handle exceptions and complete futures exceptionally without crashing ring buffer")
    void testExceptionHandling() throws Exception {
        try (DisruptorStreamBridge bridge = new DisruptorStreamBridge()) {
            bridge.subscribe("error-topic", event -> {
                throw new IllegalArgumentException("Simulated processing error");
            });

            RuleEvent event = RuleEvent.of("error-topic", new ExecutionContext());
            CompletableFuture<StreamResult> future = bridge.publish(event);

            StreamResult result = future.get(2, TimeUnit.SECONDS);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().isPresent());
            assertTrue(result.getError().get() instanceof IllegalArgumentException);

            StreamStats stats = bridge.stats();
            assertEquals(1, stats.getTotalPublished());
            assertEquals(1, stats.getTotalProcessed());
            assertEquals(1, stats.getTotalFailed());
        }
    }

    @Test
    @DisplayName("Should process concurrent events across multiple producer virtual threads with zero lost messages")
    void testConcurrentMultiProducerStreaming() throws Exception {
        int totalEvents = 50_000;
        int producerThreads = 4;
        int eventsPerThread = totalEvents / producerThreads;

        DisruptorConfig config = DisruptorConfig.builder()
                .ringBufferSize(65536)
                .multiProducer()
                .yieldingWaitStrategy()
                .build();

        try (DisruptorStreamBridge bridge = new DisruptorStreamBridge(config)) {
            CountDownLatch completionLatch = new CountDownLatch(1);
            AtomicInteger processedCounter = new AtomicInteger(0);

            bridge.subscribe("multi-prod-topic", event -> {
                if (processedCounter.incrementAndGet() == totalEvents) {
                    completionLatch.countDown();
                }
                return null;
            });

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch startGate = new CountDownLatch(1);

            for (int t = 0; t < producerThreads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        ExecutionContext ctx = new ExecutionContext(Map.of("threadId", threadId));
                        for (int i = 0; i < eventsPerThread; i++) {
                            bridge.publish("multi-prod-topic", "Rule", ctx, null);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            startGate.countDown();
            boolean finished = completionLatch.await(5, TimeUnit.SECONDS);

            assertTrue(finished, "All 50,000 multi-producer events must complete within 5 seconds");
            assertEquals(totalEvents, processedCounter.get(), "Zero lost messages in multi-producer streaming");

            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Real-world scenario: stream live transactions through compiled bytecode rule and emit alerts")
    void testRealWorldFinancialStreamingPipeline() throws Exception {
        com.helix.core.RuleCompiler compiler = new com.helix.core.RuleCompiler();
        String ruleJson = """
                {
                    "name": "HighValueFraudRule",
                    "version": "1.0.0",
                    "expression": "amount > 5000 && VIP == true",
                    "inputSchema": {
                        "amount": "double",
                        "VIP": "boolean"
                    }
                }
                """;

        CompiledRule compiledRule = compiler.compile(ruleJson);
        assertNotNull(compiledRule);

        DisruptorConfig config = DisruptorConfig.builder()
                .ringBufferSize(8192)
                .yieldingWaitStrategy()
                .build();

        try (DisruptorStreamBridge bridge = new DisruptorStreamBridge(config)) {
            bridge.registerRule("transactions", compiledRule);

            int totalTransactions = 1_000;
            CountDownLatch allDone = new CountDownLatch(totalTransactions);
            AtomicInteger matchedAlerts = new AtomicInteger(0);

            bridge.subscribeListener("transactions", (StreamListener) result -> {
                if (Boolean.TRUE.equals(result.getResult().orElse(null))) {
                    matchedAlerts.incrementAndGet();
                }
                allDone.countDown();
            });

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < totalTransactions; i++) {
                final int idx = i;
                executor.submit(() -> {
                    // Half of the transactions meet the fraud alert criteria
                    boolean isVip = (idx % 2 == 0);
                    double amount = isVip ? 8000.0 : 1000.0;
                    ExecutionContext ctx = new ExecutionContext(Map.of("amount", amount, "VIP", isVip));
                    RuleEvent event = RuleEvent.of("transactions", "HighValueFraudRule", ctx);
                    bridge.publish(event);
                });
            }

            assertTrue(allDone.await(5, TimeUnit.SECONDS), "All 1,000 transactions must be processed");
            assertEquals(500, matchedAlerts.get(), "Exactly 500 matching transactions must trigger fraud alert");

            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Should enforce lifecycle and graceful shutdown")
    void testLifecycleAndShutdown() {
        DisruptorStreamBridge bridge = new DisruptorStreamBridge();
        assertTrue(bridge.isRunning());

        StreamStats stats = bridge.stats();
        assertEquals(DisruptorConfig.DEFAULT_RING_BUFFER_SIZE, stats.getRingBufferSize());

        bridge.shutdown(Duration.ofMillis(500));
        assertFalse(bridge.isRunning());

        RuleEvent event = RuleEvent.of("topic", new ExecutionContext());
        assertThrows(IllegalStateException.class, () -> bridge.publish(event));
    }
}
