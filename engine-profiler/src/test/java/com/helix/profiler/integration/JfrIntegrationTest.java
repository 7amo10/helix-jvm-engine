package com.helix.profiler.integration;

import com.helix.profiler.jfr.JfrEventRecorder;
import com.helix.profiler.jfr.JfrRecordingManager;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JfrIntegrationTest {

    @Test
    void testJfrRecordingAndEventPlayback(@TempDir Path tempDir) throws IOException {
        JfrRecordingManager recordingManager = new JfrRecordingManager();
        JfrEventRecorder eventRecorder = new JfrEventRecorder();

        recordingManager.startRecording("HelixIntegrationTestRecording");
        assertTrue(recordingManager.isRecording());

        eventRecorder.recordExecution("RuleA", "1.0", true, 1500000L, null);
        eventRecorder.recordCompilation("RuleA", "BYTE_BUDDY", 45000000L, 1024, true);

        recordingManager.stopRecording();
        assertFalse(recordingManager.isRecording());

        Path jfrFile = tempDir.resolve("integration-test-output.jfr");
        recordingManager.dumpRecording(jfrFile);

        assertTrue(Files.exists(jfrFile));
        assertTrue(Files.size(jfrFile) > 0);

        List<RecordedEvent> recordedEvents = RecordingFile.readAllEvents(jfrFile);
        assertNotNull(recordedEvents);

        boolean foundExecution = recordedEvents.stream()
                .anyMatch(e -> e.getEventType().getName().equals("com.helix.RuleExecution"));

        boolean foundCompilation = recordedEvents.stream()
                .anyMatch(e -> e.getEventType().getName().equals("com.helix.RuleCompilation"));

        assertTrue(foundExecution, "Should contain recorded com.helix.RuleExecution event");
        assertTrue(foundCompilation, "Should contain recorded com.helix.RuleCompilation event");

        recordingManager.close();
    }
}
