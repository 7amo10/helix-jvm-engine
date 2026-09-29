package com.helix.core.sandbox;

import com.helix.api.sandbox.CapabilityPolicy;
import com.helix.api.sandbox.CapabilityViolationException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Standard implementation of {@link CapabilityPolicy} enforcing security capability boundaries
 * for untrusted rules.
 * <p>
 * Blocks OS process execution, raw file system access, network sockets, reflection, thread creation,
 * and dangerous runtime/system calls, while allowing safe arithmetic, string processing, and collection utilities.
 */
public class DefaultCapabilityPolicy implements CapabilityPolicy {

    // Default package and class prefixes strictly blocked from untrusted execution
    private static final Set<String> DEFAULT_BLOCKED_PREFIXES = Set.of(
            "java.io.",
            "java.nio.",
            "java.net.",
            "java.lang.reflect.",
            "java.lang.invoke.",
            "java.lang.Thread",
            "java.lang.ProcessBuilder",
            "java.lang.Runtime",
            "java.lang.ClassLoader",
            "java.lang.instrument.",
            "java.lang.management.",
            "java.security.",
            "javax.script.",
            "java.util.concurrent.",
            "sun.",
            "com.sun.",
            "jdk.internal.",
            "org.openjdk.jmh."
    );

    // Default dangerous methods on classes that are otherwise partially or conditionally accessed
    private static final Set<String> DEFAULT_BLOCKED_METHODS = Set.of(
            "java.lang.System.exit",
            "java.lang.System.gc",
            "java.lang.System.runFinalization",
            "java.lang.System.load",
            "java.lang.System.loadLibrary",
            "java.lang.System.mapLibraryName",
            "java.lang.System.setSecurityManager",
            "java.lang.System.setIn",
            "java.lang.System.setOut",
            "java.lang.System.setErr",
            "java.lang.System.setProperties",
            "java.lang.System.setProperty",
            "java.lang.System.clearProperty",
            "java.lang.Class.forName",
            "java.lang.Class.getDeclaredMethod",
            "java.lang.Class.getDeclaredField",
            "java.lang.Class.getDeclaredConstructor",
            "java.lang.Class.getMethod",
            "java.lang.Class.getField",
            "java.lang.Class.getConstructor",
            "java.lang.Class.newInstance",
            "java.lang.Class.getClassLoader"
    );

    private final Set<String> blockedPrefixes;
    private final Set<String> blockedMethods;
    private final Set<String> allowedPrefixes;

    /**
     * Constructs a DefaultCapabilityPolicy using standard security rules.
     */
    public DefaultCapabilityPolicy() {
        this(DEFAULT_BLOCKED_PREFIXES, DEFAULT_BLOCKED_METHODS, Collections.emptySet());
    }

    private DefaultCapabilityPolicy(Set<String> blockedPrefixes, Set<String> blockedMethods, Set<String> allowedPrefixes) {
        this.blockedPrefixes = Set.copyOf(blockedPrefixes);
        this.blockedMethods = Set.copyOf(blockedMethods);
        this.allowedPrefixes = Set.copyOf(allowedPrefixes);
    }

    @Override
    public void checkMethodCall(String className, String methodName) throws CapabilityViolationException {
        if (className == null || className.trim().isEmpty()) {
            return;
        }

        String normalizedClass = className.trim();
        String normalizedMethod = (methodName != null) ? methodName.trim() : "*";

        // Check if explicitly allowed (overrides block list)
        for (String allowed : allowedPrefixes) {
            if (normalizedClass.startsWith(allowed) || normalizedClass.equals(allowed)) {
                return;
            }
        }

        // Check exact blocked method match
        String exactMethod = normalizedClass + "." + normalizedMethod;
        if (blockedMethods.contains(exactMethod)) {
            throw new CapabilityViolationException(normalizedClass, normalizedMethod);
        }

        // Check blocked package/class prefixes
        for (String prefix : blockedPrefixes) {
            if (normalizedClass.startsWith(prefix) || normalizedClass.equals(prefix)) {
                throw new CapabilityViolationException(normalizedClass, normalizedMethod);
            }
        }
    }

    /**
     * Creates a new builder for customizing capability policies.
     *
     * @return a new Builder instance initialized with default blocked rules
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing custom or extended capability policies.
     */
    public static final class Builder {
        private final Set<String> blockedPrefixes = new HashSet<>(DEFAULT_BLOCKED_PREFIXES);
        private final Set<String> blockedMethods = new HashSet<>(DEFAULT_BLOCKED_METHODS);
        private final Set<String> allowedPrefixes = new HashSet<>();

        public Builder blockPrefix(String prefix) {
            if (prefix != null && !prefix.trim().isEmpty()) {
                this.blockedPrefixes.add(prefix.trim());
            }
            return this;
        }

        public Builder blockMethod(String className, String methodName) {
            if (className != null && methodName != null) {
                this.blockedMethods.add(className.trim() + "." + methodName.trim());
            }
            return this;
        }

        public Builder allowPrefix(String prefix) {
            if (prefix != null && !prefix.trim().isEmpty()) {
                this.allowedPrefixes.add(prefix.trim());
            }
            return this;
        }

        public DefaultCapabilityPolicy build() {
            return new DefaultCapabilityPolicy(blockedPrefixes, blockedMethods, allowedPrefixes);
        }
    }
}
