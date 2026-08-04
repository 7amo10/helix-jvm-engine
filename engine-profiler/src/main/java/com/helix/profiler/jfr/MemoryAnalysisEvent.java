package com.helix.profiler.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * JFR Event triggered upon object layout memory analysis.
 */
@Name("com.helix.MemoryAnalysis")
@Label("Helix Object Memory Analysis")
@Category({"Helix", "Memory"})
@Description("Tracks JOL memory footprint analysis results")
public class MemoryAnalysisEvent extends Event {

    @Label("Target Class Name")
    public String className;

    @Label("Shallow Size Bytes")
    public long shallowSizeBytes;

    @Label("Deep Size Bytes")
    public long deepSizeBytes;

    @Label("Compressed OOPs Enabled")
    public boolean compressedOopsEnabled;
}
