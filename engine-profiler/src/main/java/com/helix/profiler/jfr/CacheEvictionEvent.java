package com.helix.profiler.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * JFR Event triggered upon cache eviction.
 */
@Name("com.helix.CacheEviction")
@Label("Helix Cache Eviction")
@Category({"Helix", "Cache"})
@Description("Tracks rule class cache evictions across Strong, Soft, and Weak tiers")
public class CacheEvictionEvent extends Event {

    @Label("Rule Name")
    public String ruleName;

    @Label("Cache Tier")
    public String cacheTier;

    @Label("Eviction Reason")
    public String evictionReason;
}
