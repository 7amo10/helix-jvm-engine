package com.helix.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.helix.api.CompiledRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Three-tier rule cache supporting L1 (Strong Caffeine), L2 (SoftReference), and L3 (WeakReference).
 */
public class TieredRuleCache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TieredRuleCache.class);

    private final Cache<CacheKey, CompiledRule> l1Cache;
    private final Map<CacheKey, ReferenceManager.KeyedSoftReference<CacheKey, CompiledRule>> l2Cache = new ConcurrentHashMap<>();
    private final Map<CacheKey, ReferenceManager.KeyedWeakReference<CacheKey, CompiledRule>> l3Cache = new ConcurrentHashMap<>();
    private final Map<CacheKey, PromotionPolicy> promotionPolicies = new ConcurrentHashMap<>();

    private final ReferenceManager referenceManager = new ReferenceManager();
    private final CacheStatistics statistics = new CacheStatistics();

    public TieredRuleCache() {
        this(100, 10, TimeUnit.MINUTES);
    }

    public TieredRuleCache(long l1MaxSize, long l1ExpireAfterWriteMinutes, TimeUnit unit) {
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
        }
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

    private void cleanUpReferences() {
        referenceManager.processQueue();
        l2Cache.entrySet().removeIf(e -> e.getValue().get() == null);
        l3Cache.entrySet().removeIf(e -> e.getValue().get() == null);
    }

    @Override
    public void close() {
        clear();
        log.info("TieredRuleCache closed and cleared.");
    }
}
