package com.helix.experiments.metaspace;

/**
 * Summary report of Metaspace memory usage and class loading metrics.
 */
public record MetaspaceReport(
        String scenarioName,
        int classesGenerated,
        double initialMetaspaceUsedMb,
        double finalMetaspaceUsedMb,
        double metaspaceGrowthMb,
        boolean gcTriggered,
        int activeClassLoaders
) {

    @Override
    public String toString() {
        return String.format(
                "MetaspaceReport[%s] - Generated: %d classes | Initial: %.2f MB | Final: %.2f MB | Growth: +%.2f MB | ClassLoaders: %d | GC Triggered: %s",
                scenarioName, classesGenerated, initialMetaspaceUsedMb, finalMetaspaceUsedMb, metaspaceGrowthMb, activeClassLoaders, gcTriggered
        );
    }
}
