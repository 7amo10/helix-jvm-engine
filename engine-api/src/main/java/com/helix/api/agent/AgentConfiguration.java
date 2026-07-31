package com.helix.api.agent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration options for the Helix Java Agent instrumentation.
 */
public class AgentConfiguration {

    private final boolean memoryProfilingEnabled;
    private final boolean classTransformEnabled;
    private final Set<String> targetPackages;

    public AgentConfiguration(boolean memoryProfilingEnabled, boolean classTransformEnabled, Set<String> targetPackages) {
        this.memoryProfilingEnabled = memoryProfilingEnabled;
        this.classTransformEnabled = classTransformEnabled;
        this.targetPackages = targetPackages != null ? new HashSet<>(targetPackages) : Collections.emptySet();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isMemoryProfilingEnabled() {
        return memoryProfilingEnabled;
    }

    public boolean isClassTransformEnabled() {
        return classTransformEnabled;
    }

    public Set<String> getTargetPackages() {
        return Collections.unmodifiableSet(targetPackages);
    }

    public static class Builder {
        private boolean memoryProfilingEnabled = true;
        private boolean classTransformEnabled = true;
        private Set<String> targetPackages = new HashSet<>();

        public Builder withMemoryProfiling(boolean enabled) {
            this.memoryProfilingEnabled = enabled;
            return this;
        }

        public Builder withClassTransform(boolean enabled) {
            this.classTransformEnabled = enabled;
            return this;
        }

        public Builder addTargetPackage(String pkg) {
            if (pkg != null && !pkg.isBlank()) {
                this.targetPackages.add(pkg);
            }
            return this;
        }

        public AgentConfiguration build() {
            return new AgentConfiguration(memoryProfilingEnabled, classTransformEnabled, targetPackages);
        }
    }
}
