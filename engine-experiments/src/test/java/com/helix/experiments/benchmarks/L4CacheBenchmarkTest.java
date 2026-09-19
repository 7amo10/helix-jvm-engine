package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class L4CacheBenchmarkTest {

    @Test
    @DisplayName("Should execute all L4CacheBenchmark operations cleanly")
    void testL4CacheBenchmarkOperations() throws Exception {
        L4CacheBenchmark benchmark = new L4CacheBenchmark();
        benchmark.setup();

        try {
            // L1 Caffeine Cache Hit
            CompiledRule l1Hit = benchmark.benchmarkL1CaffeineCacheHit();
            assertNotNull(l1Hit, "L1 cache hit must return compiled rule");

            // Tiered L1 Cache Hit
            Optional<CompiledRule> tieredHit = benchmark.benchmarkTieredCacheL1Hit();
            assertTrue(tieredHit.isPresent(), "Tiered L1 cache hit must be present");

            // L4 Redis Cache Hit
            Optional<byte[]> l4Hit = benchmark.benchmarkL4RedisCacheHit();
            assertTrue(l4Hit.isPresent(), "L4 Redis cache hit must be present");
            assertTrue(l4Hit.get().length > 0, "L4 Redis cache hit must contain bytecode");

            // Cold ASM Rule Compilation
            CompiledRule coldCompiled = benchmark.benchmarkColdAsmRuleCompilation();
            assertNotNull(coldCompiled, "Cold ASM compilation must produce compiled rule");
            assertEquals("L4CacheBenchmarkRule", coldCompiled.getName());
        } finally {
            benchmark.tearDown();
        }
    }
}
