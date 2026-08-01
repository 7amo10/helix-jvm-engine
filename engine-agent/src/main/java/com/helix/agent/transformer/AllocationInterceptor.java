package com.helix.agent.transformer;

/**
 * Static callback target invoked from ASM-instrumented bytecode upon object instantiation.
 */
public class AllocationInterceptor {

    public static void onAllocation(String className) {
        AllocationTracker.getInstance().recordAllocation(className);
    }
}
