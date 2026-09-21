package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.cache.DistributedRuleCache;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.profiler.node.AstNodeProfiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdaptiveAstOptimizer Unit Tests")
class AdaptiveAstOptimizerTest {

    private final ExpressionRuleParser parser = new ExpressionRuleParser();

    @BeforeEach
    void setUp() {
        AstNodeProfiler.reset();
        AstNodeProfiler.setEnabled(true);
    }

    @Test
    @DisplayName("Should compute cost-to-failure ratio correctly from live stats")
    void testComputeCostToFailureRatio() throws Exception {
        ExpressionNode node = parser.parse("amount > 1000");
        String nodeId = AstNodeIdResolver.resolveNodeId("TestRule", node);

        // Record 100 executions, 50 failures (50%), total duration 500,000 ns (avg cost = 5,000 ns)
        // Ratio = 5,000 / 0.5 = 10,000.0
        for (int i = 0; i < 50; i++) {
            AstNodeProfiler.record("TestRule", nodeId, 5000, true);
            AstNodeProfiler.record("TestRule", nodeId, 5000, false);
        }

        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer();
        double ratio = optimizer.computeRatio("TestRule", node);

        assertEquals(10_000.0, ratio, 0.01, "Ratio must equal average cost divided by failure probability");
    }

    @Test
    @DisplayName("Should apply Bayesian Laplace smoothing when failure count is zero to avoid short-circuit blindness")
    void testComputeRatioZeroFailureProbability() throws Exception {
        ExpressionNode node = parser.parse("amount > 1000");
        String nodeId = AstNodeIdResolver.resolveNodeId("TestRule", node);

        // Record 10 successful executions (avg cost = 2000 ns, failures = 0)
        // With optimistic prior failure rate = 0.50, expected ratio = 2000 / 0.50 = 4000.0
        for (int i = 0; i < 10; i++) {
            AstNodeProfiler.record("TestRule", nodeId, 2000, true);
        }

        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer();
        double ratio = optimizer.computeRatio("TestRule", node);

        assertEquals(4000.0, ratio, 0.1, "Ratio must use optimistic prior when failure rate is 0");

        // Literals: true is truly infallible (Infinity), false is immediate failure (0.0)
        assertEquals(Double.POSITIVE_INFINITY, optimizer.computeRatio("TestRule", parser.parse("true")));
        assertEquals(0.0, optimizer.computeRatio("TestRule", parser.parse("false")));
    }

    @Test
    @DisplayName("Should reorder AND chain ascending by cost-to-failure ratio")
    void testReorderAndChainAscending() throws Exception {
        // Rule: slow_clause && fast_clause
        // where fast_clause has lower ratio than slow_clause
        ExpressionNode ast = parser.parse("score > 50 && amount > 1000");

        String scoreId = AstNodeIdResolver.resolveNodeId("ReorderRule", parser.parse("score > 50"));
        String amountId = AstNodeIdResolver.resolveNodeId("ReorderRule", parser.parse("amount > 1000"));

        // score: avg cost 50,000 ns, failure rate 0.5 -> ratio 100,000
        AstNodeProfiler.record("ReorderRule", scoreId, 50_000, false);
        AstNodeProfiler.record("ReorderRule", scoreId, 50_000, true);

        // amount: avg cost 100 ns, failure rate 0.5 -> ratio 200
        AstNodeProfiler.record("ReorderRule", amountId, 100, false);
        AstNodeProfiler.record("ReorderRule", amountId, 100, true);

        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer();
        ExpressionNode optimized = optimizer.optimize("ReorderRule", ast);

        assertNotNull(optimized);
        assertTrue(optimized instanceof BinaryOpNode);
        BinaryOpNode b = (BinaryOpNode) optimized;
        assertEquals(BinaryOpNode.Operator.AND, b.getOperator());

        // Left clause must now be amount > 1000 (lowest ratio)
        String leftId = AstNodeIdResolver.resolveNodeId("ReorderRule", b.getLeft());
        assertEquals(amountId, leftId, "Lower ratio clause (amount > 1000) must be evaluated first");
    }

    @Test
    @DisplayName("Should reorder 3-clause AND chain ascending by ratio")
    void testReorderThreeClauseAndChain() throws Exception {
        ExpressionNode ast = parser.parse("c1 > 10 && c2 > 20 && c3 > 30");

        Map<String, Double> customRatios = new HashMap<>();
        customRatios.put("clause_c1_greater_than", 3000.0);
        customRatios.put("clause_c2_greater_than", 100.0); // lowest
        customRatios.put("clause_c3_greater_than", 800.0); // middle

        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer((ruleName, node) -> {
            String id = AstNodeIdResolver.resolveNodeId(ruleName, node);
            return customRatios.getOrDefault(id, 50.0);
        });

        ExpressionNode optimized = optimizer.optimize("ThreeRule", ast);

        // Order should be: c2 (100) && c3 (800) && c1 (3000)
        assertNotNull(optimized);
        assertTrue(optimizer.isReorderBeneficial(ast, optimized));
    }

    @Test
    @DisplayName("Should preserve boolean logic truth value equivalence after reordering")
    void testBooleanLogicEquivalence() throws Exception {
        ExpressionRuleParser exprParser = new ExpressionRuleParser();
        ExpressionNode ast = exprParser.parse("a > 10 && b > 20 && c > 30");

        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer((rule, node) -> {
            String id = AstNodeIdResolver.resolveNodeId(rule, node);
            if (id.contains("c")) return 10.0;
            if (id.contains("b")) return 20.0;
            return 30.0;
        });

        ExpressionNode optimized = optimizer.optimize("EquivRule", ast);

        AsmBytecodeGenerator gen = new AsmBytecodeGenerator();
        Rule origRule = new RuleNode("Orig", "a > 10 && b > 20 && c > 30", Map.of(), ast);
        Rule optRule = new RuleNode("Opt", "a > 10 && b > 20 && c > 30", Map.of(), optimized);

        CompiledRule cOrig = gen.generate(origRule, ast);
        CompiledRule cOpt = gen.generate(optRule, optimized);

        // Test all 8 truth permutations
        double[][] cases = {
                {5, 5, 5}, {5, 5, 35}, {5, 25, 5}, {5, 25, 35},
                {15, 5, 5}, {15, 5, 35}, {15, 25, 5}, {15, 25, 35}
        };

        for (double[] c : cases) {
            ExecutionContext ctx = new ExecutionContext(Map.of("a", c[0], "b", c[1], "c", c[2]));
            assertEquals(cOrig.execute(ctx).getResult(), cOpt.execute(ctx).getResult(),
                    "Reordered AST must produce identical results for inputs: " + ctx);
        }
    }

    @Test
    @DisplayName("Should detect when AST is already in optimal order and skip redundant reorder")
    void testSkipReorderWhenAlreadyOptimal() throws Exception {
        ExpressionNode ast = parser.parse("amount > 1000 && score > 50");

        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer((rule, node) -> {
            String id = AstNodeIdResolver.resolveNodeId(rule, node);
            return id.contains("amount") ? 10.0 : 5000.0; // amount already first
        });

        ExpressionNode optimized = optimizer.optimize("OptimalRule", ast);
        assertFalse(optimizer.isReorderBeneficial(ast, optimized),
                "Should not mark reorder beneficial if already sorted by ratio");
    }

    @Test
    @DisplayName("Should atomically hot-swap compiled bytecode in TieredRuleCache with zero downtime")
    void testAtomicHotSwapInTieredRuleCache() throws Exception {
        TieredRuleCache cache = new TieredRuleCache();
        ExpressionNode ast = parser.parse("score > 50 && amount > 1000");
        Rule rule = new RuleNode("HotSwapRule", "score > 50 && amount > 1000", Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator();
        CompiledRule initialCompiled = generator.generate(rule, ast);
        CacheKey cacheKey = new CacheKey(rule);
        cache.put(cacheKey, initialCompiled);

        // Verify initial rule is in cache
        assertTrue(cache.get(cacheKey).isPresent());
        assertSame(initialCompiled, cache.get(cacheKey).get());

        // Optimize and hot-swap
        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer((r, node) -> {
            String id = AstNodeIdResolver.resolveNodeId(r, node);
            return id.contains("amount") ? 10.0 : 10000.0;
        });

        OptimizationResult result = optimizer.optimizeAndHotSwap(rule, ast, cache);

        assertTrue(result.reordered(), "Optimization should reorder clauses");
        assertNotNull(result.compiledRule());
        assertNotSame(initialCompiled, result.compiledRule());

        // Verify cache now serves the hot-swapped compiled rule
        CompiledRule swapped = cache.get(cacheKey).orElseThrow();
        assertSame(result.compiledRule(), swapped);

        cache.close();
    }

    static class MockDistributedRuleCache implements DistributedRuleCache {
        final AtomicReference<byte[]> storedBytecode = new AtomicReference<>();
        final AtomicReference<String> storedRuleName = new AtomicReference<>();
        final AtomicReference<String> storedVersion = new AtomicReference<>();

        @Override
        public Optional<byte[]> getBytecode(String ruleHash) {
            return Optional.ofNullable(storedBytecode.get());
        }

        @Override
        public void putBytecode(String ruleHash, String ruleName, String version, byte[] bytecode) {
            storedRuleName.set(ruleName);
            storedVersion.set(version);
            storedBytecode.set(bytecode);
        }

        @Override
        public void invalidate(String ruleHash) {}

        @Override
        public void invalidateAll() {}

        @Override
        public long getHitCount() { return 0; }

        @Override
        public long getMissCount() { return 0; }

        @Override
        public void close() {}
    }

    @Test
    @DisplayName("Should publish hot-swapped bytecode to L4 distributed cache if configured")
    void testHotSwapPublishesToL4() throws Exception {
        MockDistributedRuleCache l4Cache = new MockDistributedRuleCache();
        TieredRuleCache cache = new TieredRuleCache(100, 10, TimeUnit.MINUTES, l4Cache, null);

        ExpressionNode ast = parser.parse("score > 50 && amount > 1000");
        Rule rule = new RuleNode("L4HotSwapRule", "score > 50 && amount > 1000", Map.of(), ast);
        CacheKey cacheKey = new CacheKey(rule);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator();
        CompiledRule initial = generator.generate(rule, ast);
        cache.put(cacheKey, initial);

        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer((r, node) -> {
            String id = AstNodeIdResolver.resolveNodeId(r, node);
            return id.contains("amount") ? 10.0 : 10000.0;
        });

        OptimizationResult result = optimizer.optimizeAndHotSwap(rule, ast, cache);

        assertTrue(result.reordered());
        assertNotNull(result.bytecode());

        // Verify L4 received putBytecode
        assertEquals(rule.getName(), l4Cache.storedRuleName.get());
        assertEquals(rule.getVersion(), l4Cache.storedVersion.get());
        assertArrayEquals(result.bytecode(), l4Cache.storedBytecode.get());

        cache.close();
    }

    @Test
    @DisplayName("Should execute concurrently without errors or dropped requests during hot swap")
    void testConcurrentExecutionDuringHotSwap() throws Exception {
        TieredRuleCache cache = new TieredRuleCache();
        ExpressionNode ast = parser.parse("amount > 1000 && score > 50");
        Rule rule = new RuleNode("ConcurrentHotSwapRule", "amount > 1000 && score > 50", Map.of(), ast);
        CacheKey cacheKey = new CacheKey(rule);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator();
        CompiledRule initial = generator.generate(rule, ast);
        cache.put(cacheKey, initial);

        int threadCount = 8;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicBoolean running = new AtomicBoolean(true);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ExecutionContext ctx = new ExecutionContext(Map.of("amount", 1500.0, "score", 75.0));
                    for (int j = 0; j < operationsPerThread; j++) {
                        CompiledRule ruleInstance = cache.get(cacheKey).orElse(null);
                        if (ruleInstance != null) {
                            ExecutionResult res = ruleInstance.execute(ctx);
                            if (res.isSuccess() && Boolean.TRUE.equals(res.getResult().orElse(null))) {
                                successCount.incrementAndGet();
                            } else {
                                errorCount.incrementAndGet();
                            }
                        } else {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start workers
        startLatch.countDown();

        // Perform hot swap concurrently while workers are actively executing
        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer((r, node) -> {
            String id = AstNodeIdResolver.resolveNodeId(r, node);
            return id.contains("score") ? 5.0 : 50.0;
        });
        OptimizationResult result = optimizer.optimizeAndHotSwap(rule, ast, cache);
        assertTrue(result.reordered());

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All concurrent executions should complete promptly");
        executor.shutdown();

        assertEquals(0, errorCount.get(), "Must have zero evaluation errors during concurrent hot swap");
        assertEquals(threadCount * operationsPerThread, successCount.get(),
                "All executions must succeed during hot swap without dropped requests");

        cache.close();
    }
}
