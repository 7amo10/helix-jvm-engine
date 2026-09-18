package com.helix.core.cache.l4;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class L4RedisRuleCacheTest {

    private boolean isRedisAvailable;
    private L4RedisRuleCache cache;

    @BeforeEach
    void setUp() {
        try (Jedis jedis = new Jedis("localhost", 6379, 1000)) {
            isRedisAvailable = "PONG".equals(jedis.ping());
        } catch (Exception e) {
            isRedisAvailable = false;
        }
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            try {
                cache.invalidateAll();
            } catch (Exception ignored) {}
            cache.close();
        }
    }

    @Test
    @DisplayName("L4 cache stores and retrieves bytecode with fallback or real Redis")
    void testMockRedisStoreAndRetrieve() {
        cache = new L4RedisRuleCache("localhost", 6379, 2000, true);
        String ruleHash = "rule-hash-" + UUID.randomUUID();
        byte[] bytecode = "COMPILED_BYTECODE_SAMPLE".getBytes(StandardCharsets.UTF_8);

        cache.putBytecode(ruleHash, "SampleRule", "1.0.0", bytecode);
        Optional<byte[]> retrieved = cache.getBytecode(ruleHash);

        assertTrue(retrieved.isPresent(), "Bytecode must be present after put");
        assertArrayEquals(bytecode, retrieved.get(), "Retrieved bytecode must match original");
        assertTrue(cache.getHitCount() >= 1, "Hit count must increment on retrieval");
    }

    @Test
    @DisplayName("Real Redis store, retrieve, hit-count, and invalidation lifecycle")
    void testRealRedisLifecycle() {
        if (!isRedisAvailable) {
            System.out.println("Skipping real Redis test: Redis server not available at localhost:6379");
            return;
        }

        cache = new L4RedisRuleCache("localhost", 6379, 2000, false);
        assertFalse(cache.isFallbackActive(), "Fallback should not be active when Redis is online");

        String ruleHash = "lifecycle-hash-" + UUID.randomUUID();
        byte[] bytecode = "LIFECYCLE_BYTECODE_STREAM".getBytes(StandardCharsets.UTF_8);

        // Initially cold
        Optional<byte[]> cold = cache.getBytecode(ruleHash);
        assertTrue(cold.isEmpty(), "Cold rule lookup must return empty (compiler lease acquired)");
        assertEquals(1, cache.getMissCount(), "Miss count must be 1");

        // Put compiled bytecode
        cache.putBytecode(ruleHash, "LifecycleRule", "1.0.0", bytecode);

        // Retrieve cached bytecode
        Optional<byte[]> hit = cache.getBytecode(ruleHash);
        assertTrue(hit.isPresent(), "Cached rule lookup must return present");
        assertArrayEquals(bytecode, hit.get());
        assertEquals(1, cache.getHitCount(), "Hit count must be 1");

        // Invalidate single rule
        cache.invalidate(ruleHash);
        Optional<byte[]> postInvalidate = cache.getBytecode(ruleHash);
        assertTrue(postInvalidate.isEmpty(), "Rule must be empty after invalidation");
    }

    @Test
    @DisplayName("Thundering herd stampede prevention: 20 concurrent threads encounter cold rule simultaneously")
    void testThunderingHerdCompilationStampede() throws Exception {
        if (!isRedisAvailable) {
            System.out.println("Skipping stampede test: Redis server not available at localhost:6379");
            return;
        }

        cache = new L4RedisRuleCache("localhost", 6379, 5000, false);
        String stampedeRuleHash = "stampede-hash-" + UUID.randomUUID();
        byte[] compiledBytecode = "SYNTHESIZED_STAMPEDE_BYTECODE".getBytes(StandardCharsets.UTF_8);

        int concurrency = 20;
        AtomicInteger compilationsPerformed = new AtomicInteger(0);

        try (ExecutorService virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<byte[]>> tasks = new ArrayList<>();

            for (int i = 0; i < concurrency; i++) {
                tasks.add(() -> {
                    Optional<byte[]> result = cache.getBytecode(stampedeRuleHash);
                    if (result.isPresent()) {
                        return result.get();
                    }

                    // Only the thread that got empty() has the compilation lock
                    int compCount = compilationsPerformed.incrementAndGet();
                    // Simulate non-trivial ASM rule compilation time
                    Thread.sleep(60);
                    cache.putBytecode(stampedeRuleHash, "StampedeRule", "1.0.0", compiledBytecode);
                    return compiledBytecode;
                });
            }

            List<Future<byte[]>> futures = virtualThreadPool.invokeAll(tasks);

            for (Future<byte[]> f : futures) {
                byte[] res = f.get();
                assertNotNull(res, "Every thread must successfully obtain bytecode");
                assertArrayEquals(compiledBytecode, res, "Bytecode must match synthesized bytecode");
            }
        }

        // Key verification: exactly 1 compilation must have been performed!
        assertEquals(1, compilationsPerformed.get(),
                "Thundering herd failed: Expected exactly 1 compilation, but was " + compilationsPerformed.get());
    }

    @Test
    @DisplayName("Fallback mode when Redis is offline and allowFallback is true")
    void testFallbackModeGracefulDegradation() {
        // Port 65534 is guaranteed unreachable
        cache = new L4RedisRuleCache("127.0.0.1", 65534, 500, true);

        assertTrue(cache.isFallbackActive(), "Cache should activate fallback mode on connection failure");

        String ruleHash = "fallback-hash-" + UUID.randomUUID();
        byte[] bytecode = "FALLBACK_BYTECODE".getBytes(StandardCharsets.UTF_8);

        cache.putBytecode(ruleHash, "FallbackRule", "1.0.0", bytecode);
        Optional<byte[]> result = cache.getBytecode(ruleHash);

        assertTrue(result.isPresent());
        assertArrayEquals(bytecode, result.get());
        assertEquals(1, cache.getHitCount());
    }

    @Test
    @DisplayName("Fails with IllegalStateException when Redis offline and allowFallback is false")
    void testConnectionFailureThrowsWhenFallbackDisabled() {
        assertThrows(IllegalStateException.class, () ->
                new L4RedisRuleCache("127.0.0.1", 65534, 500, false)
        );
    }

    @Test
    @DisplayName("HelixConfig property defaults and custom configuration")
    void testHelixConfigProperties() {
        L4RedisConfig config = L4RedisConfig.fromSystemProperties();
        assertNotNull(config.getHost());
        assertTrue(config.getPort() > 0);
        assertTrue(config.getPoolMaxTotal() > 0);
        assertTrue(config.getTtlSeconds() > 0);
    }
}
