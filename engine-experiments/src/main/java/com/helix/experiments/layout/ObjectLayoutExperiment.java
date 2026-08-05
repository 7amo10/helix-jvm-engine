package com.helix.experiments.layout;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.vm.VM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Experiment analyzing JVM object layouts, header overhead, alignment padding, and Compressed OOPs footprint using JOL.
 */
public class ObjectLayoutExperiment {

    private static final Logger log = LoggerFactory.getLogger(ObjectLayoutExperiment.class);

    /**
     * Analyzes and returns detailed memory layout information for a given target object.
     */
    public LayoutReport analyzeLayout(Object target) {
        Objects.requireNonNull(target, "target object must not be null");

        ClassLayout layout = ClassLayout.parseInstance(target);
        long instanceSize = layout.instanceSize();
        long headerSize = layout.headerSize();
        long padding = Math.max(0, instanceSize - headerSize);
        boolean compressedOops = false;
        try {
            compressedOops = VM.current().details().toLowerCase().contains("compressed oops");
        } catch (Throwable ignored) {
        }
        String printable = layout.toPrintable();

        log.info("Analyzed object layout for '{}': Instance Size = {}B, Header = {}B, Padding = {}B, Compressed OOPs = {}",
                target.getClass().getName(), instanceSize, headerSize, padding, compressedOops);

        return new LayoutReport(
                target.getClass().getName(),
                headerSize,
                instanceSize,
                padding,
                compressedOops,
                printable
        );
    }

    /**
     * Compares object layout footprint with compressed oops status.
     */
    public LayoutReport compareCompressedOops(Object target) {
        LayoutReport report = analyzeLayout(target);
        log.info("Compressed OOPs Comparison:\n{}", report.printableLayout());
        return report;
    }
}
