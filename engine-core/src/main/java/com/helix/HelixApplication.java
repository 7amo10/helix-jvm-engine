package com.helix;

import com.helix.cli.CompileCommand;
import com.helix.cli.ExecuteCommand;
import com.helix.cli.ExperimentCommand;
import com.helix.cli.ProfileCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main application entrypoint for the Helix JVM Scripting Engine &amp; Profiler CLI/TUI.
 */
@Command(
        name = "helix",
        description = "Production-grade JVM Scripting Engine & Deep Profiling Platform",
        mixinStandardHelpOptions = true,
        version = "Helix v1.0.0-SNAPSHOT",
        subcommands = {
                CompileCommand.class,
                ExecuteCommand.class,
                ProfileCommand.class,
                ExperimentCommand.class
        }
)
public class HelixApplication implements Runnable {

    @Override
    public void run() {
        System.out.println("""
                ===============================================================
                       Helix JVM Scripting Engine & Profiler (v1.0.0)
                ===============================================================
                Use 'helix --help' or 'helix <subcommand> --help' for usage options.

                Available Subcommands:
                  compile     Compile JSON rules into JVM bytecode
                  execute     Compile and execute rules synchronously/async/batch
                  profile     Profile JIT/GC execution or launch TUI dashboard
                  experiment  Execute JVM behavior performance experiments
                ---------------------------------------------------------------
                """);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new HelixApplication()).execute(args);
        System.exit(exitCode);
    }
}
