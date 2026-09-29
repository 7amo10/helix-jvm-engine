package com.helix.api.sandbox;

/**
 * Service Provider Interface (SPI) for inspecting and enforcing capability policies on rule expressions.
 * <p>
 * Capability enforcement is applied statically at rule compilation / AST traversal time,
 * guaranteeing zero overhead during hot evaluation execution.
 */
@FunctionalInterface
public interface CapabilityPolicy {

    /**
     * Inspects a method call candidate during rule expression compilation.
     *
     * @param className  the target fully qualified class name (e.g., "java.lang.System")
     * @param methodName the target method name (e.g., "exit")
     * @throws CapabilityViolationException if the method call is forbidden under this policy
     */
    void checkMethodCall(String className, String methodName) throws CapabilityViolationException;

    /**
     * Inspects package or class-level access candidate during rule compilation or class loading.
     *
     * @param packageName the target package or fully qualified class name
     * @throws CapabilityViolationException if the package or class is forbidden under this policy
     */
    default void checkPackageAccess(String packageName) throws CapabilityViolationException {
        checkMethodCall(packageName, "*");
    }

    /**
     * Checks whether an operation is permitted without throwing an exception.
     *
     * @param className  the target fully qualified class name
     * @param methodName the target method name
     * @return true if permitted, false if prohibited
     */
    default boolean isPermitted(String className, String methodName) {
        try {
            checkMethodCall(className, methodName);
            return true;
        } catch (CapabilityViolationException e) {
            return false;
        }
    }
}
