package com.helix.agent.jol;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.util.Objects;

/**
 * Inspector utilizing JOL ClassLayout and GraphLayout for shallow/deep size calculations and layout visualization.
 */
public class ObjectLayoutInspector {

    public MemoryAnalysisReport inspect(Object instance) {
        Objects.requireNonNull(instance, "instance cannot be null");
        Class<?> clazz = instance.getClass();
        ClassLayout classLayout = ClassLayout.parseInstance(instance);

        long shallowSize = classLayout.instanceSize();
        long deepSize = GraphLayout.parseInstance(instance).totalSize();
        long headerSize = classLayout.headerSize();
        long padding = Math.max(0, shallowSize - headerSize);
        boolean compressedOops = CompressedOopsDetector.isCompressedOopsEnabled();
        String printable = classLayout.toPrintable();

        return new MemoryAnalysisReport(
                clazz.getName(),
                shallowSize,
                deepSize,
                headerSize,
                padding,
                compressedOops,
                printable
        );
    }

    public MemoryAnalysisReport inspectClass(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz cannot be null");
        ClassLayout classLayout = ClassLayout.parseClass(clazz);

        long shallowSize = classLayout.instanceSize();
        long headerSize = classLayout.headerSize();
        long padding = Math.max(0, shallowSize - headerSize);
        boolean compressedOops = CompressedOopsDetector.isCompressedOopsEnabled();
        String printable = classLayout.toPrintable();

        return new MemoryAnalysisReport(
                clazz.getName(),
                shallowSize,
                shallowSize,
                headerSize,
                padding,
                compressedOops,
                printable
        );
    }
}
