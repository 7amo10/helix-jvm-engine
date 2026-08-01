package com.helix.core.classloader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassLoaderManagerTest {

    @Test
    @DisplayName("Should create distinct ClassLoaders per rule in ISOLATED mode")
    void testIsolatedIsolationMode() {
        ClassLoaderManager manager = new ClassLoaderManager(IsolationMode.ISOLATED);

        RuleClassLoader loader1 = manager.getOrCreateClassLoader("FINANCE", "RuleA");
        RuleClassLoader loader2 = manager.getOrCreateClassLoader("FINANCE", "RuleB");

        assertNotNull(loader1);
        assertNotNull(loader2);
        assertNotSame(loader1, loader2, "ISOLATED mode must create separate ClassLoader instances for each rule");
        assertEquals(2, manager.getActiveLoaders().size());
        assertEquals(2, manager.getLeakDetector().getTrackedCount());

        manager.close();
        assertTrue(loader1.isClosed());
        assertTrue(loader2.isClosed());
        assertTrue(manager.getActiveLoaders().isEmpty());
    }

    @Test
    @DisplayName("Should reuse single global ClassLoader in SHARED mode")
    void testSharedIsolationMode() {
        ClassLoaderManager manager = new ClassLoaderManager(IsolationMode.SHARED);

        RuleClassLoader loader1 = manager.getOrCreateClassLoader("FRAUD", "RuleX");
        RuleClassLoader loader2 = manager.getOrCreateClassLoader("RISK", "RuleY");

        assertNotNull(loader1);
        assertSame(loader1, loader2, "SHARED mode must return the identical global RuleClassLoader");
        assertEquals(1, manager.getActiveLoaders().size());

        manager.close();
        assertTrue(loader1.isClosed());
    }

    @Test
    @DisplayName("Should create ClassLoaders per category in HIERARCHICAL mode")
    void testHierarchicalIsolationMode() {
        ClassLoaderManager manager = new ClassLoaderManager(IsolationMode.HIERARCHICAL);

        RuleClassLoader finance1 = manager.getOrCreateClassLoader("FINANCE", "Rule1");
        RuleClassLoader finance2 = manager.getOrCreateClassLoader("FINANCE", "Rule2");
        RuleClassLoader fraud1 = manager.getOrCreateClassLoader("FRAUD", "Rule3");

        assertNotNull(finance1);
        assertSame(finance1, finance2, "HIERARCHICAL mode must reuse same ClassLoader for same category");
        assertNotSame(finance1, fraud1, "HIERARCHICAL mode must create separate ClassLoaders for different categories");

        assertEquals(2, manager.getActiveLoaders().size());

        manager.close();
        assertTrue(finance1.isClosed());
        assertTrue(fraud1.isClosed());
    }

    @Test
    @DisplayName("Should close individual ClassLoader by loaderId")
    void testCloseIndividualLoader() {
        ClassLoaderManager manager = new ClassLoaderManager(IsolationMode.HIERARCHICAL);

        RuleClassLoader loader = manager.getOrCreateClassLoader("COMPLIANCE", "CheckRule");
        String loaderId = loader.getLoaderId();

        assertFalse(loader.isClosed());
        manager.closeLoader(loaderId);

        assertTrue(loader.isClosed());
        assertFalse(manager.getActiveLoaders().containsKey(loaderId));
    }
}
