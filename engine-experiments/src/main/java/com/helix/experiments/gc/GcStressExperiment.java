package com.helix.experiments.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Experiment evaluating GC behavior with SoftReferences under memory pressure
 * vs immediate GC collection of WeakReferences.
 */
public class GcStressExperiment {

    private static final Logger log = LoggerFactory.getLogger(GcStressExperiment.class);

    /**
     * Tests WeakReference lifecycle: Weak references are reclaimed immediately during any GC pass.
     */
    public GcReport weakReferenceImmediate(int objectCount) {
        log.info("Starting WeakReference Immediate GC Scenario with {} objects...", objectCount);

        List<WeakReference<byte[]>> weakList = new ArrayList<>(objectCount);
        for (int i = 0; i < objectCount; i++) {
            weakList.add(new WeakReference<>(new byte[64 * 1024])); // 64 KB per object
        }

        double totalAllocatedMb = (objectCount * 64.0 * 1024.0) / (1024.0 * 1024.0);

        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int cleared = 0;
        int retained = 0;
        for (WeakReference<byte[]> ref : weakList) {
            if (ref.get() == null) {
                cleared++;
            } else {
                retained++;
            }
        }

        log.info("WeakReference Scenario: Initial = {}, Cleared = {}, Retained = {}", objectCount, cleared, retained);
        return new GcReport("WEAK_REFERENCE", objectCount, cleared, retained, totalAllocatedMb);
    }

    /**
     * Tests SoftReference behavior under memory pressure: Soft references are retained until heap pressure demands reclamation.
     */
    public GcReport softReferenceUnderPressure(int objectCount, boolean simulateHighPressure) {
        log.info("Starting SoftReference Scenario (High Pressure: {}) with {} objects...", simulateHighPressure, objectCount);

        List<SoftReference<byte[]>> softList = new ArrayList<>(objectCount);
        for (int i = 0; i < objectCount; i++) {
            softList.add(new SoftReference<>(new byte[128 * 1024])); // 128 KB per object
        }

        double totalAllocatedMb = (objectCount * 128.0 * 1024.0) / (1024.0 * 1024.0);

        if (simulateHighPressure) {
            try {
                List<byte[]> memoryBurner = new ArrayList<>();
                for (int i = 0; i < 50; i++) {
                    memoryBurner.add(new byte[5 * 1024 * 1024]); // 5MB chunks to trigger soft ref eviction
                }
            } catch (OutOfMemoryError oom) {
                log.debug("OOM simulated for soft reference eviction test.");
            }
        }

        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int cleared = 0;
        int retained = 0;
        for (SoftReference<byte[]> ref : softList) {
            if (ref.get() == null) {
                cleared++;
            } else {
                retained++;
            }
        }

        log.info("SoftReference Scenario: Initial = {}, Cleared = {}, Retained = {}", objectCount, cleared, retained);
        return new GcReport("SOFT_REFERENCE", objectCount, cleared, retained, totalAllocatedMb);
    }
}
