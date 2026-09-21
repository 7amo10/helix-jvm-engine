package com.helix.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.helix.api.CompiledRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helix.api.cache.DistributedRuleCache;
import com.helix.core.cache.l4.RuleKeyHasher;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Multi-tier rule cache supporting L1 (Strong Caffeine), L2 (SoftReference), L3 (WeakReference),
 * and L4 (Distributed Redis) with promotion and cluster invalidation.
 */
public class TieredRuleCache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TieredRuleCache.class);

    private final Cache<CacheKey, CompiledRule> l1Cache;
    private final Map<CacheKey, ReferenceManager.KeyedSoftReference<CacheKey, CompiledRule>> l2Cache = new ConcurrentHashMap<>();
    private final Map<CacheKey, ReferenceManager.KeyedWeakReference<CacheKey, CompiledRule>> l3Cache = new ConcurrentHashMap<>();
    private final Map<CacheKey, PromotionPolicy> promotionPolicies = new ConcurrentHashMap<>();

    private final DistributedRuleCache l4Cache;
    private final Function<byte[], CompiledRule> l4RuleLoader;

    private final ReferenceManager referenceManager = new ReferenceManager();
    private final CacheStatistics statistics = new CacheStatistics();
    private final java.util.concurrent.locks.ReentrantLock cleanupLock = new java.util.concurrent.locks.ReentrantLock();

    public TieredRuleCache() {
        this(100, 10, TimeUnit.MINUTES);
    }

    public TieredRuleCache(long l1MaxSize, long l1ExpireAfterWriteMinutes, TimeUnit unit) {
        this(l1MaxSize, l1ExpireAfterWriteMinutes, unit, null, null);
    }

    public TieredRuleCache(long l1MaxSize, long l1ExpireAfterWriteMinutes, TimeUnit unit,
                           DistributedRuleCache l4Cache,
                           Function<byte[], CompiledRule> l4RuleLoader) {
        this.l4Cache = l4Cache;
        this.l4RuleLoader = l4RuleLoader;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .expireAfterWrite(l1ExpireAfterWriteMinutes, unit)
                .removalListener((key, value, cause) -> {
                    if (key != null && value != null && cause.wasEvicted()) {
                        statistics.recordEviction();
                        statistics.recordDemotion(CacheTier.L1_STRONG, CacheTier.L2_SOFT);
                        putL2((CacheKey) key, (CompiledRule) value);
                    }
                })
                .build();
    }

    public void put(CacheKey key, CompiledRule rule) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");
        cleanUpReferences();

        l1Cache.put(key, rule);
        promotionPolicies.computeIfAbsent(key, k -> new PromotionPolicy());
    }

    /**
     * Atomically hot-swaps an existing cached compiled rule with an optimized version with zero downtime.
     * Updates L1 Caffeine cache atomically, removes stale demoted references in L2/L3,
     * and publishes updated bytecode to the L4 distributed tier if configured.
     *
     * @param key      cache key identifying the rule
     * @param newRule  newly compiled rule instance
     * @param bytecode optional raw class bytecode to persist in L4
     */
    public void hotSwap(CacheKey key, CompiledRule newRule, byte[] bytecode) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(newRule, "newRule cannot be null");
        cleanUpReferences();

        l1Cache.put(key, newRule);
        l2Cache.remove(key);
        l3Cache.remove(key);
        promotionPolicies.computeIfAbsent(key, k -> new PromotionPolicy());

        if (l4Cache != null && bytecode != null) {
            String ruleHash = RuleKeyHasher.hashRule(key.getRuleName() + ":" + key.getVersion() + ":" + key.getSchemaHash());
            l4Cache.putBytecode(ruleHash, key.getRuleName(), key.getVersion(), bytecode);
        }
    }

    public void hotSwap(CacheKey key, CompiledRule newRule) {
        hotSwap(key, newRule, null);
    }

    public Optional<CompiledRule> get(CacheKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        cleanUpReferences();

        PromotionPolicy policy = promotionPolicies.computeIfAbsent(key, k -> new PromotionPolicy());
        int accesses = policy.incrementAndGetAccesses();

        // 1. Check L1 (Strong)
        CompiledRule rule = l1Cache.getIfPresent(key);
        if (rule != null) {
            statistics.recordHit(CacheTier.L1_STRONG);
            return Optional.of(rule);
        }

        // 2. Check L2 (Soft)
        ReferenceManager.KeyedSoftReference<CacheKey, CompiledRule> softRef = l2Cache.get(key);
        if (softRef != null) {
            rule = softRef.get();
            if (rule != null) {
                statistics.recordHit(CacheTier.L2_SOFT);
                if (policy.shouldPromoteToL1()) {
                    l2Cache.remove(key);
                    l1Cache.put(key, rule);
                    statistics.recordPromotion(CacheTier.L2_SOFT, CacheTier.L1_STRONG);
                }
                return Optional.of(rule);
            } else {
                l2Cache.remove(key);
            }
        }

        // 3. Check L3 (Weak)
        ReferenceManager.KeyedWeakReference<CacheKey, CompiledRule> weakRef = l3Cache.get(key);
        if (weakRef != null) {
            rule = weakRef.get();
            if (rule != null) {
                statistics.recordHit(CacheTier.L3_WEAK);
                if (policy.shouldPromoteToL2()) {
                    l3Cache.remove(key);
                    putL2(key, rule);
                    statistics.recordPromotion(CacheTier.L3_WEAK, CacheTier.L2_SOFT);
                }
                return Optional.of(rule);
            } else {
                l3Cache.remove(key);
            }
        }

        // 4. Check L4 (Distributed / Redis Cache)
        if (l4Cache != null) {
            String ruleHash = RuleKeyHasher.hashRule(key.getRuleName() + ":" + key.getVersion() + ":" + key.getSchemaHash());
            Optional<byte[]> l4Bytes = l4Cache.getBytecode(ruleHash);
            if (l4Bytes.isPresent() && l4RuleLoader != null) {
                try {
                    CompiledRule loadedRule = l4RuleLoader.apply(l4Bytes.get());
                    if (loadedRule != null) {
                        l1Cache.put(key, loadedRule);
                        statistics.recordHit(CacheTier.L4_REDIS);
                        return Optional.of(loadedRule);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load compiled rule from L4 bytecode for key {}: {}", key, e.getMessage());
                }
            }
        }

        statistics.recordMiss();
        return Optional.empty();
    }

    public void putL2(CacheKey key, CompiledRule rule) {
        l2Cache.put(key, new ReferenceManager.KeyedSoftReference<>(key, rule, referenceManager.getReferenceQueue()));
    }

    public void putL3(CacheKey key, CompiledRule rule) {
        l3Cache.put(key, new ReferenceManager.KeyedWeakReference<>(key, rule, referenceManager.getReferenceQueue()));
    }

    public void invalidate(CacheKey key) {
        if (key != null) {
            l1Cache.invalidate(key);
            l2Cache.remove(key);
            l3Cache.remove(key);
            promotionPolicies.remove(key);
            if (l4Cache != null) {
                String ruleHash = RuleKeyHasher.hashRule(key.getRuleName() + ":" + key.getVersion() + ":" + key.getSchemaHash());
                l4Cache.invalidate(ruleHash);
            }
        }
    }

    /**
     * Purges all cache entries matching the specified rule name across local L1, L2, and L3 tiers.
     *
     * @param ruleName name of the rule to evict
     */
    public void invalidateByName(String ruleName) {
        if (ruleName == null) return;
        l1Cache.asMap().keySet().removeIf(k -> ruleName.equals(k.getRuleName()));
        l2Cache.keySet().removeIf(k -> ruleName.equals(k.getRuleName()));
        l3Cache.keySet().removeIf(k -> ruleName.equals(k.getRuleName()));
        promotionPolicies.keySet().removeIf(k -> ruleName.equals(k.getRuleName()));
    }

    public DistributedRuleCache getL4Cache() {
        return l4Cache;
    }

    public void clear() {
        l1Cache.invalidateAll();
        l2Cache.clear();
        l3Cache.clear();
        promotionPolicies.clear();
    }

    public CacheStatsSnapshot getStats() {
        cleanUpReferences();
        return statistics.snapshot(l1Cache.estimatedSize(), l2Cache.size(), l3Cache.size());
    }

    public CacheStatistics getCacheStatistics() {
        return statistics;
    }

    private void cleanUpReferences() {
        if (cleanupLock.tryLock()) {
            try {
                referenceManager.processQueue();
                l2Cache.entrySet().removeIf(e -> e.getValue().get() == null);
                l3Cache.entrySet().removeIf(e -> e.getValue().get() == null);
            } finally {
                cleanupLock.unlock();
            }
        }
    }

    @Override
    public void close() {
        clear();
        log.info("TieredRuleCache closed and cleared.");
    }
}
