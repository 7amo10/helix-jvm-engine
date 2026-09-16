package com.helix.cli;

import com.helix.cli.repl.HelixRepl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

/**
 * Picocli command to launch the interactive JLine 3 Terminal REPL and Bytecode Disassembler.
 */
@Command(
        name = "repl",
        description = "Launch interactive JLine 3 Terminal REPL and Bytecode Disassembler",
        mixinStandardHelpOptions = true
)
public class ReplCommand implements CliCommand {

    @Option(names = {"-l", "--load"}, description = "Path to JSON rule or expression file to load into session")
    private File loadFile;

    @Option(names = {"-e", "--eval"}, description = "Evaluate single expression and print result directly without entering interactive mode")
    private String evalExpr;

    @Override
    public Integer call() {
        HelixRepl repl = new HelixRepl();
        if (evalExpr != null && !evalExpr.isBlank()) {
            return repl.evalSingle(evalExpr);
        }
        if (loadFile != null) {
            repl.loadInitialFile(loadFile);
        }
        return repl.start();
    }
}
