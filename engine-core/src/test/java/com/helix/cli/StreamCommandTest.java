package com.helix.cli;

import com.helix.api.ExecutionContext;
import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.StreamResult;
import com.helix.core.stream.disruptor.DisruptorConfig;
import com.helix.core.stream.disruptor.DisruptorStreamBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StreamCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @TempDir
    Path tempDir;

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

    private File createTestRuleFile() throws Exception {
        String json = """
                {
                    "name": "OrderStreamingRule",
                    "version": "1.0.0",
                    "description": "Evaluates streaming order events",
                    "category": "ORDERS",
                    "expression": "amount > 100",
                    "inputSchema": {
                        "amount": "double"
                    }
                }
                """;
        Path rulePath = tempDir.resolve("order-rule.json");
        Files.writeString(rulePath, json);
        return rulePath.toFile();
    }

    @Test
    @DisplayName("Should display stream command help options")
    void testStreamHelp() {
        CommandLine cmd = new CommandLine(new StreamCommand());
        int exitCode = cmd.execute("--help");
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("--topic"));
        assertTrue(output.contains("--rule"));
        assertTrue(output.contains("--disruptor"));
        assertTrue(output.contains("--duration"));
        assertTrue(output.contains("--bootstrap-servers"));
    }

    @Test
    @DisplayName("Should report error when rule file does not exist")
    void testMissingRuleFile() {
        CommandLine cmd = new CommandLine(new StreamCommand());
        int exitCode = cmd.execute("-t", "orders", "-r", "non-existent-rule.json");
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Rule file does not exist"));
    }

    @Test
    @DisplayName("Should report error when rule file contains invalid JSON")
    void testInvalidRuleJson() throws Exception {
        Path invalidRule = tempDir.resolve("invalid.json");
        Files.writeString(invalidRule, "{ not valid json ");

        CommandLine cmd = new CommandLine(new StreamCommand());
        int exitCode = cmd.execute("-t", "orders", "-r", invalidRule.toString());
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Stream engine failed"));
    }

    @Test
    @DisplayName("Should launch Disruptor stream daemon and shutdown gracefully after duration")
    void testDisruptorStreamExecutionWithDuration() throws Exception {
        File ruleFile = createTestRuleFile();

        CommandLine cmd = new CommandLine(new StreamCommand());
        int exitCode = cmd.execute(
                "-t", "order-events",
                "-r", ruleFile.getAbsolutePath(),
                "--disruptor",
                "--duration", "1"
        );
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("HELIX STREAM PROCESSING DAEMON"));
        assertTrue(output.contains("LMAX Disruptor Ring Buffer"));
        assertTrue(output.contains("Topic: order-events"));
        assertTrue(output.contains("HELIX STREAMING METRICS SUMMARY"));
        assertTrue(output.contains("Total Processed"));
        assertTrue(output.contains("Remaining Capacity"));
    }

    @Test
    @DisplayName("Should handle SIGINT / triggerShutdown gracefully and print final metrics table")
    void testGracefulShutdownHook() throws Exception {
        File ruleFile = createTestRuleFile();

        StreamCommand streamCmd = new StreamCommand();
        CommandLine cmd = new CommandLine(streamCmd);

        // Run daemon in background virtual thread
        CompletableFuture<Integer> execution = CompletableFuture.supplyAsync(() ->
                cmd.execute(
                        "-t", "trades",
                        "-r", ruleFile.getAbsolutePath(),
                        "--disruptor",
                        "--duration", "0" // Run indefinitely
                )
        );

        // Wait briefly for daemon to initialize
        Thread.sleep(300);

        // Trigger shutdown signal (simulating SIGINT)
        streamCmd.triggerShutdown();

        // Daemon should terminate cleanly with code 0 within 3 seconds
        Integer exitCode = execution.get(3, TimeUnit.SECONDS);
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("HELIX STREAMING METRICS SUMMARY"));
        assertTrue(output.contains("Metric"));
        assertTrue(output.contains("Value"));
    }

    @Test
    @DisplayName("Should evaluate live stream events and update streaming stats")
    void testLiveEventEvaluationAndStats() throws Exception {
        File ruleFile = createTestRuleFile();

        DisruptorConfig config = DisruptorConfig.builder()
                .ringBufferSize(1024)
                .yieldingWaitStrategy()
                .build();
        DisruptorStreamBridge bridge = new DisruptorStreamBridge(config);

        StreamCommand streamCmd = new StreamCommand(bridge);
        CommandLine cmd = new CommandLine(streamCmd);

        CompletableFuture<Integer> runFuture = CompletableFuture.supplyAsync(() ->
                cmd.execute(
                        "-t", "eval-topic",
                        "-r", ruleFile.getAbsolutePath(),
                        "--disruptor",
                        "--duration", "0"
                )
        );

        // Wait for handler to register
        Thread.sleep(200);

        // Publish live event
        ExecutionContext ctx = new ExecutionContext(Map.of("amount", 250.0));
        RuleEvent event = RuleEvent.of("eval-topic", "OrderStreamingRule", ctx);
        CompletableFuture<StreamResult> publishFuture = bridge.publish(event);

        StreamResult result = publishFuture.get(2, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getResult().orElse(null));

        // Shutdown stream daemon
        streamCmd.triggerShutdown();
        int exitCode = runFuture.get(3, TimeUnit.SECONDS);
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("OrderStreamingRule"));
        assertTrue(output.contains("HELIX STREAMING METRICS SUMMARY"));
    }
}
