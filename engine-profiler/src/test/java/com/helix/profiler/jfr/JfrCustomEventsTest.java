package com.helix.profiler.jfr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JfrCustomEventsTest {

    @Test
    void testRuleExecutionEventFields() {
        RuleExecutionEvent event = new RuleExecutionEvent();
        event.ruleName = "TestRule";
        event.ruleVersion = "1.0";
        event.success = true;
        event.durationNanos = 5000L;
        event.errorMessage = "";

        event.begin();
        event.commit();

        assertEquals("TestRule", event.ruleName);
        assertTrue(event.success);
    }

    @Test
    void testRuleCompilationEventFields() {
        RuleCompilationEvent event = new RuleCompilationEvent();
        event.ruleName = "CompileRule";
        event.generatorType = "BYTE_BUDDY";
        event.compilationTimeNanos = 1000000L;
        event.bytecodeSizeBytes = 256;
        event.success = true;

        event.begin();
        event.commit();

        assertEquals("CompileRule", event.ruleName);
        assertEquals("BYTE_BUDDY", event.generatorType);
    }
}
