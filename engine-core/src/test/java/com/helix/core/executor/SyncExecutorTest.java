package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleExecutionException;
import com.helix.core.RuleCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyncExecutorTest {

    private SyncExecutor executor;
    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        executor = new SyncExecutor();
        compiler = new RuleCompiler();
    }

    @Test
    @DisplayName("Should execute compiled rule synchronously and record metrics")
    void testSyncExecutionSuccess() throws Exception {
        String json = """
                {
                    "name": "SyncTestRule",
                    "expression": "price * quantity",
                    "inputSchema": {
                        "price": "double",
                        "quantity": "int"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);
        ExecutionContext context = new ExecutionContext(Map.of("price", 15.5, "quantity", 3));

        ExecutionResult result = executor.execute(rule, context);
        assertTrue(result.isSuccess());
        assertEquals(46.5, result.getResult().orElse(null));

        ExecutorMetrics metrics = executor.getMetrics();
        assertEquals(1, metrics.getTotalExecutions());
        assertEquals(1, metrics.getSuccessfulExecutions());
        assertEquals(0, metrics.getFailedExecutions());
        assertTrue(metrics.getTotalExecutionTimeNanos() > 0);
        assertTrue(metrics.getAverageExecutionTimeNanos() > 0);
    }

    @Test
    @DisplayName("Should record failed execution metrics when rule returns execution error")
    void testSyncExecutionFailureResult() throws Exception {
        String json = """
                {
                    "name": "MissingVarRule",
                    "expression": "x + 1",
                    "inputSchema": {
                        "x": "int"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);
        ExecutionContext emptyContext = new ExecutionContext();

        ExecutionResult result = executor.execute(rule, emptyContext);
        assertFalse(result.isSuccess());

        ExecutorMetrics metrics = executor.getMetrics();
        assertEquals(1, metrics.getTotalExecutions());
        assertEquals(0, metrics.getSuccessfulExecutions());
        assertEquals(1, metrics.getFailedExecutions());
    }

    @Test
    @DisplayName("Should throw RuleExecutionException when compiled rule throws unexpected Exception")
    void testSyncExecutionUnexpectedException() {
        CompiledRule faultyRule = new CompiledRule() {
            @Override
            public String getName() {
                return "FaultyRule";
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public ExecutionResult execute(ExecutionContext context) {
                throw new RuntimeException("Simulated unexpected crash");
            }
        };

        RuleExecutionException ex = assertThrows(RuleExecutionException.class, () -> executor.execute(faultyRule, new ExecutionContext()));
        assertTrue(ex.getMessage().contains("FaultyRule"));

        ExecutorMetrics metrics = executor.getMetrics();
        assertEquals(1, metrics.getTotalExecutions());
        assertEquals(1, metrics.getFailedExecutions());
    }
}
