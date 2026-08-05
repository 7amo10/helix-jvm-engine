package com.helix.experiments.metaspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Experiment demonstrating Metaspace memory leaks caused by unclosed, retained ClassLoaders vs.
 * proper ClassLoader closing and GC class unloading.
 */
public class MetaspaceLeakExperiment {

    private static final Logger log = LoggerFactory.getLogger(MetaspaceLeakExperiment.class);
    private final DynamicClassGenerator generator;

    public MetaspaceLeakExperiment() {
        this.generator = new DynamicClassGenerator();
    }

    /**
     * Runs the leak scenario where ClassLoader and Class references are retained indefinitely.
     */
    public MetaspaceReport runLeakScenario(int iterations) {
        log.info("Starting Metaspace Leak Scenario with {} iterations...", iterations);
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        double initialMb = getMetaspaceUsedMb(memoryBean);
        List<DynamicClassGenerator.GeneratedClassResult> retainedReferences = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            retainedReferences.add(generator.generateDynamicClass());
        }

        double finalMb = getMetaspaceUsedMb(memoryBean);
        double growthMb = finalMb - initialMb;

        log.info(String.format("Leak Scenario finished. Initial: %.2fMB, Final: %.2fMB, Growth: +%.2fMB", initialMb, finalMb, growthMb));

        return new MetaspaceReport(
                "LEAK",
                iterations,
                initialMb,
                finalMb,
                growthMb,
                false,
                retainedReferences.size()
        );
    }

    /**
     * Runs the fixed scenario where ClassLoaders are closed, unreferenced, and GC is invoked.
     */
    public MetaspaceReport runFixedScenario(int iterations) {
        log.info("Starting Metaspace Fixed Scenario with {} iterations...", iterations);
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        double initialMb = getMetaspaceUsedMb(memoryBean);

        for (int i = 0; i < iterations; i++) {
            DynamicClassGenerator.GeneratedClassResult result = generator.generateDynamicClass();
            try {
                result.classLoader().close();
            } catch (IOException e) {
                log.debug("Error closing classloader: {}", e.getMessage());
            }
        }

        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        double finalMb = getMetaspaceUsedMb(memoryBean);
        double growthMb = finalMb - initialMb;

        log.info(String.format("Fixed Scenario finished. Initial: %.2fMB, Final: %.2fMB, Growth: +%.2fMB", initialMb, finalMb, growthMb));

        return new MetaspaceReport(
                "FIXED",
                iterations,
                initialMb,
                finalMb,
                growthMb,
                true,
                0
        );
    }

    private static double getMetaspaceUsedMb(MemoryMXBean memoryBean) {
        long usedBytes = memoryBean.getNonHeapMemoryUsage().getUsed();
        return usedBytes / (1024.0 * 1024.0);
    }
}
