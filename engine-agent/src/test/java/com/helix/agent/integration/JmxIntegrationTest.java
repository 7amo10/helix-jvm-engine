package com.helix.agent.integration;

import com.helix.agent.jmx.EngineControl;
import com.helix.agent.jmx.MBeanRegistry;
import com.helix.agent.jmx.ProfilerControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.*;

class JmxIntegrationTest {

    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

    @BeforeEach
    void setUp() {
        MBeanRegistry.registerMBean(new EngineControl(), "EngineControl");
        MBeanRegistry.registerMBean(new ProfilerControl(), "ProfilerControl");
    }

    @AfterEach
    void tearDown() {
        MBeanRegistry.unregisterMBean("EngineControl");
        MBeanRegistry.unregisterMBean("ProfilerControl");
    }

    @Test
    @DisplayName("Should query EngineControl and ProfilerControl MBeans via MBeanServer platform connection")
    void testMBeansQuerying() throws Exception {
        ObjectName engineName = new ObjectName("com.helix.agent:type=EngineControl");
        ObjectName profilerName = new ObjectName("com.helix.agent:type=ProfilerControl");

        assertTrue(server.isRegistered(engineName));
        assertTrue(server.isRegistered(profilerName));

        Object uptime = server.getAttribute(engineName, "UptimeMillis");
        assertNotNull(uptime);
        assertTrue((Long) uptime >= 0);

        Object profilingEnabled = server.getAttribute(profilerName, "ProfilingEnabled");
        assertEquals(Boolean.TRUE, profilingEnabled);

        server.invoke(engineName, "triggerGC", new Object[0], new String[0]);
        server.invoke(profilerName, "resetMetrics", new Object[0], new String[0]);
    }
}
