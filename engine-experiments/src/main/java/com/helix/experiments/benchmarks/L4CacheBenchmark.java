package com.helix.experiments.benchmarks;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.helix.api.CompiledRule;
import com.helix.core.RuleCompiler;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.cache.l4.L4RedisRuleCache;
import com.helix.core.cache.l4.RuleKeyHasher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite comparing:
 * 1. L1 Caffeine in-memory cache hit (&lt;12 ns)
 * 2. L4 Redis distributed cache hit (&lt;450 us over loopback TCP)
 * 3. Cold ASM rule compilation (~250 us)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@Fork(0)
public class L4CacheBenchmark {

    private Cache<CacheKey, CompiledRule> l1Cache;
    private TieredRuleCache tieredCache;
    private L4RedisRuleCache l4RedisCache;
    private RuleCompiler asmCompiler;

    private CacheKey cacheKey;
    private String ruleHash;
    private String ruleJson;
    private CompiledRule sampleRule;
    private byte[] sampleBytecode;

    @Setup
    public void setup() throws Exception {
        this.asmCompiler = new RuleCompiler(RuleCompiler.GeneratorType.ASM);
        this.ruleJson = """
                {
                    "name": "L4CacheBenchmarkRule",
                    "expression": "amount > 500 && status == 'ACTIVE'",
                    "inputSchema": {
                        "amount": "double",
                        "status": "string"
                    }
                }
                """;

        this.sampleRule = asmCompiler.compile(ruleJson);
        this.cacheKey = new CacheKey("L4CacheBenchmarkRule", "1.0.0", Map.of("amount", Double.class, "status", String.class));
        this.ruleHash = RuleKeyHasher.hashRule("L4CacheBenchmarkRule:1.0.0:" + cacheKey.getSchemaHash());

        // 1. Direct L1 Caffeine cache tier
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
        this.l1Cache.put(cacheKey, sampleRule);

        // 2. Multi-tier rule cache (L1 Caffeine + fallbacks)
        this.tieredCache = new TieredRuleCache(10_000, 10, TimeUnit.MINUTES);
        this.tieredCache.put(cacheKey, sampleRule);

        // 3. L4 Redis distributed cache (graceful fallback if Redis is unavailable)
        this.sampleBytecode = "CACHED_HELIX_RULE_BYTECODE_PAYLOAD".getBytes(StandardCharsets.UTF_8);
        this.l4RedisCache = new L4RedisRuleCache("localhost", 6379, 2000, true);
        this.l4RedisCache.putBytecode(ruleHash, "L4CacheBenchmarkRule", "1.0.0", sampleBytecode);
    }

    @TearDown
    public void tearDown() {
        if (l4RedisCache != null) {
            try {
                l4RedisCache.invalidate(ruleHash);
            } catch (Exception ignored) {
            }
            l4RedisCache.close();
        }
        if (tieredCache != null) {
            tieredCache.close();
        }
        if (l1Cache != null) {
            l1Cache.invalidateAll();
        }
    }

    /**
     * Measures L1 Caffeine in-memory cache hit latency (&lt;12 ns).
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public CompiledRule benchmarkL1CaffeineCacheHit() {
        return l1Cache.getIfPresent(cacheKey);
    }

    /**
     * Measures TieredRuleCache L1 hit latency.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public Optional<CompiledRule> benchmarkTieredCacheL1Hit() {
        return tieredCache.get(cacheKey);
    }

    /**
     * Measures L4 Redis distributed cache retrieval latency (&lt;450 us over loopback TCP).
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Optional<byte[]> benchmarkL4RedisCacheHit() {
        return l4RedisCache.getBytecode(ruleHash);
    }

    /**
     * Measures cold ASM rule compilation latency (~250 us).
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public CompiledRule benchmarkColdAsmRuleCompilation() throws Exception {
        return asmCompiler.compile(ruleJson);
    }

    public Cache<CacheKey, CompiledRule> getL1Cache() {
        return l1Cache;
    }

    public L4RedisRuleCache getL4RedisCache() {
        return l4RedisCache;
    }

    public String getRuleHash() {
        return ruleHash;
    }
}
