package com.helix.core.debug;

import com.helix.api.ExecutionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages an interactive debug session with breakpoints, stepping, and frame stack inspection.
 */
public class DebugSession implements AutoCloseable {

    public interface DebugListener {
        void onPaused(EvaluationFrame frame);
        void onResumed();
        void onCompleted(Object result);
    }

    private final Set<Integer> breakpoints = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stepMode = new AtomicBoolean(false);
    private final AtomicLong pauseCounter = new AtomicLong(0);
    private final List<EvaluationFrame> history = new CopyOnWriteArrayList<>();
    private final List<ConditionClause> clauses = new CopyOnWriteArrayList<>();

    private volatile EvaluationFrame currentFrame;
    private volatile boolean paused = false;
    private volatile boolean completed = false;
    private volatile Object finalResult;
    private volatile DebugListener listener;

    private volatile CountDownLatch resumeLatch = new CountDownLatch(0);

    public DebugSession() {}

    public DebugSession(List<ConditionClause> clauses) {
        if (clauses != null) {
            this.clauses.addAll(clauses);
        }
    }

    public long getPauseCount() {
        return pauseCounter.get();
    }

    public void setListener(DebugListener listener) {
        this.listener = listener;
    }

    public void setClauses(List<ConditionClause> clauses) {
        this.clauses.clear();
        if (clauses != null) {
            this.clauses.addAll(clauses);
        }
    }

    public List<ConditionClause> getClauses() {
        return Collections.unmodifiableList(clauses);
    }

    public void setBreakpoint(int clauseIndex) {
        breakpoints.add(clauseIndex);
    }

    public void clearBreakpoint(int clauseIndex) {
        breakpoints.remove(clauseIndex);
    }

    public void clearAllBreakpoints() {
        breakpoints.clear();
    }

    public Set<Integer> getBreakpoints() {
        return Collections.unmodifiableSet(breakpoints);
    }

    public boolean hasBreakpoint(int clauseIndex) {
        return breakpoints.contains(clauseIndex);
    }

    public boolean isStepMode() {
        return stepMode.get();
    }

    public void setStepMode(boolean step) {
        this.stepMode.set(step);
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isCompleted() {
        return completed;
    }

    public EvaluationFrame getCurrentFrame() {
        return currentFrame;
    }

    public List<EvaluationFrame> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public Object getFinalResult() {
        return finalResult;
    }

    /**
     * Handles an incoming condition probe from bytecode.
     */
    public void handleProbe(int clauseIndex, String description, Object left, Object right,
                            boolean outcome, ExecutionContext context) {
        String operator = parseOperator(description);
        EvaluationFrame frame = new EvaluationFrame(
                clauseIndex, description, operator, left, right, outcome,
                context != null ? context.getVariables() : Collections.emptyMap()
        );

        boolean shouldPause = hasBreakpoint(clauseIndex) || stepMode.get();
        if (shouldPause) {
            this.currentFrame = frame;
            this.history.add(frame);
            this.paused = true;
            this.pauseCounter.incrementAndGet();
            this.resumeLatch = new CountDownLatch(1);

            if (listener != null) {
                listener.onPaused(frame);
            }

            try {
                // Await user action (step or continue)
                resumeLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                this.paused = false;
                if (listener != null) {
                    listener.onResumed();
                }
            }
        } else {
            this.history.add(frame);
            this.currentFrame = frame;
        }
    }

    /**
     * Advances to the next condition clause (single-step mode).
     */
    public void step() {
        this.stepMode.set(true);
        this.paused = false;
        this.resumeLatch.countDown();
    }

    /**
     * Continues execution until the next breakpoint or completion.
     */
    public void continueExecution() {
        this.stepMode.set(false);
        this.paused = false;
        this.resumeLatch.countDown();
    }

    /**
     * Signals that rule execution has completed.
     */
    public void onCompleted(Object result) {
        this.completed = true;
        this.finalResult = result;
        this.paused = false;
        this.resumeLatch.countDown();
        if (listener != null) {
            listener.onCompleted(result);
        }
    }

    private String parseOperator(String description) {
        if (description == null) return "?";
        if (description.contains(">=")) return ">=";
        if (description.contains("<=")) return "<=";
        if (description.contains("==")) return "==";
        if (description.contains("!=")) return "!=";
        if (description.contains(">")) return ">";
        if (description.contains("<")) return "<";
        if (description.contains("&&")) return "&&";
        if (description.contains("||")) return "||";
        return description;
    }

    @Override
    public void close() {
        this.resumeLatch.countDown();
        if (DebugHook.getActiveSession() == this) {
            DebugHook.setActiveSession(null);
        }
        DebugHook.setThreadSession(null);
    }
}
