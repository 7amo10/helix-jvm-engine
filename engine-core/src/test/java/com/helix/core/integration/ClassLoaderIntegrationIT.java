package com.helix.core.integration;

import com.helix.core.classloader.ClassLoaderManager;
import com.helix.core.classloader.IsolationMode;
import com.helix.core.classloader.RuleClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassLoaderIntegrationIT {

    private ClassLoaderManager classLoaderManager;

    @BeforeEach
    void setUp() {
        classLoaderManager = new ClassLoaderManager(IsolationMode.HIERARCHICAL);
    }

    @AfterEach
    void tearDown() {
        classLoaderManager.close();
    }

    @Test
    @DisplayName("IT: Hierarchical ClassLoader category sharing and lifecycle management")
    void testHierarchicalClassLoaderLifecycle() {
        RuleClassLoader loaderFin1 = classLoaderManager.getOrCreateClassLoader("FINANCE", "RiskRule1");
        RuleClassLoader loaderFin2 = classLoaderManager.getOrCreateClassLoader("FINANCE", "RiskRule2");
        RuleClassLoader loaderSec1 = classLoaderManager.getOrCreateClassLoader("SECURITY", "AuthRule1");

        assertSame(loaderFin1, loaderFin2);
        assertNotSame(loaderFin1, loaderSec1);

        assertEquals(2, classLoaderManager.getActiveLoaders().size());

        classLoaderManager.closeLoader("hierarchical_FINANCE");
        assertEquals(1, classLoaderManager.getActiveLoaders().size());
        assertTrue(loaderFin1.isClosed());
    }
}
