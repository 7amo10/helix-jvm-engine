package com.helix.core.cache;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Evaluates access counters to determine whether a cache entry qualifies for tier promotion.
 */
public class PromotionPolicy {

    public static final int L3_TO_L2_THRESHOLD = 5;
    public static final int L2_TO_L1_THRESHOLD = 20;

    private final AtomicInteger accessCount = new AtomicInteger(0);

    public int incrementAndGetAccesses() {
        return accessCount.incrementAndGet();
    }

    public int getAccesses() {
        return accessCount.get();
    }

    public boolean shouldPromoteToL2() {
        return accessCount.get() >= L3_TO_L2_THRESHOLD;
    }

    public boolean shouldPromoteToL1() {
        return accessCount.get() >= L2_TO_L1_THRESHOLD;
    }
}
