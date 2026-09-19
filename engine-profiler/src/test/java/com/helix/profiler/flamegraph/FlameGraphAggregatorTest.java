package com.helix.profiler.flamegraph;

import com.helix.api.profiler.ExecutionEvent;
import com.helix.profiler.async.FlameGraphGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FlameGraphAggregatorTest {

    private FlameGraphAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new FlameGraphAggregator();
    }

    @AfterEach
    void tearDown() {
        aggregator.close();
    }

    @Test
    @DisplayName("Should build prefix trie and calculate hit counts, depth, and cumulative percentages")
    void testPrefixTrieConstruction() {
        // Sample 1: main -> service -> execute (count = 10)
        aggregator.addSample(List.of("com.helix.Main.main", "com.helix.Service.run", "com.helix.Engine.execute"), 10);
        // Sample 2: main -> service -> validate (count = 5)
        aggregator.addSample(List.of("com.helix.Main.main", "com.helix.Service.run", "com.helix.Validator.validate"), 5);
        // Sample 3: main -> init (count = 5)
        aggregator.addSample(List.of("com.helix.Main.main", "com.helix.Init.setup"), 5);

        StackFrameNode root = aggregator.getRootNode();
        assertNotNull(root);
        assertEquals(20, root.getTotalValue(), "Root should aggregate all 20 samples");
        assertEquals(0, root.getSelfValue());

        StackFrameNode mainNode = root.getChild("com.helix.Main.main");
        assertNotNull(mainNode);
        assertEquals(20, mainNode.getTotalValue());
        assertEquals(0, mainNode.getSelfValue());
        assertEquals(1, mainNode.getDepth());
        assertEquals(100.0, mainNode.getCumulativePercentage(), 0.001);

        StackFrameNode serviceNode = mainNode.getChild("com.helix.Service.run");
        assertNotNull(serviceNode);
        assertEquals(15, serviceNode.getTotalValue());
        assertEquals(2, serviceNode.getDepth());
        assertEquals(75.0, serviceNode.getCumulativePercentage(), 0.001);

        StackFrameNode executeNode = serviceNode.getChild("com.helix.Engine.execute");
        assertNotNull(executeNode);
        assertEquals(10, executeNode.getTotalValue());
        assertEquals(10, executeNode.getSelfValue());
        assertEquals(3, executeNode.getDepth());
        assertEquals(50.0, executeNode.getCumulativePercentage(), 0.001);
        assertEquals(50.0, executeNode.getSelfPercentage(), 0.001);
        assertTrue(executeNode.isLeaf());

        StackFrameNode validateNode = serviceNode.getChild("com.helix.Validator.validate");
        assertNotNull(validateNode);
        assertEquals(5, validateNode.getTotalValue());
        assertEquals(5, validateNode.getSelfValue());
        assertEquals(25.0, validateNode.getCumulativePercentage(), 0.001);

        assertEquals(3, aggregator.getDistinctStacksCount());
        assertEquals(3, aggregator.getMaxDepth());
        assertFalse(root.toTreeString().isEmpty());
    }

    @Test
    @DisplayName("Should export folded stack format matching Brendan Gregg FlameGraph specification")
    void testExportFoldedFormat(@TempDir Path tempDir) throws Exception {
        aggregator.addSample(List.of("Thread.run", "Engine.eval", "Compiler.compile"), 42);
        aggregator.addSample(List.of("Thread.run", "Engine.eval", "Parser.parse"), 18);

        String foldedOutput = aggregator.exportFolded();
        assertNotNull(foldedOutput);

        // Brendan Gregg format: frame1;frame2;frame3 <count>
        assertTrue(foldedOutput.contains("Thread.run;Engine.eval;Compiler.compile 42"));
        assertTrue(foldedOutput.contains("Thread.run;Engine.eval;Parser.parse 18"));

        // Verify interoperability with Helix FlameGraphGenerator
        FlameGraphGenerator htmlGen = new FlameGraphGenerator();
        String html = htmlGen.generateHtmlFlameGraph(foldedOutput, "Test Flame Graph");
        assertNotNull(html);
        assertTrue(html.contains("Thread.run;Engine.eval;Compiler.compile"));
        assertTrue(html.contains("42"));

        // Test export to file
        Path exportedFile = tempDir.resolve("profile.folded");
        aggregator.exportFolded(exportedFile);
        assertTrue(Files.exists(exportedFile));
        String fileContent = Files.readString(exportedFile);
        assertEquals(foldedOutput, fileContent);

        // Test export to Writer
        StringWriter writer = new StringWriter();
        aggregator.exportFolded(MetricType.CPU_TIME, writer);
        assertEquals(foldedOutput, writer.toString());
    }

    @Test
    @DisplayName("Should support dynamic switching between CPU sampling and Heap allocation sampling")
    void testDynamicMetricSwitching() {
        assertEquals(MetricType.CPU_TIME, aggregator.getMetric());

        // Ingest CPU samples
        aggregator.addSample(MetricType.CPU_TIME, List.of("Thread.run", "Worker.compute"), 100);

        // Ingest Allocation samples (bytes)
        aggregator.addSample(MetricType.ALLOCATION_BYTES, List.of("Thread.run", "Buffer.allocate"), 1048576);

        // CPU Mode active
        assertEquals(100, aggregator.getTotalSamples());
        String cpuFolded = aggregator.exportFolded();
        assertTrue(cpuFolded.contains("Thread.run;Worker.compute 100"));
        assertFalse(cpuFolded.contains("Buffer.allocate"));

        // Switch dynamically to Allocation Mode
        aggregator.setMetric(MetricType.ALLOCATION_BYTES);
        assertEquals(MetricType.ALLOCATION_BYTES, aggregator.getMetric());
        assertEquals(1048576, aggregator.getTotalSamples());

        String allocFolded = aggregator.exportFolded();
        assertTrue(allocFolded.contains("Thread.run;Buffer.allocate 1048576"));
        assertFalse(allocFolded.contains("Worker.compute"));

        // Switch back to CPU
        aggregator.setMetric(MetricType.CPU_TIME);
        assertEquals(100, aggregator.getTotalSamples());
    }

    @Test
    @DisplayName("Should ingest and parse Brendan Gregg folded stack lines and text")
    void testIngestFoldedText(@TempDir Path tempDir) throws Exception {
        String input = """
                com.helix.App.main;com.helix.Core.run;com.helix.Parser.parse 25
                com.helix.App.main;com.helix.Core.run;com.helix.Compiler.compile 75
                # Comment line to ignore
                """;

        aggregator.ingestFoldedText(input);
        assertEquals(100, aggregator.getTotalSamples());
        assertEquals(2, aggregator.getDistinctStacksCount());

        StackFrameNode mainNode = aggregator.getRootNode().getChild("com.helix.App.main");
        assertNotNull(mainNode);
        assertEquals(100, mainNode.getTotalValue());

        // Ingest from folded file
        Path foldedPath = tempDir.resolve("input.folded");
        Files.writeString(foldedPath, "com.helix.Service.doWork 50\n");
        aggregator.ingestFoldedFile(foldedPath);

        assertEquals(150, aggregator.getTotalSamples());
        assertEquals(3, aggregator.getDistinctStacksCount());
    }

    @Test
    @DisplayName("Should ingest StackTraceElement arrays and invert leaf-first stacks to root-first call tree")
    void testStackTraceElementIngestion() {
        // Emulate Thread.currentThread().getStackTrace() where index 0 is innermost method
        StackTraceElement[] stack = new StackTraceElement[]{
                new StackTraceElement("com.helix.Leaf", "inner", "Leaf.java", 10),
                new StackTraceElement("com.helix.Middle", "step", "Middle.java", 20),
                new StackTraceElement("com.helix.Root", "start", "Root.java", 30)
        };

        aggregator.addSample(stack, 1);

        String folded = aggregator.exportFolded();
        // Inverted to root-first: Root -> Middle -> Leaf
        assertEquals("com.helix.Root.start;com.helix.Middle.step;com.helix.Leaf.inner 1\n", folded);
    }

    @Test
    @DisplayName("Should ingest live thread dumps and profile event listener notifications")
    void testThreadDumpAndProfileEventListener() {
        // Live thread dump ingestion
        aggregator.sampleAllThreads();
        assertTrue(aggregator.getTotalSamples() > 0, "Should have sampled at least one live thread");

        // ProfileEventListener integration (DefaultProfiler / Engine events)
        aggregator.onEvent(new StackSampleEvent(
                MetricType.CPU_TIME,
                List.of("com.helix.RuleEngine.eval", "com.helix.Rule.execute"),
                30L
        ));
        aggregator.onEvent(new ExecutionEvent(
                "DiscountRule",
                "1.0.0",
                500_000L,
                true,
                Instant.now()
        ));

        assertTrue(aggregator.exportFolded().contains("com.helix.RuleEngine.eval;com.helix.Rule.execute 30"));
        assertTrue(aggregator.exportFolded().contains("com.helix.engine.RuleExecution;DiscountRule 500000"));
    }

    @Test
    @DisplayName("Acceptance Criteria: Low CPU overhead during high-concurrency ingestion")
    void testHighConcurrencyThroughputAndLowOverhead() throws Exception {
        int threadCount = 8;
        int samplesPerThread = 20_000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    List<String> stackA = List.of("Thread-" + threadId, "Controller.handle", "Service.process", "Dao.query");
                    List<String> stackB = List.of("Thread-" + threadId, "Controller.handle", "Service.process", "Cache.get");

                    for (int i = 0; i < samplesPerThread / 2; i++) {
                        aggregator.addSample(stackA, 1L);
                        aggregator.addSample(stackB, 1L);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        long startTime = System.nanoTime();
        startLatch.countDown();
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "Concurrent ingestion should complete quickly");
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        executor.shutdownNow();

        long totalSamples = aggregator.getTotalSamples();
        assertEquals(threadCount * samplesPerThread, totalSamples);
        System.out.println("Ingested " + totalSamples + " samples across " + threadCount + " threads in " + durationMs + " ms");
        assertTrue(durationMs < 5000, "160,000 samples should be aggregated in under 5 seconds (was " + durationMs + " ms)");
    }

    @Test
    @DisplayName("Should ingest JDK Flight Recorder (.jfr) files and extract call stack samples")
    void testJfrRecordingIngestion(@TempDir Path tempDir) throws Exception {
        Path jfrFile = tempDir.resolve("test-flight.jfr");

        jdk.jfr.Recording recording = new jdk.jfr.Recording();
        recording.enable("jdk.ExecutionSample");
        recording.start();

        // Perform some busy work
        long dummy = 0;
        for (int i = 0; i < 50_000; i++) {
            dummy += Math.sqrt(i);
        }
        assertTrue(dummy > 0);

        recording.stop();
        recording.dump(jfrFile);
        recording.close();

        assertTrue(Files.exists(jfrFile));
        assertTrue(Files.size(jfrFile) > 0);

        // Ingest the real JFR file
        assertDoesNotThrow(() -> aggregator.ingestJfr(jfrFile));

        assertNotNull(aggregator.getRootNode(MetricType.CPU_TIME));
        assertNotNull(aggregator.getRootNode(MetricType.ALLOCATION_BYTES));
    }
}
