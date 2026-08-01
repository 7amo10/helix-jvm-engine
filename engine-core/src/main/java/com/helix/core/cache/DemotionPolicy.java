package com.helix.core.cache;

/**
 * Policy defining conditions for demoting cache entries across tiers.
 */
public class DemotionPolicy {

    private final long maxIdleTimeMillis;

    public DemotionPolicy() {
        this(300_000L); // 5 minutes default idle time
    }

    public DemotionPolicy(long maxIdleTimeMillis) {
        this.maxIdleTimeMillis = maxIdleTimeMillis;
    }

    public boolean shouldDemote(long lastAccessedTimeMillis) {
        return (System.currentTimeMillis() - lastAccessedTimeMillis) > maxIdleTimeMillis;
    }
}
