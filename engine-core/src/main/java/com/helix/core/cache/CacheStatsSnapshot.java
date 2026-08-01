package com.helix.core.cache;

public record CacheStatsSnapshot(
        long hitsL1,
        long hitsL2,
        long hitsL3,
        long misses,
        long promotionsL3ToL2,
        long promotionsL2ToL1,
        long demotionsL1ToL2,
        long demotionsL2ToL3,
        long evictions,
        long activeL1Count,
        long activeL2Count,
        long activeL3Count
) {
    public long totalHits() {
        return hitsL1 + hitsL2 + hitsL3;
    }

    public long totalRequests() {
        return totalHits() + misses;
    }

    public double hitRate() {
        long total = totalRequests();
        return total == 0 ? 0.0 : (double) totalHits() / total;
    }
}
