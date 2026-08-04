package com.helix.profiler.jit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InliningAnalyzerTest {

    @Test
    void testParseInliningLog() {
        InliningAnalyzer analyzer = new InliningAnalyzer();
        String sampleLog = """
               @ 5   com.helix.core.Rule::getId (10 bytes)   inline (hot)
               @ 12  com.helix.core.Rule::validate (50 bytes)   too big
               @ 25  java.lang.String::equalsIgnoreCase (35 bytes)   inlined
            """;

        List<InliningAnalyzer.InliningDecision> decisions = analyzer.parseInliningLog(sampleLog);
        assertEquals(3, decisions.size());

        InliningAnalyzer.InliningDecision d1 = decisions.get(0);
        assertEquals(5, d1.bytecodeIndex());
        assertEquals("com.helix.core.Rule::getId", d1.targetMethod());
        assertEquals(10, d1.bytecodeSize());
        assertTrue(d1.inlined());

        InliningAnalyzer.InliningDecision d2 = decisions.get(1);
        assertEquals(12, d2.bytecodeIndex());
        assertFalse(d2.inlined());
        assertEquals("too big", d2.reason());
    }
}
