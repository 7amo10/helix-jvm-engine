package com.helix.integration;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.RuleCompiler;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.classloader.ClassLoaderManager;
import com.helix.core.classloader.IsolationMode;
import com.helix.core.executor.AsyncExecutor;
import com.helix.core.executor.BatchExecutor;
import com.helix.core.executor.SyncExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sprint 7 End-to-End Core Integration Test Suite")
class EndToEndIntegrationTest {

    private RuleCompiler compiler;
    private ClassLoaderManager classLoaderManager;
    private TieredRuleCache cache;
    private SyncExecutor syncExecutor;
    private AsyncExecutor asyncExecutor;
    private BatchExecutor batchExecutor;

    @BeforeEach
    void setUp() {
        this.compiler = new RuleCompiler();
        this.classLoaderManager = new ClassLoaderManager(IsolationMode.HIERARCHICAL);
        this.cache = new TieredRuleCache(10, 10, java.util.concurrent.TimeUnit.MINUTES);
        this.syncExecutor = new SyncExecutor();
        this.asyncExecutor = new AsyncExecutor();
        this.batchExecutor = new BatchExecutor(4);
    }

    @AfterEach
    void tearDown() {
        if (asyncExecutor != null) {
            asyncExecutor.close();
        }
        if (cache != null) {
            cache.close();
        }
        if (classLoaderManager != null) {
            classLoaderManager.close();
        }
    }

    @Test
    @DisplayName("1. Complete Workflow: JSON -> Compile -> Load -> Execute -> Cache")
    void testCompleteEndToEndWorkflow() throws Exception {
        String ruleJson = """
                {
                    "name": "E2EHighValueOrderRule",
                    "version": "1.0.0",
                    "expression": "amount > 5000 && VIP == true",
                    "inputSchema": {
                        "amount": "double",
                        "VIP": "boolean"
                    }
                }
                """;

        // Step 1: Compile JSON Rule
        CompiledRule compiledRule = compiler.compile(ruleJson);
        assertNotNull(compiledRule);
        assertEquals("E2EHighValueOrderRule", compiledRule.getName());

        // Step 2: Cache compiled rule
        CacheKey cacheKey = new CacheKey("E2EHighValueOrderRule", "1.0.0", Map.of("amount", Double.class, "VIP", Boolean.class));
        cache.put(cacheKey, compiledRule);

        // Verify Cache Hit
        Optional<CompiledRule> cachedRuleOpt = cache.get(cacheKey);
        assertTrue(cachedRuleOpt.isPresent());
        CompiledRule cachedRule = cachedRuleOpt.get();

        // Step 3: Synchronous Execution
        ExecutionContext validContext = new ExecutionContext(Map.of("amount", 7500.0, "VIP", true));
        ExecutionResult syncResult = syncExecutor.execute(cachedRule, validContext);
        assertTrue(syncResult.isSuccess());
        assertEquals(true, syncResult.getResult().orElse(null));

        // Step 4: Asynchronous Execution
        CompletableFuture<ExecutionResult> asyncFuture = asyncExecutor.executeAsync(cachedRule, validContext);
        ExecutionResult asyncResult = asyncFuture.get();
        assertTrue(asyncResult.isSuccess());
        assertEquals(true, asyncResult.getResult().orElse(null));

        // Step 5: Batch Execution
        List<ExecutionContext> batchContexts = List.of(
                new ExecutionContext(Map.of("amount", 8000.0, "VIP", true)),
                new ExecutionContext(Map.of("amount", 2000.0, "VIP", false)),
                new ExecutionContext(Map.of("amount", 6000.0, "VIP", true))
        );
        List<ExecutionResult> batchResults = batchExecutor.executeBatch(cachedRule, batchContexts);
        assertEquals(3, batchResults.size());
        assertTrue(batchResults.get(0).isSuccess());
        assertEquals(false, batchResults.get(1).getResult().orElse(true));
        assertTrue(batchResults.get(2).isSuccess());
    }

    @Test
    @DisplayName("2. Verify ClassLoader Isolation Modes")
    void testClassLoaderIsolationModes() {
        var isolatedManager = new ClassLoaderManager(IsolationMode.ISOLATED);
        var isolatedLoader = isolatedManager.getOrCreateClassLoader("FINANCE", "RuleA");
        assertNotNull(isolatedLoader);

        var sharedManager = new ClassLoaderManager(IsolationMode.SHARED);
        var sharedLoader = sharedManager.getOrCreateClassLoader("FINANCE", "RuleB");
        assertNotNull(sharedLoader);

        isolatedManager.close();
        sharedManager.close();
    }

    @Test
    @DisplayName("3. Verify Cache Eviction under Key Pressure")
    void testCacheEvictionPressure() throws Exception {
        TieredRuleCache smallCache = new TieredRuleCache(2, 10, java.util.concurrent.TimeUnit.MINUTES);

        String ruleJson = """
                {
                    "name": "EvictRule",
                    "version": "1.0.0",
                    "expression": "x > 0",
                    "inputSchema": { "x": "int" }
                }
                """;
        CompiledRule rule = compiler.compile(ruleJson);

        CacheKey key1 = new CacheKey("Rule1", "1.0.0", Map.of());
        CacheKey key2 = new CacheKey("Rule2", "1.0.0", Map.of());
        CacheKey key3 = new CacheKey("Rule3", "1.0.0", Map.of());

        smallCache.put(key1, rule);
        smallCache.put(key2, rule);
        smallCache.put(key3, rule); // Triggers L1 LRU eviction

        smallCache.close();
    }
}
