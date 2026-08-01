package com.helix.core;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.classloader.ClassLoaderManager;
import com.helix.core.classloader.IsolationMode;
import com.helix.core.classloader.RuleClassLoader;
import com.helix.core.events.EngineEvent;
import com.helix.core.events.EventBus;
import com.helix.core.events.EventType;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class Sprint3VerificationTest {

    private RuleCompiler compiler;
    private SyncExecutor syncExecutor;
    private AsyncExecutor asyncExecutor;
    private BatchExecutor batchExecutor;
    private TieredRuleCache cache;
    private ClassLoaderManager classLoaderManager;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler();
        syncExecutor = new SyncExecutor();
        asyncExecutor = new AsyncExecutor();
        batchExecutor = new BatchExecutor(4);
        cache = new TieredRuleCache();
        classLoaderManager = new ClassLoaderManager(IsolationMode.HIERARCHICAL);
        eventBus = new EventBus();
    }

    @AfterEach
    void tearDown() {
        asyncExecutor.close();
        cache.close();
        classLoaderManager.close();
        eventBus.close();
    }

    @Test
    @DisplayName("Sprint 3 End-to-End Verification: ClassLoaders, Sync/Async/Batch Execution, Tiered Caching, and EventBus")
    void verifySprint3Deliverables() throws Exception {
        // 1. Verify EventBus dispatching
        AtomicBoolean eventDispatched = new AtomicBoolean(false);
        eventBus.subscribe(EventType.COMPILATION_COMPLETED, ev -> eventDispatched.set(true));

        // 2. Verify Compilation and ClassLoader allocation
        String json = """
                {
                    "name": "Sprint3Rule",
                    "expression": "amount > 1000 && country.equals(\\"US\\")",
                    "inputSchema": {
                        "amount": "double",
                        "country": "String"
                    }
                }
                """;

        RuleClassLoader loader = classLoaderManager.getOrCreateClassLoader("COMPLIANCE", "Sprint3Rule");
        assertNotNull(loader);

        CompiledRule compiledRule = compiler.compile(json);
        assertNotNull(compiledRule);

        eventBus.publish(new EngineEvent(EventType.COMPILATION_COMPLETED, compiledRule.getName()));
        assertTrue(eventDispatched.get());

        // 3. Verify Tiered Cache storage and retrieval
        CacheKey cacheKey = new CacheKey("Sprint3Rule", "1.0.0", Map.of("amount", Double.class, "country", String.class));
        cache.put(cacheKey, compiledRule);

        Optional<CompiledRule> retrievedRule = cache.get(cacheKey);
        assertTrue(retrievedRule.isPresent());

        // 4. Verify Synchronous Execution
        ExecutionContext ctx1 = new ExecutionContext(Map.of("amount", 1500.0, "country", "US"));
        ExecutionResult syncResult = syncExecutor.execute(retrievedRule.get(), ctx1);
        assertTrue(syncResult.isSuccess());
        assertEquals(Boolean.TRUE, syncResult.getResult().orElse(null));

        // 5. Verify Asynchronous Execution
        ExecutionContext ctx2 = new ExecutionContext(Map.of("amount", 500.0, "country", "US"));
        CompletableFuture<ExecutionResult> asyncFuture = asyncExecutor.executeAsync(retrievedRule.get(), ctx2);
        ExecutionResult asyncResult = asyncFuture.get();
        assertTrue(asyncResult.isSuccess());
        assertEquals(Boolean.FALSE, asyncResult.getResult().orElse(null));

        // 6. Verify Parallel Batch Execution
        List<ExecutionContext> batchContexts = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            double amt = i % 2 == 0 ? 2000.0 : 500.0;
            batchContexts.add(new ExecutionContext(Map.of("amount", amt, "country", "US")));
        }

        List<ExecutionResult> batchResults = batchExecutor.executeBatch(retrievedRule.get(), batchContexts);
        assertEquals(50, batchResults.size());
        for (int i = 0; i < 50; i++) {
            assertTrue(batchResults.get(i).isSuccess());
            boolean expected = i % 2 == 0;
            assertEquals(expected, batchResults.get(i).getResult().orElse(null));
        }
    }
}
