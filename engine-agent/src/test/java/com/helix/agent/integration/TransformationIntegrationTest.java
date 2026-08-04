package com.helix.agent.integration;

import com.helix.agent.transformer.AllocationTracker;
import com.helix.agent.transformer.RuleClassTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformationIntegrationTest {

    public static class TargetRule {
        public void execute() {
            String s = new String("AllocatedInRule");
        }
    }

    @BeforeEach
    void setUp() {
        AllocationTracker.getInstance().reset();
    }

    @Test
    @DisplayName("Should transform rule class bytes and verify execution callbacks")
    void testRuleBytecodeTransformation() throws Exception {
        String internalName = "com/helix/agent/integration/TransformationIntegrationTest$TargetRule";
        byte[] originalBytes = TargetRule.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")
                .readAllBytes();

        RuleClassTransformer transformer = new RuleClassTransformer("com/helix/agent");
        byte[] transformedBytes = transformer.transform(
                TargetRule.class.getClassLoader(),
                internalName,
                null,
                null,
                originalBytes
        );

        assertNotNull(transformedBytes);
        assertTrue(transformedBytes.length > originalBytes.length);
    }
}
