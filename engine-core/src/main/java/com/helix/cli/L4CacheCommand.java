package com.helix.cli;

import com.helix.cli.output.OutputFormatter;
import com.helix.cli.ui.AsciiTableRenderer;
import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.cache.l4.BytecodeCodec;
import com.helix.core.cache.l4.L4RedisConfig;
import com.helix.core.cache.l4.L4RedisRuleCache;
import com.helix.core.cache.l4.RedisCacheInvalidator;
import com.helix.core.cache.l4.SerializedRuleData;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Picocli subcommand for inspecting and administering the L4 Redis distributed rule cache.
 */
@Command(
        name = "l4",
        description = "Manage and inspect L4 Redis distributed rule cache",
        mixinStandardHelpOptions = true
)
public class L4CacheCommand implements Callable<Integer> {

    @Option(names = {"-s", "--stats"}, description = "Display L4 Redis cache statistics and server metrics")
    private boolean stats;

    @Option(names = {"-c", "--clear"}, description = "Purge all cached rule bytecode from the L4 Redis cache")
    private boolean clear;

    @Option(names = {"-i", "--invalidate"}, description = "Evict and broadcast cluster invalidation for a specific rule name")
    private String invalidateRule;

    @Option(names = {"--host"}, defaultValue = "localhost", description = "Redis server hostname")
    private String host = "localhost";

    @Option(names = {"-p", "--port"}, defaultValue = "6379", description = "Redis server port")
    private int port = 6379;

    @Option(names = {"--timeout"}, defaultValue = "2000", description = "Redis connection timeout in milliseconds")
    private int timeout = 2000;

    @Option(names = {"-o", "--output"}, defaultValue = "table", description = "Output format: table, text, json, csv")
    private String outputFormat = "table";

    @Option(names = {"-q", "--quiet"}, description = "Suppress non-essential output")
    private boolean quiet;

    // Optional injected dependencies for unit testing
    private L4RedisRuleCache injectedCache;
    private JedisPool injectedPool;

    public L4CacheCommand() {
    }

    public L4CacheCommand(L4RedisRuleCache cache, JedisPool pool) {
        this.injectedCache = cache;
        this.injectedPool = pool;
    }

    @Override
    public Integer call() {
        try {
            L4RedisRuleCache cache = (injectedCache != null)
                    ? injectedCache
                    : new L4RedisRuleCache(host, port, timeout, true);

            JedisPool pool = (injectedPool != null)
                    ? injectedPool
                    : (!cache.isFallbackActive() ? getCacheJedisPool(cache) : null);

            // Execute action
            if (clear) {
                return executeClear(cache, pool);
            } else if (invalidateRule != null && !invalidateRule.isBlank()) {
                return executeInvalidate(cache, pool, invalidateRule.trim());
            } else {
                // Default action is displaying stats
                return executeStats(cache, pool);
            }
        } catch (Exception e) {
            TerminalRenderer.renderError("Failed to execute L4 cache operation: " + e.getMessage());
            return 1;
        }
    }

    private Integer executeClear(L4RedisRuleCache cache, JedisPool pool) {
        int deletedCount = 0;
        if (pool != null) {
            try (Jedis jedis = pool.getResource()) {
                Set<String> keys = jedis.keys("helix:rule:*");
                if (keys != null && !keys.isEmpty()) {
                    deletedCount = (int) jedis.del(keys.toArray(new String[0]));
                }
            } catch (Exception e) {
                TerminalRenderer.renderWarning("Could not query Redis directly for key count during clear: " + e.getMessage());
            }
        }

        cache.invalidateAll();

        if (!quiet) {
            TerminalRenderer.renderSuccess("Successfully purged all L4 Redis cache entries (" + deletedCount + " keys removed).");
        }
        return 0;
    }

    private Integer executeInvalidate(L4RedisRuleCache cache, JedisPool pool, String ruleName) {
        // 1. Broadcast cluster invalidation event
        if (pool != null) {
            try {
                RedisCacheInvalidator invalidator = new RedisCacheInvalidator(pool);
                invalidator.broadcastInvalidation(ruleName);
            } catch (Exception e) {
                TerminalRenderer.renderWarning("Failed to broadcast cluster invalidation event: " + e.getMessage());
            }
        }

        // 2. Search and purge matching rule keys in Redis
        int purgedKeys = 0;
        if (pool != null) {
            try (Jedis jedis = pool.getResource()) {
                Set<String> keys = jedis.keys("helix:rule:*");
                if (keys != null) {
                    for (String key : keys) {
                        if (key.endsWith(":lock")) {
                            continue;
                        }
                        byte[] payload = jedis.get(key.getBytes(StandardCharsets.UTF_8));
                        if (payload != null && payload.length > 0) {
                            try {
                                SerializedRuleData data = BytecodeCodec.decode(payload);
                                if (ruleName.equals(data.getRuleName())) {
                                    jedis.del(key, key + ":lock");
                                    purgedKeys++;
                                }
                            } catch (Exception ignored) {
                                // raw bytecode or other schema
                            }
                        }
                    }
                }
            } catch (Exception e) {
                TerminalRenderer.renderWarning("Failed to scan Redis for rule keys: " + e.getMessage());
            }
        }

        if (!quiet) {
            TerminalRenderer.renderSuccess(String.format("Successfully invalidated rule '%s' across L4 cache (%d keys evicted) and broadcasted cluster invalidation.",
                    ruleName, purgedKeys));
        }
        return 0;
    }

    private Integer executeStats(L4RedisRuleCache cache, JedisPool pool) {
        long hits = cache.getHitCount();
        long misses = cache.getMissCount();
        long totalRequests = hits + misses;
        double hitRatio = (totalRequests > 0) ? (hits * 100.0 / totalRequests) : 0.0;

        String endpoint = (injectedCache != null) ? "injected" : (host + ":" + port);
        boolean isConnected = (pool != null) && !cache.isFallbackActive();

        String usedMemory = "N/A (Offline)";
        String connectedClients = "N/A (Offline)";
        String redisVersion = "N/A";
        int ruleKeyCount = 0;

        if (isConnected) {
            try (Jedis jedis = pool.getResource()) {
                String memoryInfo = jedis.info("memory");
                usedMemory = extractInfoProperty(memoryInfo, "used_memory_human");

                String clientsInfo = jedis.info("clients");
                connectedClients = extractInfoProperty(clientsInfo, "connected_clients");

                String serverInfo = jedis.info("server");
                redisVersion = extractInfoProperty(serverInfo, "redis_version");

                Set<String> keys = jedis.keys("helix:rule:*");
                if (keys != null) {
                    ruleKeyCount = (int) keys.stream().filter(k -> !k.endsWith(":lock")).count();
                }
            } catch (Exception e) {
                isConnected = false;
                usedMemory = "Error querying memory";
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Redis Endpoint", endpoint);
        data.put("Connection Status", isConnected ? "ONLINE" : "OFFLINE (In-Memory Fallback)");
        data.put("Redis Version", redisVersion);
        data.put("Cache Hits", hits);
        data.put("Cache Misses", misses);
        data.put("Hit Ratio", String.format("%.2f%%", hitRatio));
        data.put("Used Memory", usedMemory);
        data.put("Connected Clients", connectedClients);
        data.put("Cached Rule Entries", ruleKeyCount);
        data.put("Fallback Mode Active", cache.isFallbackActive());

        if ("table".equalsIgnoreCase(outputFormat)) {
            System.out.println(AsciiTableRenderer.renderKeyValueTable("L4 REDIS DISTRIBUTED CACHE METRICS", data));
        } else {
            OutputFormatter formatter = new OutputFormatter(outputFormat, quiet);
            String output = formatter.formatResult("L4 Redis Distributed Cache Metrics", data);
            if (!output.isEmpty()) {
                System.out.println(output);
            }
        }

        return 0;
    }

    private String extractInfoProperty(String infoString, String propertyName) {
        if (infoString == null) return "N/A";
        for (String line : infoString.split("\r?\n")) {
            if (line.startsWith(propertyName + ":")) {
                return line.substring(propertyName.length() + 1).trim();
            }
        }
        return "N/A";
    }

    private JedisPool getCacheJedisPool(L4RedisRuleCache cache) {
        try {
            var field = L4RedisRuleCache.class.getDeclaredField("jedisPool");
            field.setAccessible(true);
            return (JedisPool) field.get(cache);
        } catch (Exception e) {
            return null;
        }
    }
}
