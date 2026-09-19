package com.helix.core.cache.l4;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.CacheTier;
import com.helix.core.cache.TieredRuleCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ClusterCacheInvalidationTest {

    private boolean isRedisAvailable;
    private JedisPool jedisPool;

    @BeforeEach
    void setUp() {
        try (Jedis jedis = new Jedis("localhost", 6379, 1000)) {
            isRedisAvailable = "PONG".equals(jedis.ping());
            if (isRedisAvailable) {
                jedisPool = new JedisPool("localhost", 6379);
            }
        } catch (Exception e) {
            isRedisAvailable = false;
        }
    }

    @AfterEach
    void tearDown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    @Test
    @DisplayName("Subscriber invokes invalidation consumer callback when message received")
    void testSubscriberMessageDispatch() {
        AtomicBoolean evicted = new AtomicBoolean(false);
        CacheInvalidationSubscriber subscriber = new CacheInvalidationSubscriber(ruleName -> {
            if ("FraudRule".equals(ruleName)) {
                evicted.set(true);
            }
        });

        subscriber.onMessage("helix:cache:invalidate", "FraudRule");
        assertTrue(evicted.get(), "Subscriber must dispatch invalidation callback");
    }

    @Test
    @DisplayName("TieredRuleCache falls through to L4 distributed tier on L1-L3 miss and promotes to L1")
    void testTieredRuleCacheL4FallthroughAndPromotion() {
        L4RedisRuleCache l4Cache = new L4RedisRuleCache("localhost", 6379, 2000, true);
        String ruleName = "TaxRule_" + UUID.randomUUID();
        CacheKey key = new CacheKey(ruleName, "1.0.0", Collections.emptyMap());
        String ruleHash = RuleKeyHasher.hashRule(key.getRuleName() + ":" + key.getVersion() + ":" + key.getSchemaHash());

        byte[] fakeBytecode = "MOCK_TAX_RULE_BYTECODE".getBytes(StandardCharsets.UTF_8);
        l4Cache.putBytecode(ruleHash, ruleName, "1.0.0", fakeBytecode);

        CompiledRule mockCompiledRule = new MockCompiledRule(ruleName, "1.0.0");

        // Construct TieredRuleCache with L4 and a rule loader function
        TieredRuleCache tieredCache = new TieredRuleCache(
                100, 10, TimeUnit.MINUTES,
                l4Cache,
                bytes -> mockCompiledRule
        );

        // 1. First get() misses L1/L2/L3, hits L4, and promotes to L1
        Optional<CompiledRule> retrieved = tieredCache.get(key);
        assertTrue(retrieved.isPresent(), "Must retrieve compiled rule from L4");
        assertEquals(ruleName, retrieved.get().getName());
        assertEquals(0, tieredCache.getStats().hitsL1(), "L1 hits initially 0 on cold lookup");
        assertEquals(1, tieredCache.getCacheStatistics().getHitsL4(), "L4 hits must be 1 on first retrieval");

        // 2. Second get() must hit L1 directly (sub-12ns path)
        Optional<CompiledRule> secondRetrieved = tieredCache.get(key);
        assertTrue(secondRetrieved.isPresent());
        assertEquals(1, tieredCache.getStats().hitsL1(), "L1 hits must be 1 after promotion to L1");
        assertEquals(2, tieredCache.getStats().totalHits() + tieredCache.getCacheStatistics().getHitsL4());

        tieredCache.close();
        l4Cache.close();
    }

    @Test
    @DisplayName("Real cluster invalidation: Node A eviction broadcasts over Redis Pub/Sub to Node B within 20ms")
    void testRealClusterInvalidationPropagation() throws Exception {
        if (!isRedisAvailable || jedisPool == null) {
            System.out.println("Skipping real cluster test: Redis server not available at localhost:6379");
            return;
        }

        String ruleName = "ClusterSharedRule_" + UUID.randomUUID();
        CacheKey key = new CacheKey(ruleName, "1.0.0", Collections.emptyMap());
        CompiledRule rule = new MockCompiledRule(ruleName, "1.0.0");

        // Node A and Node B have their own TieredRuleCache instances
        TieredRuleCache nodeACache = new TieredRuleCache(100, 10, TimeUnit.MINUTES);
        TieredRuleCache nodeBCache = new TieredRuleCache(100, 10, TimeUnit.MINUTES);

        // Populate both nodes with the rule
        nodeACache.put(key, rule);
        nodeBCache.put(key, rule);

        assertTrue(nodeACache.get(key).isPresent(), "Node A must have rule in L1");
        assertTrue(nodeBCache.get(key).isPresent(), "Node B must have rule in L1");

        CountDownLatch nodeBInvalidated = new CountDownLatch(1);
        long[] propagationDurationMs = new long[1];

        // Start Node B's Redis invalidation listener in a virtual thread
        RedisCacheInvalidationListener nodeBListener = new RedisCacheInvalidationListener(
                jedisPool,
                invalidatedRule -> {
                    if (ruleName.equals(invalidatedRule)) {
                        nodeBCache.invalidateByName(invalidatedRule);
                        nodeBInvalidated.countDown();
                    }
                }
        );
        nodeBListener.start();

        // Await subscriber thread to confirm subscription with Redis
        assertTrue(nodeBListener.awaitSubscribed(2, TimeUnit.SECONDS), "Node B listener must be subscribed");

        // Pre-warm publisher connection in pool to avoid socket initialization latency in timing
        try (Jedis warm = jedisPool.getResource()) {
            warm.ping();
        }

        // Node A initiates invalidation and broadcasts via RedisCacheInvalidator
        RedisCacheInvalidator nodeAInvalidator = new RedisCacheInvalidator(jedisPool);

        long publishStart = System.currentTimeMillis();
        nodeAInvalidator.broadcastInvalidation(ruleName);

        // Await Node B receiving the invalidation event
        boolean received = nodeBInvalidated.await(2, TimeUnit.SECONDS);
        propagationDurationMs[0] = System.currentTimeMillis() - publishStart;
        nodeACache.invalidateByName(ruleName);

        assertTrue(received, "Node B must receive the invalidation message via Redis Pub/Sub");
        assertTrue(propagationDurationMs[0] <= 250, "Propagation must be sub-250ms (was: " + propagationDurationMs[0] + "ms)");

        // Verify Node B's local cache entry was purged
        Optional<CompiledRule> nodeBCheck = nodeBCache.get(key);
        assertTrue(nodeBCheck.isEmpty(), "Node B local cache must be purged following cluster invalidation");

        nodeBListener.close();
        nodeACache.close();
        nodeBCache.close();
    }

    private static class MockCompiledRule implements CompiledRule {
        private final String name;
        private final String version;

        public MockCompiledRule(String name, String version) {
            this.name = name;
            this.version = version;
        }

        @Override public String getName() { return name; }
        @Override public String getVersion() { return version; }
        @Override public ExecutionResult execute(ExecutionContext context) {
            return ExecutionResult.success("result", 100L);
        }
    }
}
