package com.helix.agent.transformer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleClassTransformerTest {

    public static class SampleRuleTarget {
        public void execute() {
            // Sample method
        }
    }

    @Test
    @DisplayName("Should skip transformation for classes outside target package")
    void testSkipNonMatchingClass() {
        RuleClassTransformer transformer = new RuleClassTransformer("com/helix/agent");
        byte[] result = transformer.transform(null, "java/lang/String", null, null, new byte[0]);
        assertNull(result);
    }

    @Test
    @DisplayName("Should transform matching class bytes successfully")
    void testTransformMatchingClass() throws Exception {
        String internalName = "com/helix/agent/transformer/RuleClassTransformerTest$SampleRuleTarget";
        byte[] originalBytes = RuleClassTransformerTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")
                .readAllBytes();

        RuleClassTransformer transformer = new RuleClassTransformer("com/helix/agent");
        byte[] transformedBytes = transformer.transform(
                RuleClassTransformerTest.class.getClassLoader(),
                internalName,
                null,
                null,
                originalBytes
        );

        assertNotNull(transformedBytes);
        assertTrue(transformedBytes.length > 0);
    }
}
