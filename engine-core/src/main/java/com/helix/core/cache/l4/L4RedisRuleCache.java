package com.helix.core.cache.l4;

import com.helix.api.cache.DistributedRuleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise L4 Distributed Rule Cache backed by Redis.
 * Uses atomic Lua scripts for fetch-or-lock stampede protection and Pub/Sub invalidation.
 * Degrades gracefully to in-memory fallback when Redis is unreachable.
 */
public class L4RedisRuleCache implements DistributedRuleCache {

    private static final Logger log = LoggerFactory.getLogger(L4RedisRuleCache.class);
    private static final String KEY_PREFIX = "helix:rule:";
    private static final String NOTIFY_CHANNEL = "helix:cache:ready";
    private static final long DEFAULT_LOCK_TTL_MS = 5000L;
    private static final long WAIT_TIMEOUT_MS = 3000L;

    private final String host;
    private final int port;
    private final int timeoutMs;
    private final int ttlSeconds;
    private final boolean allowFallback;

    private final JedisPool jedisPool;
    private final boolean fallbackActive;
    private final ConcurrentHashMap<String, byte[]> localMemoryFallback = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public L4RedisRuleCache() {
        this(L4RedisConfig.fromSystemProperties());
    }

    public L4RedisRuleCache(L4RedisConfig config) {
        this(config.getHost(), config.getPort(), config.getTimeoutMs(),
             config.getPoolMaxTotal(), config.getTtlSeconds(), config.isFallbackEnabled());
    }

    public L4RedisRuleCache(String host, int port, int timeoutMs, boolean allowFallback) {
        this(host, port, timeoutMs, 64, 86400, allowFallback);
    }

    public L4RedisRuleCache(String host, int port, int timeoutMs, int maxTotalPool, int ttlSeconds, boolean allowFallback) {
        this.host = host != null ? host : "localhost";
        this.port = port > 0 ? port : 6379;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 2000;
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 86400;
        this.allowFallback = allowFallback;

        JedisPool pool = null;
        boolean fallback = false;
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(maxTotalPool);
            poolConfig.setMaxIdle(Math.min(16, maxTotalPool));
            poolConfig.setMinIdle(2);
            poolConfig.setTestOnBorrow(true);
            pool = new JedisPool(poolConfig, this.host, this.port, this.timeoutMs);
            try (Jedis j = pool.getResource()) {
                j.ping();
            }
            log.info("Successfully connected to Redis L4 cache at {}:{}", this.host, this.port);
        } catch (Exception e) {
            if (!allowFallback) {
                if (pool != null) {
                    pool.close();
                }
                throw new IllegalStateException("Redis L4 cache connection failed to " + this.host + ":" + this.port, e);
            }
            log.warn("Failed to connect to Redis at {}:{}. Activating local in-memory fallback mode", this.host, this.port);
            fallback = true;
            if (pool != null) {
                pool.close();
                pool = null;
            }
        }

        this.jedisPool = pool;
        this.fallbackActive = fallback;
    }

    @Override
    public Optional<byte[]> getBytecode(String ruleHash) {
        if (fallbackActive || jedisPool == null) {
            byte[] val = localMemoryFallback.get(ruleHash);
            if (val != null) {
                hitCount.incrementAndGet();
                return extractBytecode(val);
            }
            missCount.incrementAndGet();
            return Optional.empty();
        }

        byte[] keyBytes = (KEY_PREFIX + ruleHash).getBytes(StandardCharsets.UTF_8);
        byte[] lockTtlBytes = String.valueOf(DEFAULT_LOCK_TTL_MS).getBytes(StandardCharsets.UTF_8);

        try (Jedis jedis = jedisPool.getResource()) {
            Object res = jedis.eval(
                    RedisLuaScripts.FETCH_OR_LOCK.getBytes(StandardCharsets.UTF_8),
                    Collections.singletonList(keyBytes),
                    Collections.singletonList(lockTtlBytes)
            );

            if (res instanceof List<?> list && !list.isEmpty()) {
                long status = ((Number) list.get(0)).longValue();
                if (status == 1L) {
                    // Cache Hit: bytecode payload returned
                    byte[] payload = (byte[]) list.get(1);
                    hitCount.incrementAndGet();
                    return extractBytecode(payload);
                } else if (status == 2L) {
                    // Lock Acquired: current thread is the designated compiler
                    missCount.incrementAndGet();
                    return Optional.empty();
                } else if (status == 3L) {
                    // Another node is actively compiling: wait for completion
                    return waitForBytecode(jedis, keyBytes, WAIT_TIMEOUT_MS);
                }
            }
        } catch (Exception e) {
            log.warn("Redis error on getBytecode, attempting fallback retrieval: {}", e.getMessage());
            byte[] val = localMemoryFallback.get(ruleHash);
            if (val != null) {
                hitCount.incrementAndGet();
                return extractBytecode(val);
            }
        }

        missCount.incrementAndGet();
        return Optional.empty();
    }

    private Optional<byte[]> waitForBytecode(Jedis jedis, byte[] keyBytes, long maxWaitMs) {
        long start = System.currentTimeMillis();
        byte[] lockKeyBytes = (new String(keyBytes, StandardCharsets.UTF_8) + ":lock").getBytes(StandardCharsets.UTF_8);

        while (System.currentTimeMillis() - start < maxWaitMs) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            byte[] payload = jedis.get(keyBytes);
            if (payload != null) {
                hitCount.incrementAndGet();
                return extractBytecode(payload);
            }

            // If lock key was deleted and no bytecode exists, compilation was aborted
            if (!jedis.exists(lockKeyBytes)) {
                break;
            }
        }

        missCount.incrementAndGet();
        return Optional.empty();
    }

    private Optional<byte[]> extractBytecode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }
        try {
            SerializedRuleData data = BytecodeCodec.decode(payload);
            return Optional.of(data.getBytecode());
        } catch (Exception e) {
            return Optional.of(payload);
        }
    }

    @Override
    public void putBytecode(String ruleHash, String ruleName, String version, byte[] bytecode) {
        SerializedRuleData data = new SerializedRuleData(ruleName, version, bytecode, System.currentTimeMillis(), ruleHash);
        byte[] payload = BytecodeCodec.encode(data);

        localMemoryFallback.put(ruleHash, payload);

        if (fallbackActive || jedisPool == null) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            byte[] keyBytes = (KEY_PREFIX + ruleHash).getBytes(StandardCharsets.UTF_8);
            byte[] channelBytes = NOTIFY_CHANNEL.getBytes(StandardCharsets.UTF_8);
            byte[] ttlBytes = String.valueOf(ttlSeconds).getBytes(StandardCharsets.UTF_8);

            jedis.eval(
                    RedisLuaScripts.STORE_AND_NOTIFY.getBytes(StandardCharsets.UTF_8),
                    List.of(keyBytes, channelBytes),
                    List.of(payload, ttlBytes)
            );
        } catch (Exception e) {
            log.warn("Redis error on putBytecode for rule {}, cached locally: {}", ruleName, e.getMessage());
        }
    }

    @Override
    public void invalidate(String ruleHash) {
        localMemoryFallback.remove(ruleHash);

        if (!fallbackActive && jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = KEY_PREFIX + ruleHash;
                jedis.del(key, key + ":lock");
            } catch (Exception e) {
                log.warn("Redis error on invalidate for ruleHash {}: {}", ruleHash, e.getMessage());
            }
        }
    }

    @Override
    public void invalidateAll() {
        localMemoryFallback.clear();

        if (!fallbackActive && jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                Set<String> keys = jedis.keys(KEY_PREFIX + "*");
                if (keys != null && !keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                }
            } catch (Exception e) {
                log.warn("Redis error on invalidateAll: {}", e.getMessage());
            }
        }
    }

    public Optional<SerializedRuleData> getRuleData(String ruleHash) {
        byte[] payload = null;
        if (fallbackActive || jedisPool == null) {
            payload = localMemoryFallback.get(ruleHash);
        } else {
            try (Jedis jedis = jedisPool.getResource()) {
                payload = jedis.get((KEY_PREFIX + ruleHash).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("Redis error on getRuleData for {}: {}", ruleHash, e.getMessage());
                payload = localMemoryFallback.get(ruleHash);
            }
        }

        if (payload != null && payload.length > 0) {
            try {
                return Optional.of(BytecodeCodec.decode(payload));
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    @Override
    public long getHitCount() {
        return hitCount.get();
    }

    @Override
    public long getMissCount() {
        return missCount.get();
    }

    public boolean isFallbackActive() {
        return fallbackActive;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public boolean isAllowFallback() {
        return allowFallback;
    }

    @Override
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}
