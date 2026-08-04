package com.helix.agent.jmx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.*;

class JmxMBeanTest {

    private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

    @BeforeEach
    @AfterEach
    void cleanup() {
        MBeanRegistry.unregisterMBean("EngineControl");
        MBeanRegistry.unregisterMBean("ProfilerControl");
    }

    @Test
    @DisplayName("Should register and unregister EngineControl MBean cleanly")
    void testEngineControlRegistration() throws Exception {
        EngineControl engineControl = new EngineControl();
        MBeanRegistry.registerMBean(engineControl, "EngineControl");

        ObjectName name = new ObjectName("com.helix.agent:type=EngineControl");
        assertTrue(mBeanServer.isRegistered(name));
        assertTrue(engineControl.getActiveLoadersCount() > 0);

        engineControl.triggerGC();
        engineControl.clearCache();

        MBeanRegistry.unregisterMBean("EngineControl");
        assertFalse(mBeanServer.isRegistered(name));
    }

    @Test
    @DisplayName("Should register ProfilerControl and manage profiling state")
    void testProfilerControl() throws Exception {
        ProfilerControl profilerControl = new ProfilerControl();
        MBeanRegistry.registerMBean(profilerControl, "ProfilerControl");

        ObjectName name = new ObjectName("com.helix.agent:type=ProfilerControl");
        assertTrue(mBeanServer.isRegistered(name));

        assertTrue(profilerControl.isProfilingEnabled());
        profilerControl.setProfilingEnabled(false);
        assertFalse(profilerControl.isProfilingEnabled());

        profilerControl.resetMetrics();
        assertEquals(0, profilerControl.getExecutionCount());

        MBeanRegistry.unregisterMBean("ProfilerControl");
        assertFalse(mBeanServer.isRegistered(name));
    }
}
