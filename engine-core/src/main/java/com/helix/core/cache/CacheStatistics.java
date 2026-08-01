package com.helix.core.cache;

import java.util.concurrent.atomic.AtomicLong;

public class CacheStatistics {

    private final AtomicLong hitsL1 = new AtomicLong(0);
    private final AtomicLong hitsL2 = new AtomicLong(0);
    private final AtomicLong hitsL3 = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong promotionsL3ToL2 = new AtomicLong(0);
    private final AtomicLong promotionsL2ToL1 = new AtomicLong(0);
    private final AtomicLong demotionsL1ToL2 = new AtomicLong(0);
    private final AtomicLong demotionsL2ToL3 = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    public void recordHit(CacheTier tier) {
        switch (tier) {
            case L1_STRONG -> hitsL1.incrementAndGet();
            case L2_SOFT -> hitsL2.incrementAndGet();
            case L3_WEAK -> hitsL3.incrementAndGet();
        }
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordPromotion(CacheTier from, CacheTier to) {
        if (from == CacheTier.L3_WEAK && to == CacheTier.L2_SOFT) {
            promotionsL3ToL2.incrementAndGet();
        } else if (from == CacheTier.L2_SOFT && to == CacheTier.L1_STRONG) {
            promotionsL2ToL1.incrementAndGet();
        }
    }

    public void recordDemotion(CacheTier from, CacheTier to) {
        if (from == CacheTier.L1_STRONG && to == CacheTier.L2_SOFT) {
            demotionsL1ToL2.incrementAndGet();
        } else if (from == CacheTier.L2_SOFT && to == CacheTier.L3_WEAK) {
            demotionsL2ToL3.incrementAndGet();
        }
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public CacheStatsSnapshot snapshot(long l1Count, long l2Count, long l3Count) {
        return new CacheStatsSnapshot(
                hitsL1.get(),
                hitsL2.get(),
                hitsL3.get(),
                misses.get(),
                promotionsL3ToL2.get(),
                promotionsL2ToL1.get(),
                demotionsL1ToL2.get(),
                demotionsL2ToL3.get(),
                evictions.get(),
                l1Count,
                l2Count,
                l3Count
        );
    }
}
