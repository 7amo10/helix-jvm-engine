package com.helix.agent.jmx;

public interface ProfilerControlMBean {
    boolean isProfilingEnabled();
    void setProfilingEnabled(boolean enabled);
    long getExecutionCount();
    void resetMetrics();
}
