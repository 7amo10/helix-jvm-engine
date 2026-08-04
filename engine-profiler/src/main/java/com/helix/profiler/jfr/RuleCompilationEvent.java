package com.helix.profiler.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * JFR Event triggered upon rule compilation.
 */
@Name("com.helix.RuleCompilation")
@Label("Helix Rule Compilation")
@Category({"Helix", "Compilation"})
@Description("Tracks JSON parsing, AST building, and bytecode compilation")
public class RuleCompilationEvent extends Event {

    @Label("Rule Name")
    public String ruleName;

    @Label("Generator Type")
    public String generatorType;

    @Label("Compilation Time Nanos")
    public long compilationTimeNanos;

    @Label("Bytecode Size Bytes")
    public int bytecodeSizeBytes;

    @Label("Success")
    public boolean success;
}
