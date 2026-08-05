package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.core.RuleCompiler;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import org.openjdk.jmh.annotations.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite measuring L1/L2/L3 cache lookup throughput, hit vs miss latency, and eviction overhead.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@Fork(1)
public class CacheBenchmark {

    @Param({"100", "1000"})
    private int cacheCapacity;

    private TieredRuleCache cache;
    private CacheKey existingKey;
    private CacheKey missingKey;
    private RuleCompiler compiler;
    private CompiledRule compiledRule;

    @Setup
    public void setup() throws Exception {
        this.compiler = new RuleCompiler();
        this.cache = new TieredRuleCache(cacheCapacity, 10, TimeUnit.MINUTES);

        String ruleJson = """
                {
                    "name": "CacheBenchmarkRule",
                    "expression": "amount > 100",
                    "inputSchema": {
                        "amount": "double"
                    }
                }
                """;

        this.compiledRule = compiler.compile(ruleJson);
        this.existingKey = new CacheKey("CacheBenchmarkRule", "1.0.0", Map.of("amount", Double.class));
        this.missingKey = new CacheKey("MissingRule", "1.0.0", Map.of());

        // Pre-populate cache with existingKey
        cache.put(existingKey, compiledRule);
    }

    @TearDown
    public void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }

    @Benchmark
    public Optional<CompiledRule> benchmarkCacheHit() {
        return cache.get(existingKey);
    }

    @Benchmark
    public Optional<CompiledRule> benchmarkCacheMiss() {
        return cache.get(missingKey);
    }

    @Benchmark
    public void benchmarkCacheEviction() {
        CacheKey tempKey = new CacheKey("EvictKey_" + System.nanoTime(), "1.0.0", Map.of());
        cache.put(tempKey, compiledRule);
    }
}
