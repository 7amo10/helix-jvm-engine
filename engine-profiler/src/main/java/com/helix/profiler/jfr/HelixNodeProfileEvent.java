package com.helix.profiler.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * Custom JDK Flight Recorder (JFR) event emitting per-node profiling telemetry.
 *
 * <p>Captures live execution counts, average costs, failure/short-circuit rates,
 * and cost-to-failure ratios for AST nodes in compiled Helix rules.</p>
 */
@Name("com.helix.NodeProfile")
@Label("Helix Node Profile")
@Category({"Helix", "Profiler"})
@Description("Telemetry event tracking AST node execution cost, failure rate, and cost-to-failure ratio")
public class HelixNodeProfileEvent extends Event {

    @Label("Rule Name")
    public String ruleName;

    @Label("Node ID")
    public String nodeId;

    @Label("Execution Count")
    public long executionCount;

    @Label("Average Cost Nanos")
    public double avgCostNanos;

    @Label("Failure Rate")
    public double failureRate;

    @Label("Cost to Failure Ratio")
    public double ratio;
}
