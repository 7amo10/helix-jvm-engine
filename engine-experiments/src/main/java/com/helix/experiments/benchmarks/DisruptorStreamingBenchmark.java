package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.StreamResult;
import com.helix.core.RuleCompiler;
import com.helix.core.stream.disruptor.DisruptorConfig;
import com.helix.core.stream.disruptor.DisruptorStreamBridge;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite measuring sustained ring buffer streaming throughput
 * (&gt;1,500,000 events/sec) and latency percentiles (P50, P99) with LMAX Disruptor.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@Fork(0)
public class DisruptorStreamingBenchmark {

    public static final String TOPIC = "benchmark-events";
    public static final String RULE_NAME = "StreamBenchmarkRule";

    private DisruptorStreamBridge bridge;
    private ExecutionContext sampleContext;
    private RuleEvent sampleEvent;

    @Setup
    public void setup() throws Exception {
        RuleCompiler compiler = new RuleCompiler(RuleCompiler.GeneratorType.ASM);
        String ruleJson = """
                {
                    "name": "StreamBenchmarkRule",
                    "expression": "amount > 100.0 && status == 'ACTIVE'",
                    "inputSchema": {
                        "amount": "double",
                        "status": "string"
                    }
                }
                """;

        CompiledRule compiledRule = compiler.compile(ruleJson);

        DisruptorConfig config = DisruptorConfig.builder()
                .ringBufferSize(65536)
                .singleProducer()
                .yieldingWaitStrategy()
                .threadFactory(r -> new Thread(r, "helix-disruptor-benchmark-consumer"))
                .build();

        this.bridge = new DisruptorStreamBridge(config);
        this.bridge.registerRule(TOPIC, compiledRule);
        this.bridge.registerRule(RULE_NAME, compiledRule);

        this.sampleContext = new ExecutionContext(Map.of("amount", 250.0, "status", "ACTIVE"));
        this.sampleEvent = RuleEvent.of(TOPIC, sampleContext);

        // Pre-warm ring buffer and consumer thread
        for (int i = 0; i < 50_000; i++) {
            bridge.publish(TOPIC, RULE_NAME, sampleContext, null);
        }
        Thread.sleep(100);
    }

    @TearDown
    public void tearDown() {
        if (bridge != null) {
            bridge.shutdown(Duration.ofSeconds(3));
        }
    }

    /**
     * Measures sustained zero-allocation streaming throughput (events/second).
     * Target: &gt;1,500,000 events/sec.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkSustainedThroughput() {
        bridge.publish(TOPIC, RULE_NAME, sampleContext, null);
    }

    /**
     * Measures ring buffer publication latency percentiles (P50, P99).
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void benchmarkRingBufferLatency() {
        bridge.publish(TOPIC, RULE_NAME, sampleContext, null);
    }

    /**
     * Measures synchronous end-to-end event completion latency percentiles (P50, P99).
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public StreamResult benchmarkRoundTripLatency() {
        return bridge.publish(sampleEvent).join();
    }

    public DisruptorStreamBridge getBridge() {
        return bridge;
    }
}
