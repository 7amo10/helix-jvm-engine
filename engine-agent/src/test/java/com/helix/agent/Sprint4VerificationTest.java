package com.helix.agent;

import com.helix.agent.jmx.EngineControl;
import com.helix.agent.jmx.MBeanRegistry;
import com.helix.agent.jol.MemoryAnalysisReport;
import com.helix.agent.jol.MemoryAnalyzer;
import com.helix.agent.transformer.AllocationTracker;
import com.helix.agent.transformer.RuleClassTransformer;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sprint 4 Verification Suite")
class Sprint4VerificationTest {

    public static class SampleRule {
        public void execute() {
            String temp = new String("Sprint4Verification");
        }
    }

    @Test
    @DisplayName("Verify AgentMain initialization and configuration")
    void testAgentInitialization() {
        Instrumentation inst = ByteBuddyAgent.install();
        AgentMain.agentmain("retransform=true,packages=com.helix", inst);

        assertNotNull(AgentMain.getInstrumentation());
        assertNotNull(AgentMain.getConfiguration());
        assertTrue(AgentMain.getConfiguration().isEnableRetransformation());
    }

    @Test
    @DisplayName("Verify RuleClassTransformer and AllocationTracker")
    void testTransformerAndAllocationTracker() throws Exception {
        AllocationTracker tracker = AllocationTracker.getInstance();
        tracker.reset();

        String internalName = "com/helix/agent/Sprint4VerificationTest$SampleRule";
        byte[] originalBytes = SampleRule.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")
                .readAllBytes();

        RuleClassTransformer transformer = new RuleClassTransformer("com/helix/agent");
        byte[] transformed = transformer.transform(
                SampleRule.class.getClassLoader(),
                internalName,
                null,
                null,
                originalBytes
        );

        assertNotNull(transformed);
        assertTrue(transformed.length > 0);

        tracker.recordAllocation("java/lang/String");
        assertEquals(1, tracker.getTotalAllocations());
    }

    @Test
    @DisplayName("Verify MemoryAnalyzer layout inspection")
    void testMemoryAnalyzer() {
        MemoryAnalyzer analyzer = new MemoryAnalyzer();
        SampleRule sample = new SampleRule();
        MemoryAnalysisReport report = analyzer.analyze(sample);

        assertNotNull(report);
        assertEquals(SampleRule.class.getName(), report.className());
        assertTrue(report.instanceSizeShallow() > 0);
    }

    @Test
    @DisplayName("Verify JMX EngineControl registration and operations")
    void testJmxEngineControl() throws Exception {
        MBeanRegistry.registerMBean(new EngineControl(), "EngineControl");
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("com.helix.agent:type=EngineControl");

        assertTrue(server.isRegistered(name));
        Object activeLoaders = server.getAttribute(name, "ActiveLoadersCount");
        assertNotNull(activeLoaders);

        MBeanRegistry.unregisterMBean("EngineControl");
        assertFalse(server.isRegistered(name));
    }
}
