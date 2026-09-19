package com.helix.cli;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionResult;
import com.helix.api.stream.StreamBridge;
import com.helix.api.stream.StreamResult;
import com.helix.api.stream.StreamStats;
import com.helix.cli.output.OutputFormatter;
import com.helix.cli.ui.AsciiTableRenderer;
import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.RuleCompiler;
import com.helix.core.stream.disruptor.DisruptorConfig;
import com.helix.core.stream.disruptor.DisruptorStreamBridge;
import com.helix.core.stream.kafka.CommitMode;
import com.helix.core.stream.kafka.KafkaStreamConfig;
import com.helix.core.stream.kafka.KafkaStreamEngine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Picocli subcommand for streaming rule execution via Apache Kafka or LMAX Disruptor.
 * Runs as a daemon with graceful SIGINT shutdown handling and runtime stats reporting.
 */
@Command(
        name = "stream",
        description = "Launch daemon stream rule evaluation with Kafka or LMAX Disruptor",
        mixinStandardHelpOptions = true
)
public class StreamCommand implements CliCommand {

    @Option(names = {"-t", "--topic"}, required = true, description = "Stream topic to subscribe to")
    private String topic;

    @Option(names = {"-r", "--rule"}, required = true, description = "Path to JSON rule file to compile and evaluate")
    private File ruleFile;

    @Option(names = {"--disruptor"}, description = "Use in-process LMAX Disruptor ring buffer instead of Kafka")
    private boolean disruptor;

    @Option(names = {"--bootstrap-servers"}, defaultValue = "localhost:9092", description = "Kafka bootstrap servers")
    private String bootstrapServers = "localhost:9092";

    @Option(names = {"--group-id"}, defaultValue = "helix-stream-group", description = "Kafka consumer group ID")
    private String groupId = "helix-stream-group";

    @Option(names = {"--output-topic"}, description = "Kafka output topic to publish evaluation results")
    private String outputTopic;

    @Option(names = {"--commit-mode"}, defaultValue = "SYNC", description = "Kafka commit mode: SYNC, ASYNC, NONE")
    private String commitMode = "SYNC";

    @Option(names = {"--ring-buffer-size"}, defaultValue = "1024", description = "LMAX Disruptor ring buffer capacity")
    private int ringBufferSize = 1024;

    @Option(names = {"--max-in-flight"}, defaultValue = "100", description = "Max in-flight records before backpressure pause")
    private int maxInFlight = 100;

    @Option(names = {"--low-watermark"}, defaultValue = "25", description = "Low watermark for backpressure resume")
    private int lowWatermark = 25;

    @Option(names = {"--poll-timeout"}, defaultValue = "100", description = "Kafka poll timeout in milliseconds")
    private int pollTimeoutMs = 100;

    @Option(names = {"-d", "--duration"}, defaultValue = "0", description = "Duration in seconds to run daemon before clean exit (0 = run indefinitely)")
    private int durationSeconds = 0;

    @Option(names = {"-o", "--output"}, defaultValue = "table", description = "Output format: table, text, json, csv")
    private String outputFormat = "table";

    @Option(names = {"-q", "--quiet"}, description = "Suppress non-essential output")
    private boolean quiet;

    // Optional injected bridge for unit testing
    private StreamBridge injectedBridge;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public StreamCommand() {
    }

    public StreamCommand(StreamBridge bridge) {
        this.injectedBridge = bridge;
    }

    public void setInjectedBridge(StreamBridge bridge) {
        this.injectedBridge = bridge;
    }

    public void triggerShutdown() {
        if (stopped.compareAndSet(false, true)) {
            shutdownLatch.countDown();
        }
    }

    @Override
    public Integer call() {
        try {
            if (ruleFile == null || !ruleFile.exists()) {
                TerminalRenderer.renderError("Rule file does not exist: " + ruleFile);
                return 1;
            }

            // 1. Compile rule from JSON
            String ruleJson = Files.readString(ruleFile.toPath());
            RuleCompiler compiler = new RuleCompiler();
            CompiledRule rule = compiler.compile(ruleJson);

            if (!quiet) {
                TerminalRenderer.renderSuccess(String.format("Successfully compiled rule '%s' (v%s).",
                        rule.getName(), rule.getVersion()));
            }

            // 2. Initialize StreamBridge
            StreamBridge bridge = (injectedBridge != null) ? injectedBridge : createBridge();

            // 3. Subscribe rule evaluation handler to topic
            bridge.subscribe(topic, event -> {
                long startNanos = System.nanoTime();
                try {
                    ExecutionResult exec = rule.execute(event.getContext());
                    long duration = System.nanoTime() - startNanos;
                    StreamResult result = exec.isSuccess()
                            ? StreamResult.success(event.getEventId(), topic, rule.getName(), exec.getResult().orElse(null), duration)
                            : StreamResult.failure(event.getEventId(), topic, rule.getName(),
                            exec.getError().orElse(new RuntimeException("Rule evaluation failed")), duration);

                    if (!quiet) {
                        TerminalRenderer.renderInfo(String.format("Event [%s] -> '%s': %s (duration: %.3f ms)",
                                event.getEventId(), rule.getName(),
                                result.isSuccess() ? "SUCCESS" : "FAILURE",
                                duration / 1_000_000.0));
                    }
                    return result;
                } catch (Throwable t) {
                    long duration = System.nanoTime() - startNanos;
                    return StreamResult.failure(event.getEventId(), topic, rule.getName(), t, duration);
                }
            });

            // 4. Register SIGINT shutdown hook
            Thread shutdownHook = new Thread(() -> {
                if (stopped.compareAndSet(false, true)) {
                    TerminalRenderer.renderInfo("Shutdown signal received (SIGINT). Stopping stream engine...");
                    bridge.shutdown(Duration.ofSeconds(5));
                    shutdownLatch.countDown();
                }
            }, "helix-stream-sigint-hook");

            Runtime.getRuntime().addShutdownHook(shutdownHook);

            if (!quiet) {
                TerminalRenderer.renderHeader("HELIX STREAM PROCESSING DAEMON");
                TerminalRenderer.renderInfo("Engine: " + (disruptor ? "LMAX Disruptor Ring Buffer" : "Apache Kafka Clustered Engine"));
                TerminalRenderer.renderInfo("Topic: " + topic);
                TerminalRenderer.renderInfo("Rule: " + rule.getName());
                TerminalRenderer.renderInfo("Press Ctrl+C to terminate stream processing.");
            }

            // 5. Await termination or duration
            try {
                if (durationSeconds > 0) {
                    shutdownLatch.await(durationSeconds, TimeUnit.SECONDS);
                } else {
                    shutdownLatch.await();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                if (stopped.compareAndSet(false, true)) {
                    bridge.shutdown(Duration.ofSeconds(5));
                }
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // Shutdown already in progress
                }

                // 6. Render final stream metrics
                renderStreamStats(bridge.stats());
            }

            return 0;
        } catch (Exception e) {
            TerminalRenderer.renderError("Stream engine failed: " + e.getMessage());
            return 1;
        }
    }

    private StreamBridge createBridge() {
        if (disruptor) {
            DisruptorConfig config = DisruptorConfig.builder()
                    .ringBufferSize(ringBufferSize)
                    .yieldingWaitStrategy()
                    .build();
            return new DisruptorStreamBridge(config);
        } else {
            KafkaStreamConfig.Builder builder = KafkaStreamConfig.builder()
                    .bootstrapServers(bootstrapServers)
                    .groupId(groupId)
                    .inputTopics(Set.of(topic))
                    .maxInFlightPerPartition(maxInFlight)
                    .lowWatermarkPerPartition(lowWatermark)
                    .pollTimeout(Duration.ofMillis(pollTimeoutMs))
                    .commitMode(CommitMode.valueOf(commitMode.toUpperCase()));

            if (outputTopic != null && !outputTopic.isBlank()) {
                builder.outputTopic(outputTopic);
            }

            return new KafkaStreamEngine(builder.build());
        }
    }

    private void renderStreamStats(StreamStats stats) {
        if (stats == null) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Engine Type", disruptor ? "LMAX Disruptor" : "Apache Kafka");
        data.put("Topic", topic);
        data.put("Total Published", stats.getTotalPublished());
        data.put("Total Processed", stats.getTotalProcessed());
        data.put("Total Failed", stats.getTotalFailed());
        data.put("Total Dropped", stats.getTotalDropped());
        data.put("Ring / Buffer Capacity", stats.getRingBufferSize());
        data.put("Remaining Capacity", stats.getRemainingCapacity());

        if ("table".equalsIgnoreCase(outputFormat)) {
            System.out.println(AsciiTableRenderer.renderKeyValueTable("HELIX STREAMING METRICS SUMMARY", data));
        } else {
            OutputFormatter formatter = new OutputFormatter(outputFormat, quiet);
            String output = formatter.formatResult("Helix Streaming Metrics Summary", data);
            if (!output.isEmpty()) {
                System.out.println(output);
            }
        }
    }
}
