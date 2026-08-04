package com.helix.agent.jmx;

import com.helix.agent.transformer.AllocationTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;

public class EngineControl implements EngineControlMBean {

    private static final Logger log = LoggerFactory.getLogger(EngineControl.class);
    private final long startTimeNanos = System.currentTimeMillis();

    @Override
    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTimeNanos;
    }

    @Override
    public int getActiveLoadersCount() {
        return ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
    }

    @Override
    public long getTotalAllocations() {
        return AllocationTracker.getInstance().getTotalAllocations();
    }

    @Override
    public void clearCache() {
        log.info("[JMX EngineControl] Cache clear requested via MBean");
    }

    @Override
    public void triggerGC() {
        log.info("[JMX EngineControl] System.gc() triggered via MBean");
        System.gc();
    }
}
