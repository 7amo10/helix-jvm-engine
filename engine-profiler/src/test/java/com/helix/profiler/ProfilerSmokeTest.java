package com.helix.profiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerSmokeTest {

    @Test
    @DisplayName("Engine Profiler module test harness smoke test")
    void smokeTest() {
        assertTrue(true, "engine-profiler test infrastructure initialized");
    }
}
