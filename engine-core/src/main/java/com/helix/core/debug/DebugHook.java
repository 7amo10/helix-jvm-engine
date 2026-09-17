package com.helix.core.debug;

import com.helix.api.ExecutionContext;

/**
 * Static probe hook invoked directly by ASM-instrumented bytecode prior to condition branch jumps.
 */
public class DebugHook {

    private static volatile DebugSession activeSession;
    private static final ThreadLocal<DebugSession> threadSession = new ThreadLocal<>();

    public static void setActiveSession(DebugSession session) {
        activeSession = session;
    }

    public static DebugSession getActiveSession() {
        DebugSession session = threadSession.get();
        return session != null ? session : activeSession;
    }

    public static void setThreadSession(DebugSession session) {
        if (session != null) {
            threadSession.set(session);
        } else {
            threadSession.remove();
        }
    }

    /**
     * Probe method invoked prior to branch jump instructions (IFNE, IF_ICMPGT, etc.).
     *
     * @param clauseIndex index of the AST condition clause
     * @param description textual description or operator
     * @param left        evaluated left operand value
     * @param right       evaluated right operand value
     * @param outcome     boolean branch condition evaluation outcome
     * @param context     current execution context
     */
    public static void onCondition(int clauseIndex, String description, Object left, Object right,
                                   boolean outcome, ExecutionContext context) {
        DebugSession session = getActiveSession();
        if (session != null) {
            session.handleProbe(clauseIndex, description, left, right, outcome, context);
        }
    }

    /**
     * Fallback probe method without ExecutionContext.
     */
    public static void onCondition(int clauseIndex, String description, Object left, Object right,
                                   boolean outcome) {
        onCondition(clauseIndex, description, left, right, outcome, null);
    }
}
