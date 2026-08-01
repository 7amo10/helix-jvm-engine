package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.RuleCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncExecutorTest {

    private AsyncExecutor asyncExecutor;
    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        asyncExecutor = new AsyncExecutor();
        compiler = new RuleCompiler();
    }

    @AfterEach
    void tearDown() {
        asyncExecutor.close();
    }

    @Test
    @DisplayName("Should execute rule asynchronously and resolve CompletableFuture")
    void testExecuteAsyncSuccess() throws Exception {
        String json = """
                {
                    "name": "AsyncRule",
                    "expression": "a + b * 2",
                    "inputSchema": {
                        "a": "long",
                        "b": "long"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);
        ExecutionContext context = new ExecutionContext(Map.of("a", 10L, "b", 5L));

        CompletableFuture<ExecutionResult> future = asyncExecutor.executeAsync(rule, context);
        ExecutionResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals(20L, result.getResult().orElse(null));

        ExecutorMetrics metrics = asyncExecutor.getMetrics();
        assertEquals(1, metrics.getTotalExecutions());
        assertEquals(1, metrics.getSuccessfulExecutions());
    }

    @Test
    @DisplayName("Should handle timeout during async rule execution")
    void testExecuteAsyncTimeout() throws Exception {
        CompiledRule slowRule = new CompiledRule() {
            @Override
            public String getName() {
                return "SlowRule";
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public ExecutionResult execute(ExecutionContext context) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                return ExecutionResult.success(true, 500_000_000L);
            }
        };

        CompletableFuture<ExecutionResult> future = asyncExecutor.executeAsync(slowRule, new ExecutionContext(), 50, TimeUnit.MILLISECONDS);
        ExecutionResult result = future.get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().isPresent());
        assertTrue(result.getError().get().getMessage().contains("timed out"));
    }
}
