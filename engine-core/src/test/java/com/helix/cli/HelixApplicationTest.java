package com.helix.cli;

import com.helix.HelixApplication;
import com.helix.cli.output.OutputFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CLI Application Unit & Integration Tests")
class HelixApplicationTest {

    @Test
    @DisplayName("1. Verify Main Helix Application Help Menu")
    void testHelixApplicationMainHelp() {
        CommandLine cmd = new CommandLine(new HelixApplication());
        int exitCode = cmd.execute("--help");
        assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("2. Verify Experiment Command Subcommand")
    void testExperimentCommand() {
        CommandLine cmd = new CommandLine(new HelixApplication());
        int exitCode = cmd.execute("experiment", "--name", "jit", "--output", "json");
        assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("3. Verify Output Formatter: Text, JSON, CSV")
    void testOutputFormatter() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "SUCCESS");
        data.put("key", "val");

        OutputFormatter textFormatter = new OutputFormatter("text", false);
        String textOutput = textFormatter.formatResult("Test Title", data);
        assertTrue(textOutput.contains("Test Title"));
        assertTrue(textOutput.contains("val"));

        OutputFormatter jsonFormatter = new OutputFormatter("json", false);
        String jsonOutput = jsonFormatter.formatResult("Test Title", data);
        assertTrue(jsonOutput.contains("\"status\" : \"SUCCESS\""));

        OutputFormatter csvFormatter = new OutputFormatter("csv", false);
        String csvOutput = csvFormatter.formatResult("Test Title", data);
        assertTrue(csvOutput.contains("Key,Value"));
        assertTrue(csvOutput.contains("\"status\",\"SUCCESS\""));
    }
}
