package com.helix.api.cache;

import java.util.Optional;

/**
 * Contract for out-of-process distributed rule cache tiers (e.g. Redis, Memcached).
 * Stores compiled HotSpot rule bytecode keyed by deterministic rule hashes.
 */
public interface DistributedRuleCache extends AutoCloseable {

    /**
     * Retrieves compiled bytecode by its deterministic rule hash.
     *
     * @param ruleHash SHA-256 rule hash
     * @return Optional containing raw class bytecode if present in the distributed tier
     */
    Optional<byte[]> getBytecode(String ruleHash);

    /**
     * Stores compiled bytecode with rule metadata in the distributed cache tier.
     *
     * @param ruleHash   SHA-256 rule hash
     * @param ruleName   name of the rule
     * @param version    rule version
     * @param bytecode   compiled JVM class bytes
     */
    void putBytecode(String ruleHash, String ruleName, String version, byte[] bytecode);

    /**
     * Evicts a single rule from the distributed cache.
     *
     * @param ruleHash SHA-256 rule hash
     */
    void invalidate(String ruleHash);

    /**
     * Evicts all cached rules across the distributed cluster.
     */
    void invalidateAll();

    /**
     * Returns the total number of cache hits on this distributed tier.
     *
     * @return cumulative hit count
     */
    long getHitCount();

    /**
     * Returns the total number of cache misses on this distributed tier.
     *
     * @return cumulative miss count
     */
    long getMissCount();

    /**
     * Closes connections or client pools associated with this distributed cache tier.
     */
    @Override
    void close();
}
