package com.helix.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe callback receiver invoked from instrumented bytecode during method entry, exit, or object allocation.
 */
public class AgentCallback {

    private static final Logger log = LoggerFactory.getLogger(AgentCallback.class);

    private static final ThreadLocal<ExecutionStats> THREAD_STATS = ThreadLocal.withInitial(ExecutionStats::new);

    public static void onMethodEntry(String className, String methodName) {
        THREAD_STATS.get().pushMethodEntry();
        if (log.isTraceEnabled()) {
            log.trace("[HelixCallback] Entering {}.{}", className, methodName);
        }
    }

    public static void onMethodExit(String className, String methodName) {
        THREAD_STATS.get().popMethodExit();
        if (log.isTraceEnabled()) {
            log.trace("[HelixCallback] Exiting {}.{}", className, methodName);
        }
    }

    public static void recordAllocation(String className, long bytes) {
        THREAD_STATS.get().recordAllocation();
        if (log.isTraceEnabled()) {
            log.trace("[HelixCallback] Allocated {} ({} bytes)", className, bytes);
        }
    }

    public static ExecutionStats getThreadStats() {
        return THREAD_STATS.get();
    }

    public static void clearThreadStats() {
        THREAD_STATS.get().reset();
    }
}
