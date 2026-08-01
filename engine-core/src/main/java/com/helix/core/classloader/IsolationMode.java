package com.helix.core.classloader;

/**
 * Strategy mode determining ClassLoader isolation and sharing behavior.
 */
public enum IsolationMode {
    /**
     * Completely isolated RuleClassLoader created per compiled rule.
     */
    ISOLATED,

    /**
     * Single shared RuleClassLoader instance for all compiled rules.
     */
    SHARED,

    /**
     * RuleClassLoader created per category, inheriting from SharedUtilityClassLoader.
     */
    HIERARCHICAL
}
