package com.helix.profiler.jfr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JfrRecordingManagerTest {

    @Test
    void testRecordingLifecycleAndDump(@TempDir Path tempDir) throws IOException {
        JfrRecordingManager manager = new JfrRecordingManager();
        assertFalse(manager.isRecording());

        manager.startRecording("HelixTestRecording");
        assertTrue(manager.isRecording());

        JfrEventRecorder recorder = new JfrEventRecorder();
        recorder.recordExecution("TestRule", "1.0", true, 5000L, null);

        manager.stopRecording();
        assertFalse(manager.isRecording());

        Path jfrFile = tempDir.resolve("test-recording.jfr");
        manager.dumpRecording(jfrFile);

        assertTrue(Files.exists(jfrFile));
        assertTrue(Files.size(jfrFile) > 0);

        manager.close();
    }
}
