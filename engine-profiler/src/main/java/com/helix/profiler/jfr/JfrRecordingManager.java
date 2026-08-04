package com.helix.profiler.jfr;

import jdk.jfr.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Manager for managing JDK Flight Recorder (JFR) recording sessions programmatically.
 */
public class JfrRecordingManager {

    private static final Logger log = LoggerFactory.getLogger(JfrRecordingManager.class);

    private Recording activeRecording;

    /**
     * Starts a new JFR recording session.
     */
    public synchronized void startRecording(String recordingName) {
        if (isRecording()) {
            log.warn("JFR recording session already active. Stopping active session first.");
            stopRecording();
        }

        try {
            Recording recording = new Recording();
            if (recordingName != null && !recordingName.isBlank()) {
                recording.setName(recordingName);
            }
            recording.enable("com.helix.RuleExecution");
            recording.enable("com.helix.RuleCompilation");
            recording.enable("com.helix.ClassLoaderCreated");
            recording.enable("com.helix.CacheEviction");
            recording.enable("com.helix.MemoryAnalysis");

            recording.start();
            this.activeRecording = recording;
            log.info("Started JFR recording: {}", recording.getName());
        } catch (Exception e) {
            log.error("Failed to start JFR recording: {}", e.getMessage(), e);
        }
    }

    /**
     * Stops the active JFR recording session.
     */
    public synchronized void stopRecording() {
        if (activeRecording != null) {
            try {
                activeRecording.stop();
                log.info("Stopped JFR recording: {}", activeRecording.getName());
            } catch (Exception e) {
                log.error("Error stopping JFR recording: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Dumps the recorded JFR events to the specified file.
     */
    public synchronized void dumpRecording(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination path must not be null");
        if (activeRecording == null) {
            throw new IllegalStateException("No active or completed JFR recording to dump.");
        }

        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        try {
            activeRecording.dump(destination);
            log.info("Dumped JFR recording file to: {}", destination);
        } catch (IOException e) {
            log.error("Failed to dump JFR recording: {}", e.getMessage(), e);
            throw e;
        }
    }

    public synchronized boolean isRecording() {
        return activeRecording != null && activeRecording.getState() == jdk.jfr.RecordingState.RUNNING;
    }

    public synchronized void close() {
        if (activeRecording != null) {
            activeRecording.close();
            activeRecording = null;
        }
    }
}
