package com.helix.experiments.metaspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicClassGeneratorTest {

    @Test
    void testDynamicClassGeneration() throws Exception {
        DynamicClassGenerator generator = new DynamicClassGenerator();
        DynamicClassGenerator.GeneratedClassResult result = generator.generateDynamicClass();

        assertNotNull(result);
        assertNotNull(result.generatedClass());
        assertNotNull(result.classLoader());

        Object instance = result.generatedClass().getDeclaredConstructor().newInstance();
        assertEquals("DynamicRuleInstance", instance.toString());

        result.classLoader().close();
    }
}
