package com.helix.core.classloader;

import com.helix.api.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedUtilityClassLoaderTest {

    @Test
    @DisplayName("Should enforce singleton instance behavior")
    void testSingletonInstance() {
        SharedUtilityClassLoader instance1 = SharedUtilityClassLoader.getInstance();
        SharedUtilityClassLoader instance2 = SharedUtilityClassLoader.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "SharedUtilityClassLoader.getInstance() must return the identical singleton instance");
    }

    @Test
    @DisplayName("Should correctly identify shared packages")
    void testIsSharedPackage() {
        SharedUtilityClassLoader loader = SharedUtilityClassLoader.getInstance();

        assertTrue(loader.isSharedPackage("com.helix.api.Rule"));
        assertTrue(loader.isSharedPackage("com.helix.api.ExecutionContext"));
        assertTrue(loader.isSharedPackage("com.helix.core.parser.ast.ExpressionNode"));
        assertTrue(loader.isSharedPackage("com.fasterxml.jackson.databind.ObjectMapper"));
        assertTrue(loader.isSharedPackage("org.slf4j.Logger"));

        assertFalse(loader.isSharedPackage("java.lang.String"));
        assertFalse(loader.isSharedPackage("com.unrelated.CustomClass"));
        assertFalse(loader.isSharedPackage(null));
    }

    @Test
    @DisplayName("Should track loaded shared classes")
    void testLoadClassAndTracking() throws Exception {
        SharedUtilityClassLoader loader = SharedUtilityClassLoader.getInstance();

        Class<?> ruleClass = loader.loadClass("com.helix.api.Rule");
        assertNotNull(ruleClass);
        assertEquals(Rule.class, ruleClass);

        assertTrue(loader.getSharedLoadedClassNames().contains("com.helix.api.Rule"));
    }

    @Test
    @DisplayName("Should serve as parent ClassLoader for RuleClassLoader")
    void testParentClassLoaderForRuleClassLoader() {
        SharedUtilityClassLoader sharedParent = SharedUtilityClassLoader.getInstance();
        RuleClassLoader ruleLoader = new RuleClassLoader("test-child", sharedParent);

        assertSame(sharedParent, ruleLoader.getParent(), "RuleClassLoader parent must be SharedUtilityClassLoader");
    }
}
