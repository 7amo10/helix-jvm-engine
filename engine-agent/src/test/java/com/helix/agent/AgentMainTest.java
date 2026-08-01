package com.helix.agent;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;

import static org.junit.jupiter.api.Assertions.*;

class AgentMainTest {

    @Test
    @DisplayName("Should parse default agent configuration correctly")
    void testDefaultConfiguration() {
        AgentConfiguration config = AgentConfiguration.parse(null);
        assertTrue(config.isEnableRetransformation());
        assertEquals("com.helix", config.getTargetPackages());
        assertEquals("INFO", config.getLogLevel());
    }

    @Test
    @DisplayName("Should parse custom agent configuration string")
    void testCustomConfiguration() {
        String args = "retransform=false,packages=com.helix.rules,logLevel=DEBUG";
        AgentConfiguration config = AgentConfiguration.parse(args);

        assertFalse(config.isEnableRetransformation());
        assertEquals("com.helix.rules", config.getTargetPackages());
        assertEquals("DEBUG", config.getLogLevel());
    }

    @Test
    @DisplayName("Should initialize agent via premain entry point with ByteBuddy agent")
    void testAgentMainInitialization() {
        Instrumentation inst = ByteBuddyAgent.install();
        AgentMain.premain("retransform=true,packages=com.helix.agent", inst);

        assertNotNull(AgentMain.getInstrumentation());
        assertNotNull(AgentMain.getConfiguration());
        assertTrue(AgentMain.getConfiguration().isEnableRetransformation());
        assertEquals("com.helix.agent", AgentMain.getConfiguration().getTargetPackages());
    }
}
