package com.helix.core.debug;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.cli.repl.BytecodeDisassembler;
import com.helix.core.bytecode.BytecodeCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DebugSessionTest {

    private BytecodeCompiler compiler;
    private BytecodeDisassembler disassembler;
    private ExecutionContext context;
    private DebugSession session;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        compiler = new BytecodeCompiler();
        disassembler = new BytecodeDisassembler();
        context = new ExecutionContext();
        session = new DebugSession();
        DebugHook.setActiveSession(session);
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        session.close();
        DebugHook.setActiveSession(null);
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Non-debug compilation should incur zero overhead (no DebugHook instructions)")
    void testZeroOverheadNonDebugCompilation() throws Exception {
        String expression = "amount > 1000 && score >= 750";
        CompiledRule standardRule = compiler.compile(expression, false);

        String disassembly = disassembler.disassembleExpression(expression, false);
        assertFalse(disassembly.contains("DebugHook"), "Non-debug bytecode must NOT contain DebugHook invocations");
        assertFalse(disassembly.contains("onCondition"), "Non-debug bytecode must NOT contain onCondition probe hooks");

        context.setVariable("amount", 1500);
        context.setVariable("score", 800);

        ExecutionResult result = standardRule.execute(context);
        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getResult().orElse(null));
        assertTrue(session.getHistory().isEmpty(), "No debug frames should be captured during non-debug execution");
    }

    @Test
    @DisplayName("Debug compilation should emit INVOKESTATIC DebugHook.onCondition prior to IFNE branch jump")
    void testDebugCompilationEmitsProbeInstructions() throws Exception {
        String expression = "amount > 1000 && score >= 750";
        String disassembly = disassembler.disassembleExpression(expression, true);

        assertTrue(disassembly.contains("com/helix/core/debug/DebugHook"),
                "Debug-instrumented bytecode must contain DebugHook invocations");
        assertTrue(disassembly.contains("onCondition"),
                "Debug-instrumented bytecode must contain onCondition probe");
        assertTrue(disassembly.contains("IFNE"),
                "Debug-instrumented bytecode must contain IFNE branch jump");

        // Verify onCondition is called before IFNE
        int hookIndex = disassembly.indexOf("DebugHook.onCondition");
        int ifneIndex = disassembly.indexOf("IFNE");
        assertTrue(hookIndex < ifneIndex, "DebugHook.onCondition must be emitted prior to branch jump IFNE");
    }

    @Test
    @DisplayName("Breakpoints should pause execution and capture local variables and operands in EvaluationFrame")
    void testBreakpointPauseAndFrameCapture() throws Exception {
        String expression = "amount > 1000 && score >= 750";
        CompiledRule rule = compiler.compile(expression, true);

        context.setVariable("amount", 1500);
        context.setVariable("score", 800);

        session.setBreakpoint(0); // Breakpoint on first condition: amount > 1000

        CountDownLatch pausedLatch = new CountDownLatch(1);
        AtomicReference<EvaluationFrame> capturedFrame = new AtomicReference<>();

        session.setListener(new DebugSession.DebugListener() {
            @Override
            public void onPaused(EvaluationFrame frame) {
                capturedFrame.set(frame);
                pausedLatch.countDown();
            }

            @Override
            public void onResumed() {}

            @Override
            public void onCompleted(Object result) {}
        });

        Future<ExecutionResult> future = executor.submit(() -> rule.execute(context));

        // Wait for execution to hit breakpoint
        assertTrue(pausedLatch.await(5, TimeUnit.SECONDS), "Execution must pause at breakpoint 0");
        assertTrue(session.isPaused(), "Session must report paused state");

        EvaluationFrame frame = capturedFrame.get();
        assertNotNull(frame, "Evaluation frame must be captured");
        assertEquals(0, frame.getClauseIndex());
        assertEquals(">", frame.getOperator());
        assertEquals(1500, ((Number) frame.getLeftValue()).intValue());
        assertEquals(1000, ((Number) frame.getRightValue()).intValue());
        assertTrue(frame.isOutcome(), "amount > 1000 outcome must be true");
        assertEquals(1500, frame.getVariables().get("amount"));
        assertEquals(800, frame.getVariables().get("score"));

        // Continue execution
        session.continueExecution();
        ExecutionResult finalResult = future.get(5, TimeUnit.SECONDS);
        assertTrue(finalResult.isSuccess());
        assertEquals(Boolean.TRUE, finalResult.getResult().orElse(null));
    }

    @Test
    @DisplayName("Single-step mode should advance clause-by-clause and record variable state")
    void testSteppingThroughConditions() throws Exception {
        String expression = "amount > 1000 && score >= 750";
        CompiledRule rule = compiler.compile(expression, true);

        context.setVariable("amount", 2000);
        context.setVariable("score", 850);

        session.setStepMode(true); // Single-step mode from beginning

        CountDownLatch step1Latch = new CountDownLatch(1);
        CountDownLatch step2Latch = new CountDownLatch(1);
        AtomicReference<EvaluationFrame> frame1 = new AtomicReference<>();
        AtomicReference<EvaluationFrame> frame2 = new AtomicReference<>();

        session.setListener(new DebugSession.DebugListener() {
            @Override
            public void onPaused(EvaluationFrame frame) {
                if (frame.getClauseIndex() == 0) {
                    frame1.set(frame);
                    step1Latch.countDown();
                } else if (frame.getClauseIndex() == 1) {
                    frame2.set(frame);
                    step2Latch.countDown();
                }
            }

            @Override
            public void onResumed() {}

            @Override
            public void onCompleted(Object result) {}
        });

        Future<ExecutionResult> future = executor.submit(() -> rule.execute(context));

        // Paused at step 1 (clause 0)
        assertTrue(step1Latch.await(5, TimeUnit.SECONDS), "Step 1 must pause at clause 0");
        assertNotNull(frame1.get());
        assertEquals(0, frame1.get().getClauseIndex());
        assertEquals(">", frame1.get().getOperator());
        assertEquals(2000, ((Number) frame1.get().getLeftValue()).intValue());

        // Advance to step 2 (clause 1)
        session.step();
        assertTrue(step2Latch.await(5, TimeUnit.SECONDS), "Step 2 must pause at clause 1");
        assertNotNull(frame2.get());
        assertEquals(1, frame2.get().getClauseIndex());
        assertEquals(">=", frame2.get().getOperator());
        assertEquals(850, ((Number) frame2.get().getLeftValue()).intValue());
        assertEquals(750, ((Number) frame2.get().getRightValue()).intValue());

        // Continue to finish
        session.continueExecution();
        ExecutionResult finalResult = future.get(5, TimeUnit.SECONDS);
        assertTrue(finalResult.isSuccess());
        assertEquals(Boolean.TRUE, finalResult.getResult().orElse(null));

        // Verify history contains both frames
        List<EvaluationFrame> history = session.getHistory();
        assertEquals(2, history.size());
        assertEquals(0, history.get(0).getClauseIndex());
        assertEquals(1, history.get(1).getClauseIndex());
    }

    @Test
    @DisplayName("Breakpoint management: set, clear, clearAll, hasBreakpoint")
    void testBreakpointManagement() {
        session.setBreakpoint(0);
        session.setBreakpoint(2);
        assertTrue(session.hasBreakpoint(0));
        assertFalse(session.hasBreakpoint(1));
        assertTrue(session.hasBreakpoint(2));
        assertEquals(2, session.getBreakpoints().size());

        session.clearBreakpoint(0);
        assertFalse(session.hasBreakpoint(0));
        assertEquals(1, session.getBreakpoints().size());

        session.clearAllBreakpoints();
        assertTrue(session.getBreakpoints().isEmpty());
    }
}
