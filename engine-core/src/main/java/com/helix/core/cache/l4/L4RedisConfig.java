package com.helix.core.cache.l4;

/**
 * Configuration parameters for Helix Redis L4 distributed rule caching.
 */
public final class L4RedisConfig {

    public static final String KEY_HOST = "helix.cache.l4.redis.host";
    public static final String KEY_PORT = "helix.cache.l4.redis.port";
    public static final String KEY_POOL_MAX_TOTAL = "helix.cache.l4.redis.pool.max-total";
    public static final String KEY_TTL_SECONDS = "helix.cache.l4.redis.ttl-seconds";
    public static final String KEY_TIMEOUT_MS = "helix.cache.l4.redis.timeout-ms";
    public static final String KEY_FALLBACK_ENABLED = "helix.cache.l4.redis.fallback-enabled";

    private final String host;
    private final int port;
    private final int timeoutMs;
    private final int poolMaxTotal;
    private final int ttlSeconds;
    private final boolean fallbackEnabled;

    public L4RedisConfig(String host, int port, int timeoutMs, int poolMaxTotal, int ttlSeconds, boolean fallbackEnabled) {
        this.host = host != null ? host : "localhost";
        this.port = port > 0 ? port : 6379;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 2000;
        this.poolMaxTotal = poolMaxTotal > 0 ? poolMaxTotal : 64;
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 86400;
        this.fallbackEnabled = fallbackEnabled;
    }

    public static L4RedisConfig fromSystemProperties() {
        String host = System.getProperty(KEY_HOST, "localhost");
        int port = Integer.parseInt(System.getProperty(KEY_PORT, "6379"));
        int timeoutMs = Integer.parseInt(System.getProperty(KEY_TIMEOUT_MS, "2000"));
        int poolMaxTotal = Integer.parseInt(System.getProperty(KEY_POOL_MAX_TOTAL, "64"));
        int ttlSeconds = Integer.parseInt(System.getProperty(KEY_TTL_SECONDS, "86400"));
        boolean fallback = Boolean.parseBoolean(System.getProperty(KEY_FALLBACK_ENABLED, "true"));

        return new L4RedisConfig(host, port, timeoutMs, poolMaxTotal, ttlSeconds, fallback);
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getTimeoutMs() { return timeoutMs; }
    public int getPoolMaxTotal() { return poolMaxTotal; }
    public int getTtlSeconds() { return ttlSeconds; }
    public boolean isFallbackEnabled() { return fallbackEnabled; }
}
