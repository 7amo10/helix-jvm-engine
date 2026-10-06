package com.helix.core.sandbox;

import com.helix.api.sandbox.CapabilityPolicy;
import com.helix.core.classloader.RuleClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Isolated ClassLoader for executing rules submitted by untrusted tenants.
 * <p>
 * Extends {@link RuleClassLoader} and enforces defence-in-depth security by verifying
 * requested class names against a {@link CapabilityPolicy} before delegating to the parent loader.
 */
public class SandboxClassLoader extends RuleClassLoader {

    private static final Logger log = LoggerFactory.getLogger(SandboxClassLoader.class);
    private static final AtomicLong LOADER_COUNTER = new AtomicLong(0);

    private final CapabilityPolicy policy;

    /**
     * Constructs a SandboxClassLoader with default naming and standard DefaultCapabilityPolicy.
     *
     * @param parent parent ClassLoader
     */
    public SandboxClassLoader(ClassLoader parent) {
        this("sandbox-loader-" + LOADER_COUNTER.incrementAndGet(), parent, new DefaultCapabilityPolicy());
    }

    /**
     * Constructs a SandboxClassLoader with custom identifier and standard DefaultCapabilityPolicy.
     *
     * @param loaderId unique identifier for this classloader
     * @param parent   parent ClassLoader
     */
    public SandboxClassLoader(String loaderId, ClassLoader parent) {
        this(loaderId, parent, new DefaultCapabilityPolicy());
    }

    /**
     * Constructs a SandboxClassLoader with custom identifier, parent loader, and specific capability policy.
     *
     * @param loaderId unique identifier for this classloader
     * @param parent   parent ClassLoader
     * @param policy   capability policy to enforce on class loading
     */
    public SandboxClassLoader(String loaderId, ClassLoader parent, CapabilityPolicy policy) {
        super(loaderId, parent);
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        log.debug("SandboxClassLoader '{}' initialized with capability policy", loaderId);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name != null) {
            // Verify against capability policy; if prohibited, deny loading immediately
            if (!policy.isPermitted(name, "*")) {
                log.warn("Blocked attempt to load restricted class '{}' in sandbox loader '{}'", name, getLoaderId());
                throw new ClassNotFoundException(
                        "Class '" + name + "' is blocked in sandboxed execution mode by capability policy");
            }
        }
        return super.loadClass(name, resolve);
    }

    /**
     * Returns the capability policy enforced by this sandbox loader.
     *
     * @return capability policy
     */
    public CapabilityPolicy getPolicy() {
        return policy;
    }
}
