package com.helix.core;

import com.helix.api.RuleEngine;
import com.helix.api.profiler.Profiler;

/**
 * Public programmatic factory facade for creating and configuring
 * Helix RuleEngine and Profiler instances for embedded environments.
 */
public final class HelixEngines {

    private HelixEngines() {
        // Prevent instantiation of utility factory
    }

    /**
     * Creates a new standard {@link RuleEngine} instance orchestrating rule compilation and execution.
     *
     * @return a new {@link RuleEngine} backed by {@link DefaultRuleEngine}
     */
    public static RuleEngine createDefault() {
        return new DefaultRuleEngine();
    }

    /**
     * Creates a new in-memory {@link Profiler} tracking engine telemetry events.
     *
     * @return a new {@link Profiler} backed by {@link DefaultProfiler}
     */
    public static Profiler createProfiler() {
        return new DefaultProfiler();
    }
}
