package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CacheBenchmarkTest {

    @Test
    void testCacheBenchmarkOperations() throws Exception {
        CacheBenchmark benchmark = new CacheBenchmark();
        benchmark.setup();

        Optional<CompiledRule> hitResult = benchmark.benchmarkCacheHit();
        assertTrue(hitResult.isPresent());

        Optional<CompiledRule> missResult = benchmark.benchmarkCacheMiss();
        assertTrue(missResult.isEmpty());

        benchmark.benchmarkCacheEviction();
        benchmark.tearDown();
    }
}
