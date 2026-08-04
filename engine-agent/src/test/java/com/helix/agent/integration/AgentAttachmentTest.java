package com.helix.agent.integration;

import com.helix.agent.AgentMain;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;

import static org.junit.jupiter.api.Assertions.*;

class AgentAttachmentTest {

    @Test
    @DisplayName("Should dynamically attach Java Agent and expose Instrumentation handle")
    void testDynamicAgentAttachment() {
        Instrumentation inst = ByteBuddyAgent.install();
        assertNotNull(inst);

        AgentMain.agentmain("retransform=true,packages=com.helix", inst);

        assertNotNull(AgentMain.getInstrumentation());
        assertNotNull(AgentMain.getConfiguration());
    }
}
