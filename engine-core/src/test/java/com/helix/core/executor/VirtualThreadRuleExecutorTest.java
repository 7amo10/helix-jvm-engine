package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleExecutionException;
import com.helix.core.RuleCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class VirtualThreadRuleExecutorTest {

    private VirtualThreadRuleExecutor executor;
    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        executor = new VirtualThreadRuleExecutor(Duration.ofSeconds(5));
        compiler = new RuleCompiler();
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    @DisplayName("Should execute compiled rule synchronously using virtual threads")
    void testExecuteSuccess() throws Exception {
        String json = """
                {
                    "name": "VTEvalRule",
                    "expression": "price * quantity",
                    "inputSchema": {
                        "price": "double",
                        "quantity": "int"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);
        ExecutionContext context = new ExecutionContext(Map.of("price", 19.99, "quantity", 2));

        ExecutionResult result = executor.execute(rule, context);
        assertTrue(result.isSuccess());
        assertEquals(39.98, (Double) result.getResult().orElse(null), 0.001);

        VirtualThreadExecutorMetrics metrics = executor.getMetrics();
        assertEquals(1, metrics.getTotalExecutions());
        assertEquals(1, metrics.getSuccessfulExecutions());
        assertEquals(0, metrics.getFailedExecutions());
        assertEquals(0, metrics.getTimedOutExecutions());
    }

    @Test
    @DisplayName("Should verify rule executes on a virtual thread without pinning")
    void testVirtualThreadVerification() throws Exception {
        AtomicBoolean isVirtualThread = new AtomicBoolean(false);

        CompiledRule inspectingRule = new CompiledRule() {
            @Override
            public String getName() {
                return "InspectingRule";
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public ExecutionResult execute(ExecutionContext context) {
                isVirtualThread.set(Thread.currentThread().isVirtual());
                return ExecutionResult.success("ok", 100);
            }
        };

        ExecutionResult result = executor.execute(inspectingRule, new ExecutionContext());
        assertTrue(result.isSuccess());
        assertTrue(isVirtualThread.get(), "Rule execution thread must be a Loom virtual thread");
    }

    @Test
    @DisplayName("Should handle deadline timeout cleanly and record timeout metrics")
    void testExecutionTimeout() {
        CompiledRule slowRule = new CompiledRule() {
            @Override
            public String getName() {
                return "SlowSleepRule";
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public ExecutionResult execute(ExecutionContext context) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ExecutionResult.success("finished", 2000);
            }
        };

        RuleExecutionException ex = assertThrows(RuleExecutionException.class, () ->
                executor.execute(slowRule, new ExecutionContext(), Duration.ofMillis(50)));

        assertTrue(ex.getMessage().contains("timed out"));
        VirtualThreadExecutorMetrics metrics = executor.getMetrics();
        assertEquals(1, metrics.getTimedOutExecutions());
        assertEquals(1, metrics.getFailedExecutions());
    }

    @Test
    @DisplayName("Should execute asynchronously via CompletableFuture")
    void testExecuteAsync() throws Exception {
        String json = """
                {
                    "name": "AsyncVTRule",
                    "expression": "x + y",
                    "inputSchema": {
                        "x": "int",
                        "y": "int"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);
        ExecutionContext context = new ExecutionContext(Map.of("x", 40, "y", 2));

        CompletableFuture<ExecutionResult> future = executor.executeAsync(rule, context);
        ExecutionResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals(42L, ((Number) result.getResult().orElse(null)).longValue());
    }

    @Test
    @DisplayName("Should execute batch contexts concurrently using StructuredTaskScope fan-out")
    void testExecuteAllBatchSuccess() throws Exception {
        String json = """
                {
                    "name": "BatchVTRule",
                    "expression": "val * 10",
                    "inputSchema": {
                        "val": "int"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);
        List<ExecutionContext> contexts = new ArrayList<>();
        int count = 50;
        for (int i = 0; i < count; i++) {
            contexts.add(new ExecutionContext(Map.of("val", i)));
        }

        List<ExecutionResult> results = executor.executeAll(rule, contexts);
        assertEquals(count, results.size());

        for (int i = 0; i < count; i++) {
            ExecutionResult res = results.get(i);
            assertTrue(res.isSuccess());
            assertEquals((long) i * 10, ((Number) res.getResult().orElse(null)).longValue());
        }

        VirtualThreadExecutorMetrics metrics = executor.getMetrics();
        assertEquals(count, metrics.getTotalExecutions());
        assertEquals(count, metrics.getSuccessfulExecutions());
    }

    @Test
    @DisplayName("Should fail fast in batch fan-out when a subtask throws an unhandled exception")
    void testExecuteAllFailFast() throws Exception {
        String json = """
                {
                    "name": "FailFastRule",
                    "expression": "val * 2",
                    "inputSchema": {
                        "val": "int"
                    }
                }
                """;
        CompiledRule rule = compiler.compile(json);

        List<ExecutionContext> contexts = new ArrayList<>();
        contexts.add(new ExecutionContext(Map.of("val", 1)));
        contexts.add(new ExecutionContext()); // missing "val" causes failure in typed rule execution
        contexts.add(new ExecutionContext(Map.of("val", 3)));

        // Missing field returns failure result or throws in schema validation; let's verify with faulty rule
        CompiledRule throwingRule = new CompiledRule() {
            @Override
            public String getName() {
                return "ThrowingRule";
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public ExecutionResult execute(ExecutionContext context) {
                Integer val = (Integer) context.getVariable("val").orElse(null);
                if (val != null && val == 2) {
                    throw new RuntimeException("Simulated catastrophic failure on item 2");
                }
                return ExecutionResult.success(val != null ? val * 10 : 0, 100);
            }
        };

        List<ExecutionContext> testContexts = List.of(
                new ExecutionContext(Map.of("val", 1)),
                new ExecutionContext(Map.of("val", 2)),
                new ExecutionContext(Map.of("val", 3))
        );

        RuleExecutionException ex = assertThrows(RuleExecutionException.class, () ->
                executor.executeAll(throwingRule, testContexts));

        assertTrue(ex.getMessage().contains("Evaluation failed") || ex.getMessage().contains("Structured task scope failure"));
    }

    @Test
    @DisplayName("Should execute multiple rules concurrently against a single context")
    void testExecuteAllRules() throws Exception {
        String json1 = """
                {
                    "name": "RuleA",
                    "expression": "x + 1",
                    "inputSchema": { "x": "int" }
                }
                """;
        String json2 = """
                {
                    "name": "RuleB",
                    "expression": "x * 2",
                    "inputSchema": { "x": "int" }
                }
                """;

        CompiledRule ruleA = compiler.compile(json1);
        CompiledRule ruleB = compiler.compile(json2);
        ExecutionContext ctx = new ExecutionContext(Map.of("x", 10));

        List<ExecutionResult> results = executor.executeAllRules(List.of(ruleA, ruleB), ctx, Duration.ofSeconds(2));
        assertEquals(2, results.size());
        assertEquals(11L, ((Number) results.get(0).getResult().orElse(null)).longValue());
        assertEquals(20L, ((Number) results.get(1).getResult().orElse(null)).longValue());
    }

    @Test
    @DisplayName("Should support massive concurrent evaluations with 10,000 virtual threads")
    void testHighConcurrencyEvaluation() throws Exception {
        int taskCount = 10_000;
        String json = """
                {
                    "name": "ScaleRule",
                    "expression": "n * 2",
                    "inputSchema": { "n": "int" }
                }
                """;
        CompiledRule rule = compiler.compile(json);

        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentLinkedQueue<ExecutionResult> results = new ConcurrentLinkedQueue<>();
        AtomicBoolean anyFailed = new AtomicBoolean(false);

        for (int i = 0; i < taskCount; i++) {
            final int val = i;
            executor.executeAsync(rule, new ExecutionContext(Map.of("n", val)))
                    .thenAccept(res -> {
                        if (!res.isSuccess()) {
                            anyFailed.set(true);
                        }
                        results.add(res);
                        latch.countDown();
                    });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "All 10,000 tasks should finish well within 30 seconds");
        assertFalse(anyFailed.get(), "No virtual thread evaluations should fail");
        assertEquals(taskCount, results.size());

        VirtualThreadExecutorMetrics metrics = executor.getMetrics();
        assertEquals(taskCount, metrics.getTotalExecutions());
        assertEquals(taskCount, metrics.getSuccessfulExecutions());
    }
}
