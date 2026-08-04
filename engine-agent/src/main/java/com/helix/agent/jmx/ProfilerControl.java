package com.helix.agent.jmx;

import com.helix.agent.AgentCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class ProfilerControl implements ProfilerControlMBean {

    private static final Logger log = LoggerFactory.getLogger(ProfilerControl.class);
    private final AtomicBoolean profilingEnabled = new AtomicBoolean(true);

    @Override
    public boolean isProfilingEnabled() {
        return profilingEnabled.get();
    }

    @Override
    public void setProfilingEnabled(boolean enabled) {
        log.info("[JMX ProfilerControl] Profiling enabled set to {}", enabled);
        profilingEnabled.set(enabled);
    }

    @Override
    public long getExecutionCount() {
        return AgentCallback.getThreadStats().getTotalExecutions();
    }

    @Override
    public void resetMetrics() {
        log.info("[JMX ProfilerControl] Resetting thread metrics via MBean");
        AgentCallback.clearThreadStats();
    }
}
