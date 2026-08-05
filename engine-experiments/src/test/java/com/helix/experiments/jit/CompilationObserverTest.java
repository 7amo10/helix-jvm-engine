package com.helix.experiments.jit;

import com.helix.profiler.jit.CompilationEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CompilationObserverTest {

    @Test
    void testCompilationObserverEventTracking() {
        CompilationObserver observer = new CompilationObserver();

        CompilationEvent c1 = new CompilationEvent(100L, 1, 3, "com.helix.RuleEngine::eval", 40, false, false, false, "NORMAL", Instant.now());
        CompilationEvent c2 = new CompilationEvent(200L, 2, 4, "com.helix.RuleEngine::eval", 40, false, false, false, "NORMAL", Instant.now());

        observer.onCompilationEvent(c1);
        observer.onCompilationEvent(c2);

        assertEquals(4, observer.getHighestTierForMethod("RuleEngine::eval"));
        assertEquals(1, observer.getCompilationCountForTier(3));
        assertEquals(1, observer.getCompilationCountForTier(4));
    }
}
