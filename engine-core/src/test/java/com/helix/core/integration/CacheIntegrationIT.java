package com.helix.core.integration;

import com.helix.api.CompiledRule;
import com.helix.core.RuleCompiler;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.CacheStatsSnapshot;
import com.helix.core.cache.TieredRuleCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CacheIntegrationIT {

    private RuleCompiler compiler;
    private TieredRuleCache cache;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler();
        cache = new TieredRuleCache();
    }

    @AfterEach
    void tearDown() {
        cache.close();
    }

    @Test
    @DisplayName("IT: Verify tier promotions from L3 -> L2 -> L1 across multiple rule access cycles")
    void testTieredCachePromotionCycle() throws Exception {
        String json = """
                {
                    "name": "IntegrationCacheRule",
                    "expression": "a + b",
                    "inputSchema": {
                        "a": "int",
                        "b": "int"
                    }
                }
                """;

        CompiledRule rule = compiler.compile(json);
        CacheKey key = new CacheKey("IntegrationCacheRule", "1.0.0", Map.of("a", Integer.class, "b", Integer.class));

        // Manually place in L3
        cache.putL3(key, rule);

        // 5 reads -> promotes L3 to L2
        for (int i = 0; i < 5; i++) {
            Optional<CompiledRule> res = cache.get(key);
            assertTrue(res.isPresent());
        }

        CacheStatsSnapshot stats1 = cache.getStats();
        assertEquals(5, stats1.hitsL3());
        assertEquals(1, stats1.promotionsL3ToL2());

        // 15 more reads (total 20 reads) -> promotes L2 to L1
        for (int i = 0; i < 15; i++) {
            Optional<CompiledRule> res = cache.get(key);
            assertTrue(res.isPresent());
        }

        CacheStatsSnapshot stats2 = cache.getStats();
        assertEquals(1, stats2.promotionsL2ToL1());

        // Subsequent read hits L1
        Optional<CompiledRule> l1Res = cache.get(key);
        assertTrue(l1Res.isPresent());
        CacheStatsSnapshot stats3 = cache.getStats();
        assertEquals(1, stats3.hitsL1());
    }
}
