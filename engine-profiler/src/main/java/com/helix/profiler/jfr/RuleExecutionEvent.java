package com.helix.profiler.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * JFR Event triggered upon rule execution.
 */
@Name("com.helix.RuleExecution")
@Label("Helix Rule Execution")
@Category({"Helix", "Execution"})
@Description("Tracks synchronous or asynchronous execution of a compiled rule")
public class RuleExecutionEvent extends Event {

    @Label("Rule Name")
    public String ruleName;

    @Label("Rule Version")
    public String ruleVersion;

    @Label("Success")
    public boolean success;

    @Label("Execution Duration Nanos")
    public long durationNanos;

    @Label("Error Message")
    public String errorMessage;
}
