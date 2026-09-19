package com.helix.core.cache.l4;

import com.helix.core.cache.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Objects;

/**
 * Publisher for broadcasting cache invalidation events across cluster nodes via Redis Pub/Sub.
 */
public class RedisCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidator.class);
    public static final String DEFAULT_INVALIDATION_CHANNEL = "helix:cache:invalidation";

    private final JedisPool jedisPool;
    private final String channel;

    public RedisCacheInvalidator(JedisPool jedisPool) {
        this(jedisPool, DEFAULT_INVALIDATION_CHANNEL);
    }

    public RedisCacheInvalidator(JedisPool jedisPool, String channel) {
        this.jedisPool = jedisPool;
        this.channel = channel != null ? channel : DEFAULT_INVALIDATION_CHANNEL;
    }

    /**
     * Broadcasts an invalidation event for the specified rule name.
     *
     * @param ruleName name of the rule to invalidate across the cluster
     */
    public void broadcastInvalidation(String ruleName) {
        Objects.requireNonNull(ruleName, "ruleName cannot be null");
        if (jedisPool == null) {
            log.debug("Redis pool not configured; skipping cluster invalidation broadcast for rule: {}", ruleName);
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, ruleName);
            log.debug("Broadcasted cache invalidation event on channel '{}' for rule: {}", channel, ruleName);
        } catch (Exception e) {
            log.warn("Failed to broadcast cache invalidation on channel '{}' for rule: {}", channel, ruleName, e);
        }
    }

    /**
     * Broadcasts an invalidation event using a CacheKey.
     *
     * @param key cache key of the rule
     */
    public void broadcastInvalidation(CacheKey key) {
        if (key != null) {
            broadcastInvalidation(key.getRuleName());
        }
    }

    public String getChannel() {
        return channel;
    }
}
