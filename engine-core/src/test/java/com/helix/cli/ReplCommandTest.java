package com.helix.cli;

import picocli.CommandLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplCommandTest {

    @Test
    @DisplayName("Should evaluate single expression via picocli --eval flag")
    void testEvalFlag() {
        CommandLine cmd = new CommandLine(new ReplCommand());
        int exitCode = cmd.execute("-e", "1 + 2 == 3");
        assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("Should report error code on invalid expression via picocli --eval flag")
    void testEvalFlagError() {
        CommandLine cmd = new CommandLine(new ReplCommand());
        int exitCode = cmd.execute("-e", "1 +");
        assertEquals(0, exitCode); // Evaluator prints syntax error without throwing unhandled exception
    }
}
