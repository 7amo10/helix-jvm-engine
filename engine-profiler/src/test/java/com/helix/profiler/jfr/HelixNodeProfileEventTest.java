package com.helix.profiler.jfr;

import com.helix.profiler.node.AstNodeProfiler;
import com.helix.profiler.node.NodeStats;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HelixNodeProfileEvent and JFR Parsing Tests")
class HelixNodeProfileEventTest {

    @Test
    @DisplayName("Should populate all JFR event fields correctly")
    void testHelixNodeProfileEventFields() {
        HelixNodeProfileEvent event = new HelixNodeProfileEvent();
        event.ruleName = "FraudRule";
        event.nodeId = "ml_fraud_model_v1";
        event.executionCount = 5000L;
        event.avgCostNanos = 42500.5;
        event.failureRate = 0.997;
        event.ratio = 42628.38;

        event.begin();
        event.commit();

        assertEquals("FraudRule", event.ruleName);
        assertEquals("ml_fraud_model_v1", event.nodeId);
        assertEquals(5000L, event.executionCount);
        assertEquals(42500.5, event.avgCostNanos);
        assertEquals(0.997, event.failureRate);
        assertEquals(42628.38, event.ratio);
    }

    @Test
    @DisplayName("Should capture and parse HelixNodeProfileEvent in JFR recordings")
    void testJfrRecordingAndParsing(@TempDir Path tempDir) throws IOException {
        JfrRecordingManager recordingManager = new JfrRecordingManager();
        recordingManager.startRecording("HelixNodeProfileTestRecording");

        // Record a profile event via JfrEventRecorder
        JfrEventRecorder eventRecorder = new JfrEventRecorder();
        eventRecorder.recordNodeProfile("TelemetryRule", "clause_amount_gt_1000", 12500L, 28.5, 0.85, 33.53);

        // Also record via AstNodeProfiler JFR emission helper
        AstNodeProfiler.reset();
        AstNodeProfiler.record("TelemetryRule", "clause_ml_inference", 35000L, false);
        AstNodeProfiler.emitJfrEvent("TelemetryRule", "clause_ml_inference");

        recordingManager.stopRecording();

        Path jfrFile = tempDir.resolve("node-profile-test.jfr");
        recordingManager.dumpRecording(jfrFile);

        assertTrue(Files.exists(jfrFile));
        assertTrue(Files.size(jfrFile) > 0);

        List<RecordedEvent> recordedEvents = RecordingFile.readAllEvents(jfrFile);
        assertNotNull(recordedEvents);

        List<RecordedEvent> profileEvents = recordedEvents.stream()
                .filter(e -> "com.helix.NodeProfile".equals(e.getEventType().getName()))
                .toList();

        assertFalse(profileEvents.isEmpty(), "JFR recording must contain com.helix.NodeProfile events");

        RecordedEvent event1 = profileEvents.stream()
                .filter(e -> "clause_amount_gt_1000".equals(e.getString("nodeId")))
                .findFirst()
                .orElseThrow();

        assertEquals("TelemetryRule", event1.getString("ruleName"));
        assertEquals(12500L, event1.getLong("executionCount"));
        assertEquals(28.5, event1.getDouble("avgCostNanos"), 0.001);
        assertEquals(0.85, event1.getDouble("failureRate"), 0.001);
        assertEquals(33.53, event1.getDouble("ratio"), 0.01);

        recordingManager.close();
    }
}
