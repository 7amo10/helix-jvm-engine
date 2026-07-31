package com.helix.api.agent;

/**
 * Callback interface invoked when class bytecode instrumentation occurs.
 */
@FunctionalInterface
public interface InstrumentationCallback {

    /**
     * Invoked when a target class is instrumented by the Java Agent.
     *
     * @param className  fully qualified class name
     * @param classBytes raw transformed bytecode bytes
     */
    void onClassTransformed(String className, byte[] classBytes);
}
