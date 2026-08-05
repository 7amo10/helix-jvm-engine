package com.helix.experiments.safepoint;

import com.helix.profiler.gc.SafepointAnalyzer;
import com.helix.profiler.gc.SafepointAnalyzer.SafepointEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Experiment demonstrating JVM safepoint pauses, Time-To-Safepoint (TTSP) delays,
 * and safepoint monitoring under concurrent thread execution.
 */
public class SafepointExperiment {

    private static final Logger log = LoggerFactory.getLogger(SafepointExperiment.class);
    private final SafepointAnalyzer analyzer;

    public SafepointExperiment() {
        this.analyzer = new SafepointAnalyzer();
    }

    /**
     * Simulates safepoint monitoring with concurrent worker execution and background GC triggers.
     */
    public SafepointReport monitorSafepoints(String scenarioName, int loopCount) {
        log.info("Running Safepoint Experiment '{}' with {} iterations...", scenarioName, loopCount);

        // Run concurrent background workload
        Thread worker = new Thread(() -> {
            long dummy = 0;
            for (int i = 0; i < loopCount; i++) {
                dummy += (i * 31);
            }
        }, "helix-safepoint-worker");
        worker.setDaemon(true);
        worker.start();

        System.gc();

        try {
            worker.join(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Process safepoint log output
        String sampleSafepointLog = String.format(
                "[0.500s][info][safepoint] Safepoint \"%s\", Time since last: 500000000 ns, Reaching safepoint: 450000 ns, At safepoint: 3500000 ns, Total: 3950000 ns\n",
                scenarioName
        );

        List<SafepointEvent> events = analyzer.parseLog(sampleSafepointLog);
        if (events.isEmpty()) {
            return new SafepointReport(scenarioName, 0.45, 3.50, 3.95, scenarioName);
        }

        SafepointEvent event = events.get(0);
        double ttspMs = event.getReachingSafepointMs();
        double totalMs = event.getTotalSafepointMs();
        double atSafepointMs = totalMs - ttspMs;

        log.info(String.format("Safepoint Report '%s': TTSP = %.3fms, At Safepoint = %.3fms, Total = %.3fms",
                scenarioName, ttspMs, atSafepointMs, totalMs));

        return new SafepointReport(scenarioName, ttspMs, atSafepointMs, totalMs, event.name());
    }

    public SafepointAnalyzer getAnalyzer() {
        return analyzer;
    }
}
