package com.helix.cli.repl;

import com.helix.api.ExecutionContext;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-completer for the Helix REPL.
 * Provides context-aware completions for REPL commands, built-in functions, and active variables.
 */
public class HelixReplCompleter implements Completer {

    private final ExecutionContext context;

    private static final List<String> COMMANDS = List.of(
            ":help", ":eval", ":set", ":context", ":vars", ":ast", ":bytecode", ":perf",
            ":load", ":clear", ":exit", ":quit", ":break", ":step", ":inspect", ":continue", ":debug"
    );

    private static final List<String> BUILTINS = List.of(
            "ML(\"fraud\")", "ML(\"risk\")", "len(", "max(", "min(", "abs(", "now()"
    );

    public HelixReplCompleter(ExecutionContext context) {
        this.context = context;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String word = line.word();
        String buffer = line.line();

        if (buffer.startsWith(":") || line.wordIndex() == 0 && word.startsWith(":")) {
            for (String cmd : COMMANDS) {
                if (cmd.startsWith(word.toLowerCase())) {
                    candidates.add(new Candidate(cmd, cmd, null, "REPL Command", null, null, true));
                }
            }
            return;
        }

        // Builtin functions
        for (String fn : BUILTINS) {
            if (fn.toLowerCase().startsWith(word.toLowerCase())) {
                candidates.add(new Candidate(fn, fn, null, "Function", null, null, false));
            }
        }

        // Context variables
        if (context != null) {
            for (String varName : context.getVariables().keySet()) {
                if (varName.toLowerCase().startsWith(word.toLowerCase())) {
                    candidates.add(new Candidate(varName, varName, null, "Variable", null, null, true));
                }
            }
        }
    }
}
