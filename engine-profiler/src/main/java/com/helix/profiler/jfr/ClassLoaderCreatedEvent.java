package com.helix.profiler.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * JFR Event triggered upon ClassLoader creation.
 */
@Name("com.helix.ClassLoaderCreated")
@Label("Helix ClassLoader Created")
@Category({"Helix", "ClassLoading"})
@Description("Tracks custom RuleClassLoader instantiation and lifecycle")
public class ClassLoaderCreatedEvent extends Event {

    @Label("ClassLoader ID")
    public String loaderId;

    @Label("Isolation Mode")
    public String isolationMode;

    @Label("Parent Loader Name")
    public String parentLoaderName;
}
