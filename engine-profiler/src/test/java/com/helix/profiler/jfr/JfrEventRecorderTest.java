package com.helix.profiler.jfr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JfrEventRecorderTest {

    @Test
    void testEventRecorderMethods() {
        JfrEventRecorder recorder = new JfrEventRecorder();

        assertDoesNotThrow(() -> recorder.recordExecution("Rule1", "1.0", true, 1000L, null));
        assertDoesNotThrow(() -> recorder.recordCompilation("Rule1", "BYTE_BUDDY", 2000000L, 512, true));
        assertDoesNotThrow(() -> recorder.recordClassLoaderCreated("loader-1", "ISOLATED", "SharedLoader"));
        assertDoesNotThrow(() -> recorder.recordCacheEviction("Rule1", "L1", "EXPIRED"));
        assertDoesNotThrow(() -> recorder.recordMemoryAnalysis("com.helix.Rule", 64L, 256L, true));
    }
}
