package com.helix.experiments.layout;

/**
 * Report summarizing JOL object memory layout, header sizes, padding, and compressed oops settings.
 */
public record LayoutReport(
        String className,
        long headerSizeBytes,
        long instanceSizeBytes,
        long paddingBytes,
        boolean compressedOopsEnabled,
        String printableLayout
) {

    @Override
    public String toString() {
        return String.format(
                "LayoutReport[%s] - Instance Size: %d B | Header: %d B | Padding: %d B | Compressed OOPs: %s",
                className, instanceSizeBytes, headerSizeBytes, paddingBytes, compressedOopsEnabled
        );
    }
}
