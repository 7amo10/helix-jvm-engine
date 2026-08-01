package com.helix.core.cache;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TieredRuleCacheTest {

    private TieredRuleCache cache;

    @BeforeEach
    void setUp() {
        cache = new TieredRuleCache(2, 10, TimeUnit.MINUTES);
    }

    @AfterEach
    void tearDown() {
        cache.close();
    }

    @Test
    @DisplayName("Should store rule in L1 cache and return on lookup")
    void testL1Hit() {
        CacheKey key = new CacheKey("Rule1", "1.0.0", Map.of("x", Integer.class));
        CompiledRule rule = createDummyRule("Rule1");

        cache.put(key, rule);

        Optional<CompiledRule> cached = cache.get(key);
        assertTrue(cached.isPresent());
        assertEquals("Rule1", cached.get().getName());

        CacheStatsSnapshot stats = cache.getStats();
        assertEquals(1, stats.hitsL1());
        assertEquals(0, stats.misses());
        assertEquals(1.0, stats.hitRate());
    }

    @Test
    @DisplayName("Should fallback to L2 soft reference cache when L1 misses and promote to L1 on 20+ accesses")
    void testL2HitAndPromotion() {
        CacheKey key = new CacheKey("L2Rule", "1.0.0", Map.of());
        CompiledRule rule = createDummyRule("L2Rule");

        cache.putL2(key, rule);

        // Access 19 times (hits L2)
        for (int i = 0; i < 19; i++) {
            Optional<CompiledRule> res = cache.get(key);
            assertTrue(res.isPresent());
        }

        CacheStatsSnapshot stats1 = cache.getStats();
        assertEquals(19, stats1.hitsL2());

        // 20th access -> promotes to L1
        Optional<CompiledRule> promotedRes = cache.get(key);
        assertTrue(promotedRes.isPresent());

        CacheStatsSnapshot stats2 = cache.getStats();
        assertEquals(1, stats2.promotionsL2ToL1());

        // 21st access -> hits L1 directly
        cache.get(key);
        CacheStatsSnapshot stats3 = cache.getStats();
        assertEquals(1, stats3.hitsL1());
    }

    @Test
    @DisplayName("Should fallback to L3 weak reference cache when L1/L2 miss and promote to L2 on 5+ accesses")
    void testL3HitAndPromotion() {
        CacheKey key = new CacheKey("L3Rule", "1.0.0", Map.of());
        CompiledRule rule = createDummyRule("L3Rule");

        cache.putL3(key, rule);

        for (int i = 0; i < 5; i++) {
            assertTrue(cache.get(key).isPresent());
        }

        CacheStatsSnapshot stats = cache.getStats();
        assertEquals(5, stats.hitsL3());
        assertEquals(1, stats.promotionsL3ToL2());
    }

    @Test
    @DisplayName("Should record cache miss when key is not present")
    void testCacheMiss() {
        CacheKey key = new CacheKey("NonExistent", "1.0.0", Map.of());
        Optional<CompiledRule> res = cache.get(key);
        assertTrue(res.isEmpty());

        CacheStatsSnapshot stats = cache.getStats();
        assertEquals(1, stats.misses());
        assertEquals(0.0, stats.hitRate());
    }

    private CompiledRule createDummyRule(String name) {
        return new CompiledRule() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public ExecutionResult execute(ExecutionContext context) {
                return ExecutionResult.success(true, 100);
            }
        };
    }
}
