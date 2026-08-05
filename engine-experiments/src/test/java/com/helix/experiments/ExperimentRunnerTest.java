package com.helix.experiments;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentRunnerTest {

    @Test
    void testExecuteIndividualCommands() {
        assertEquals(0, ExperimentRunner.executeCommand("metaspace"));
        assertEquals(0, ExperimentRunner.executeCommand("jit"));
        assertEquals(0, ExperimentRunner.executeCommand("gc"));
        assertEquals(0, ExperimentRunner.executeCommand("layout"));
        assertEquals(0, ExperimentRunner.executeCommand("safepoint"));
    }

    @Test
    void testExecuteAllCommand() {
        assertEquals(0, ExperimentRunner.executeCommand("all"));
    }

    @Test
    void testUnknownCommand() {
        assertEquals(1, ExperimentRunner.executeCommand("nonexistent-command"));
    }
}
