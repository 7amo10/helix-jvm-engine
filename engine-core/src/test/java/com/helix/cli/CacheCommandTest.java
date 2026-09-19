package com.helix.cli;

import com.helix.core.cache.l4.L4RedisRuleCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class CacheCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("Should display cache command help menu")
    void testCacheHelp() {
        CommandLine cmd = new CommandLine(new CacheCommand());
        int exitCode = cmd.execute("--help");
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("cache"));
        assertTrue(outContent.toString().contains("l4"));
    }

    @Test
    @DisplayName("Should display l4 subcommand help menu")
    void testL4CacheHelp() {
        CommandLine cmd = new CommandLine(new CacheCommand());
        int exitCode = cmd.execute("l4", "--help");
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("--stats"));
        assertTrue(outContent.toString().contains("--clear"));
        assertTrue(outContent.toString().contains("--invalidate"));
    }

    @Test
    @DisplayName("Should print L4 cache statistics in formatted ASCII table")
    void testL4CacheStatsTable() {
        // Uses graceful offline fallback if Redis is not running locally
        CommandLine cmd = new CommandLine(new CacheCommand());
        int exitCode = cmd.execute("l4", "--stats", "--timeout", "100");
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("L4 REDIS DISTRIBUTED CACHE METRICS"));
        assertTrue(output.contains("Redis Endpoint"));
        assertTrue(output.contains("Connection Status"));
        assertTrue(output.contains("Cache Hits"));
        assertTrue(output.contains("Cache Misses"));
        assertTrue(output.contains("Hit Ratio"));
        assertTrue(output.contains("Used Memory"));
        assertTrue(output.contains("Connected Clients"));
    }

    @Test
    @DisplayName("Should print L4 cache statistics in JSON format")
    void testL4CacheStatsJson() {
        CommandLine cmd = new CommandLine(new CacheCommand());
        int exitCode = cmd.execute("l4", "--stats", "--output", "json", "--timeout", "100");
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("\"Redis Endpoint\""));
        assertTrue(output.contains("\"Connection Status\""));
        assertTrue(output.contains("\"Cache Hits\""));
    }

    @Test
    @DisplayName("Should print L4 cache statistics in CSV format")
    void testL4CacheStatsCsv() {
        CommandLine cmd = new CommandLine(new CacheCommand());
        int exitCode = cmd.execute("l4", "--stats", "--output", "csv", "--timeout", "100");
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("Key,Value"));
        assertTrue(output.contains("\"Redis Endpoint\""));
    }

    @Test
    @DisplayName("Should execute L4 cache clear operation")
    void testL4CacheClear() {
        L4RedisRuleCache cache = new L4RedisRuleCache("localhost", 6379, 100, true);
        cache.putBytecode("hash-1", "RuleA", "1.0", new byte[]{1, 2, 3});

        L4CacheCommand l4Cmd = new L4CacheCommand(cache, null);
        CommandLine cmd = new CommandLine(l4Cmd);
        int exitCode = cmd.execute("--clear");
        assertEquals(0, exitCode);

        assertTrue(cache.getBytecode("hash-1").isEmpty());
        assertTrue(outContent.toString().contains("Successfully purged all L4 Redis cache entries"));
    }

    @Test
    @DisplayName("Should execute L4 cache rule invalidation operation")
    void testL4CacheInvalidate() {
        L4RedisRuleCache cache = new L4RedisRuleCache("localhost", 6379, 100, true);
        cache.putBytecode("hash-2", "OrderValidationRule", "1.0", new byte[]{4, 5, 6});

        L4CacheCommand l4Cmd = new L4CacheCommand(cache, null);
        CommandLine cmd = new CommandLine(l4Cmd);
        int exitCode = cmd.execute("--invalidate", "OrderValidationRule");
        assertEquals(0, exitCode);

        assertTrue(outContent.toString().contains("Successfully invalidated rule 'OrderValidationRule'"));
    }
}
