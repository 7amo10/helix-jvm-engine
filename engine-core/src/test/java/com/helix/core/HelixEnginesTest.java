package com.helix.core;

import com.helix.HelixApplication;
import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleEngine;
import com.helix.api.profiler.ProfileEvent;
import com.helix.api.profiler.ProfileEventListener;
import com.helix.api.profiler.Profiler;
import com.helix.core.parser.RuleSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HelixEngines & Default Implementations Test Suite")
class HelixEnginesTest {

    @Test
    @DisplayName("HelixEngines.createDefault() returns a working DefaultRuleEngine")
    void testCreateDefaultRuleEngine() throws Exception {
        RuleEngine engine = HelixEngines.createDefault();
        assertNotNull(engine);
        assertTrue(engine instanceof DefaultRuleEngine);

        RuleSchema schema = new RuleSchema(
                "test_rule", "1.0", "Test rule", "TEST",
                "x > 10", Map.of("x", Integer.class)
        );
        CompiledRule compiled = engine.compile(schema);
        assertNotNull(compiled);

        ExecutionContext ctx = new ExecutionContext(Map.of("x", 25));
        ExecutionResult result = engine.execute(compiled, ctx);
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getResult().orElse(null));

        CompletableFuture<ExecutionResult> asyncResult = engine.executeAsync(compiled, ctx);
        assertTrue(asyncResult.get().isSuccess());

        if (engine instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Test
    @DisplayName("HelixEngines.createProfiler() returns a working DefaultProfiler")
    void testCreateProfiler() {
        Profiler profiler = HelixEngines.createProfiler();
        assertNotNull(profiler);
        assertTrue(profiler instanceof DefaultProfiler);

        assertFalse(profiler.isRunning());
        profiler.start();
        assertTrue(profiler.isRunning());

        AtomicBoolean eventReceived = new AtomicBoolean(false);
        ProfileEventListener listener = event -> eventReceived.set(true);
        profiler.addListener(listener);

        ProfileEvent testEvent = () -> Instant.now();
        profiler.recordEvent(testEvent);

        assertTrue(eventReceived.get());
        assertTrue(profiler.getRecordedEvents().contains(testEvent));

        profiler.removeListener(listener);
        profiler.stop();
        assertFalse(profiler.isRunning());
    }

    @Test
    @DisplayName("HelixApplication deprecated delegates properly invoke HelixEngines")
    void testHelixApplicationDelegates() {
        RuleEngine engine = HelixApplication.createEngine();
        assertNotNull(engine);
        assertTrue(engine instanceof DefaultRuleEngine);

        Profiler profiler = HelixApplication.createProfiler(engine);
        assertNotNull(profiler);
        assertTrue(profiler instanceof DefaultProfiler);
    }
}
