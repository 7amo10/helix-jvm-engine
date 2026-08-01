package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.RuleCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatchExecutorTest {

    private BatchExecutor batchExecutor;
    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        batchExecutor = new BatchExecutor(4);
        compiler = new RuleCompiler();
    }

    @Test
    @DisplayName("Should execute batch of contexts in parallel and preserve ordering")
    void testBatchExecutionSuccess() throws Exception {
        String json = """
                {
                    "name": "BatchRule",
                    "expression": "val * 2",
                    "inputSchema": {
                        "val": "long"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);

        List<ExecutionContext> contexts = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            contexts.add(new ExecutionContext(Map.of("val", i)));
        }

        List<ExecutionResult> results = batchExecutor.executeBatch(rule, contexts);
        assertEquals(100, results.size());

        for (int i = 0; i < 100; i++) {
            ExecutionResult res = results.get(i);
            assertTrue(res.isSuccess());
            assertEquals((long) (i + 1) * 2, res.getResult().orElse(null));
        }

        ExecutorMetrics metrics = batchExecutor.getMetrics();
        assertEquals(100, metrics.getTotalExecutions());
        assertEquals(100, metrics.getSuccessfulExecutions());
    }

    @Test
    @DisplayName("Should isolate context errors without failing whole batch")
    void testBatchExecutionErrorIsolation() throws Exception {
        String json = """
                {
                    "name": "ValidationRule",
                    "expression": "x > 10",
                    "inputSchema": {
                        "x": "int"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);

        List<ExecutionContext> contexts = List.of(
                new ExecutionContext(Map.of("x", 15)),
                new ExecutionContext(), // Missing variable -> failure
                new ExecutionContext(Map.of("x", 5))
        );

        List<ExecutionResult> results = batchExecutor.executeBatch(rule, contexts);
        assertEquals(3, results.size());

        assertTrue(results.get(0).isSuccess());
        assertEquals(Boolean.TRUE, results.get(0).getResult().orElse(null));

        assertFalse(results.get(1).isSuccess());

        assertTrue(results.get(2).isSuccess());
        assertEquals(Boolean.FALSE, results.get(2).getResult().orElse(null));
    }

    @Test
    @DisplayName("Should return empty list for empty or null context input")
    void testEmptyContextList() throws Exception {
        String json = """
                {
                    "name": "DummyRule",
                    "expression": "true"
                }
                """;
        CompiledRule rule = compiler.compile(json);

        assertTrue(batchExecutor.executeBatch(rule, List.of()).isEmpty());
        assertTrue(batchExecutor.executeBatch(rule, null).isEmpty());
    }
}
