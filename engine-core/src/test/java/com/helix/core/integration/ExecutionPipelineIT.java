package com.helix.core.integration;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.RuleCompiler;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.executor.AsyncExecutor;
import com.helix.core.executor.BatchExecutor;
import com.helix.core.executor.SyncExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionPipelineIT {

    private RuleCompiler compiler;
    private SyncExecutor syncExecutor;
    private AsyncExecutor asyncExecutor;
    private BatchExecutor batchExecutor;
    private TieredRuleCache cache;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler();
        syncExecutor = new SyncExecutor();
        asyncExecutor = new AsyncExecutor();
        batchExecutor = new BatchExecutor(4);
        cache = new TieredRuleCache();
    }

    @AfterEach
    void tearDown() {
        asyncExecutor.close();
        cache.close();
    }

    @Test
    @DisplayName("IT: Full pipeline compile -> cache -> sync execute")
    void testCompileCacheAndSyncExecute() throws Exception {
        String json = """
                {
                    "name": "PipelineDiscountRule",
                    "expression": "amount > 500",
                    "inputSchema": {
                        "amount": "double"
                    }
                }
                """;

        CompiledRule compiled = compiler.compile(json);
        CacheKey key = new CacheKey("PipelineDiscountRule", "1.0.0", Map.of("amount", Double.class));
        cache.put(key, compiled);

        Optional<CompiledRule> cached = cache.get(key);
        assertTrue(cached.isPresent());

        ExecutionResult result1 = syncExecutor.execute(cached.get(), new ExecutionContext(Map.of("amount", 1000.0)));
        assertTrue(result1.isSuccess());
        assertEquals(Boolean.TRUE, result1.getResult().orElse(null));

        ExecutionResult result2 = syncExecutor.execute(cached.get(), new ExecutionContext(Map.of("amount", 300.0)));
        assertTrue(result2.isSuccess());
        assertEquals(Boolean.FALSE, result2.getResult().orElse(null));
    }

    @Test
    @DisplayName("IT: Full pipeline compile -> async execute -> batch execute")
    void testAsyncAndBatchExecutionPipeline() throws Exception {
        String json = """
                {
                    "name": "TaxCalculator",
                    "expression": "income * 0.2",
                    "inputSchema": {
                        "income": "double"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);

        // Async execution test
        CompletableFuture<ExecutionResult> future = asyncExecutor.executeAsync(rule, new ExecutionContext(Map.of("income", 50000.0)));
        ExecutionResult asyncResult = future.get();
        assertTrue(asyncResult.isSuccess());
        assertEquals(10000.0, asyncResult.getResult().orElse(null));

        // Batch execution test
        List<ExecutionContext> contexts = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            contexts.add(new ExecutionContext(Map.of("income", (double) i * 1000)));
        }

        List<ExecutionResult> batchResults = batchExecutor.executeBatch(rule, contexts);
        assertEquals(20, batchResults.size());
        for (int i = 0; i < 20; i++) {
            assertTrue(batchResults.get(i).isSuccess());
            assertEquals(((double) (i + 1) * 1000) * 0.2, batchResults.get(i).getResult().orElse(null));
        }
    }
}
