package com.helix.core.cache.l4;

/**
 * Atomic Redis Lua scripts for concurrency-safe rule caching and stampede prevention.
 */
public final class RedisLuaScripts {

    private RedisLuaScripts() {}

    /**
     * Atomic fetch-or-lock pattern:
     * KEYS[1]: rule bytecode key (e.g. "helix:rule:<hash>")
     * ARGV[1]: lock TTL in milliseconds (e.g. "5000")
     *
     * Returns a 2-element table:
     * - {1, bytecode} if cached entry is found
     * - {2, "LOCKED"} if lock successfully acquired (caller must compile)
     * - {3, "WAIT"}   if lock is already held by another node (caller must wait)
     */
    public static final String FETCH_OR_LOCK =
            "local val = redis.call('GET', KEYS[1]);\n" +
            "if val then\n" +
            "    return {1, val};\n" +
            "end\n" +
            "local lockKey = KEYS[1] .. ':lock';\n" +
            "local acquired = redis.call('SET', lockKey, '1', 'NX', 'PX', ARGV[1]);\n" +
            "if acquired then\n" +
            "    return {2, 'LOCKED'};\n" +
            "else\n" +
            "    return {3, 'WAIT'};\n" +
            "end\n";

    /**
     * Stores compiled bytecode, sets expiration TTL, deletes the compilation lease lock,
     * and publishes an update event on the cluster notification channel.
     *
     * KEYS[1]: rule bytecode key
     * KEYS[2]: notification channel
     * ARGV[1]: bytecode payload
     * ARGV[2]: TTL in seconds
     */
    public static final String STORE_AND_NOTIFY =
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]);\n" +
            "local lockKey = KEYS[1] .. ':lock';\n" +
            "redis.call('DEL', lockKey);\n" +
            "redis.call('PUBLISH', KEYS[2], KEYS[1]);\n" +
            "return 'OK';\n";
}
