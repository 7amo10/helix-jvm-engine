package com.helix.agent.jmx;

public interface EngineControlMBean {
    long getUptimeMillis();
    int getActiveLoadersCount();
    long getTotalAllocations();
    void clearCache();
    void triggerGC();
}
