package com.helix.agent.jol;

public record MemoryAnalysisReport(
        String className,
        long instanceSizeShallow,
        long instanceSizeDeep,
        long headerSize,
        long lossPaddingBytes,
        boolean compressedOops,
        String printableLayout
) {
}
