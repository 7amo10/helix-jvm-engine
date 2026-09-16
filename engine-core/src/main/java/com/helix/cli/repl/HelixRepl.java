package com.helix.cli.repl;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.Rule;
import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.bytecode.AstEvaluator;
import com.helix.core.parser.ExpressionParseException;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.RuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Interactive JLine 3 Terminal REPL and HotSpot Bytecode Disassembler for Helix.
 */
public class HelixRepl {

    public static final String PROMPT = TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_CYAN + "helix> " + TerminalRenderer.ANSI_RESET;
    public static final String MULTILINE_PROMPT = TerminalRenderer.ANSI_BOLD + "\u001B[90m   ...> " + TerminalRenderer.ANSI_RESET;

    private final ExecutionContext context;
    private final ExpressionRuleParser parser;
    private final AstTreeRenderer treeRenderer;
    private final BytecodeDisassembler disassembler;
    private final RuleParser ruleParser;

    private Terminal terminal;
    private LineReader lineReader;
    private PrintWriter out;

    private ExpressionNode lastAst;
    private String lastExpression;
    private Object lastResult;
    private boolean running = true;

    private InputStream customIn;
    private OutputStream customOut;

    public HelixRepl() {
        this(new ExecutionContext());
    }

    public HelixRepl(ExecutionContext context) {
        this.context = context;
        this.parser = new ExpressionRuleParser();
        this.treeRenderer = new AstTreeRenderer();
        this.disassembler = new BytecodeDisassembler();
        this.ruleParser = new RuleParser();
        this.out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
    }

    public HelixRepl(InputStream in, OutputStream out, ExecutionContext context) {
        this(context);
        this.customIn = in;
        this.customOut = out;
        if (out != null) {
            this.out = new PrintWriter(out, true, StandardCharsets.UTF_8);
        }
    }

    /**
     * Initializes JLine 3 Terminal and LineReader with history and autocompletion.
     */
    public void initTerminal() throws Exception {
        TerminalBuilder builder = TerminalBuilder.builder();
        if (customIn != null && customOut != null) {
            builder.streams(customIn, customOut);
            builder.dumb(true);
        } else {
            builder.system(true);
            builder.dumb(true);
        }
        this.terminal = builder.build();
        this.out = terminal.writer();

        Path historyPath = Paths.get(System.getProperty("user.home"), ".helix", "history");
        try {
            if (historyPath.getParent() != null) {
                Files.createDirectories(historyPath.getParent());
            }
        } catch (Exception ignored) {}

        LineReaderBuilder readerBuilder = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new HelixReplCompleter(context))
                .highlighter(new HelixReplHighlighter())
                .parser(new HelixReplParser())
                .history(new DefaultHistory())
                .variable(LineReader.HISTORY_FILE, historyPath)
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, MULTILINE_PROMPT);

        this.lineReader = readerBuilder.build();
    }

    /**
     * Starts the interactive REPL event loop.
     *
     * @return exit code (0 for clean termination)
     */
    public int start() {
        try {
            if (terminal == null) {
                initTerminal();
            }
            printWelcomeBanner();

            while (running) {
                String line;
                try {
                    line = lineReader.readLine(PROMPT);
                } catch (UserInterruptException e) {
                    // Ctrl+C caught: cancel current line and prompt anew
                    out.println(TerminalRenderer.ANSI_YELLOW + "^C" + TerminalRenderer.ANSI_RESET);
                    out.flush();
                    continue;
                } catch (EndOfFileException e) {
                    // Ctrl+D caught: clean exit
                    out.println();
                    break;
                }

                if (line == null) {
                    break;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                handleInput(trimmed);
            }

            out.println(TerminalRenderer.ANSI_GREEN + "Farewell from Helix. Happy hacking!" + TerminalRenderer.ANSI_RESET);
            out.flush();
            return 0;
        } catch (Exception e) {
            if (out != null) {
                out.println(TerminalRenderer.ANSI_RED + "[FATAL] REPL crashed: " + e.getMessage() + TerminalRenderer.ANSI_RESET);
                out.flush();
            } else {
                System.err.println("[FATAL] REPL crashed: " + e.getMessage());
            }
            return 1;
        } finally {
            close();
        }
    }

    /**
     * Evaluates a single expression and prints the result, used for CLI non-interactive execution.
     */
    public int evalSingle(String expression) {
        try {
            if (out == null) {
                out = new PrintWriter(System.out, true);
            }
            evaluateExpression(expression);
            return 0;
        } catch (Exception e) {
            System.err.println(TerminalRenderer.ANSI_RED + "[ERROR] " + e.getMessage() + TerminalRenderer.ANSI_RESET);
            return 1;
        }
    }

    /**
     * Loads an initial rule file into the REPL.
     */
    public void loadInitialFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            String content = Files.readString(file.toPath());
            if (file.getName().endsWith(".json")) {
                Rule rule = ruleParser.parse(content);
                this.lastExpression = rule.getExpression();
                this.lastAst = parser.parseAndFold(rule.getExpression());
                if (out != null) {
                    out.println(TerminalRenderer.ANSI_GREEN + "[LOAD] Loaded JSON rule: " + rule.getName() + " (v" + rule.getVersion() + ")" + TerminalRenderer.ANSI_RESET);
                }
            } else {
                this.lastExpression = content.trim();
                this.lastAst = parser.parseAndFold(content);
                if (out != null) {
                    out.println(TerminalRenderer.ANSI_GREEN + "[LOAD] Loaded expression from " + file.getName() + TerminalRenderer.ANSI_RESET);
                }
            }
        } catch (Exception e) {
            if (out != null) {
                out.println(TerminalRenderer.ANSI_RED + "[ERROR] Failed to load file: " + e.getMessage() + TerminalRenderer.ANSI_RESET);
            }
        }
    }

    public void handleInput(String input) {
        if (input.startsWith(":")) {
            executeCommand(input);
        } else {
            evaluateExpression(input);
        }
        out.flush();
    }

    private void executeCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case ":help", ":h", ":?" -> printHelp();
            case ":eval", ":e" -> {
                if (arg.isEmpty()) {
                    out.println(TerminalRenderer.ANSI_YELLOW + "Usage: :eval <expression>" + TerminalRenderer.ANSI_RESET);
                } else {
                    evaluateExpression(arg);
                }
            }
            case ":ast" -> showAst(arg);
            case ":bytecode", ":bc", ":asm" -> showBytecode(arg);
            case ":perf" -> runBenchmark(arg);
            case ":set" -> setVariable(arg);
            case ":context", ":vars" -> showContext();
            case ":load", ":l" -> loadFile(arg);
            case ":clear", ":cls" -> clearScreen();
            case ":exit", ":quit", ":q" -> running = false;
            default -> out.println(TerminalRenderer.ANSI_RED + "Unknown command: '" + cmd + "'. Type ':help' for available commands." + TerminalRenderer.ANSI_RESET);
        }
    }

    private void evaluateExpression(String expr) {
        try {
            long parseStart = System.nanoTime();
            ExpressionNode ast = parser.parse(expr);
            ExpressionNode folded = parser.parseAndFold(expr);
            long parseDuration = System.nanoTime() - parseStart;

            this.lastAst = folded;
            this.lastExpression = expr;

            AstEvaluator evaluator = new AstEvaluator(context);
            long evalStart = System.nanoTime();
            Object result = folded.accept(evaluator);
            long evalDuration = System.nanoTime() - evalStart;

            this.lastResult = result;
            context.setVariable("_", result);
            context.setVariable("$it", result);

            String typeName = (result != null) ? result.getClass().getSimpleName() : "null";
            double evalMicros = evalDuration / 1000.0;
            double parseMicros = parseDuration / 1000.0;

            out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_GREEN + "=> " + result + TerminalRenderer.ANSI_RESET
                    + "  \u001B[90m(" + typeName + ") [" + String.format("%.2f", evalMicros) + " µs eval, "
                    + String.format("%.2f", parseMicros) + " µs parse]\u001B[0m");
        } catch (ExpressionParseException e) {
            out.println(TerminalRenderer.ANSI_RED + e.getMessage() + TerminalRenderer.ANSI_RESET);
        } catch (Exception e) {
            out.println(TerminalRenderer.ANSI_RED + "[ERROR] Evaluation failed: " + e.getMessage() + TerminalRenderer.ANSI_RESET);
        }
    }

    private void showAst(String expr) {
        ExpressionNode targetAst = lastAst;
        if (!expr.isEmpty()) {
            try {
                targetAst = parser.parseAndFold(expr);
                this.lastAst = targetAst;
                this.lastExpression = expr;
            } catch (Exception e) {
                out.println(TerminalRenderer.ANSI_RED + "[ERROR] " + e.getMessage() + TerminalRenderer.ANSI_RESET);
                return;
            }
        }
        if (targetAst == null) {
            out.println(TerminalRenderer.ANSI_YELLOW + "No AST available. Evaluate an expression first or specify one: :ast <expr>" + TerminalRenderer.ANSI_RESET);
            return;
        }

        out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_CYAN + "=== Abstract Syntax Tree (AST) ===" + TerminalRenderer.ANSI_RESET);
        out.println(treeRenderer.render(targetAst));
    }

    private void showBytecode(String expr) {
        String targetExpr = lastExpression;
        if (!expr.isEmpty()) {
            targetExpr = expr;
        }
        if (targetExpr == null || targetExpr.isBlank()) {
            out.println(TerminalRenderer.ANSI_YELLOW + "No expression available. Evaluate an expression first or specify one: :bytecode <expr>" + TerminalRenderer.ANSI_RESET);
            return;
        }

        out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_CYAN + "=== HotSpot JVM Disassembled Bytecode (javap -v format) ===" + TerminalRenderer.ANSI_RESET);
        String rawDisassembly = disassembler.disassembleExpression(targetExpr);
        out.println(disassembler.colorize(rawDisassembly));
    }

    private void runBenchmark(String args) {
        int iterations = 100_000;
        String expr = lastExpression;

        if (!args.isEmpty()) {
            String[] parts = args.split("\\s+", 2);
            try {
                iterations = Integer.parseInt(parts[0]);
                if (parts.length > 1) {
                    expr = parts[1].trim();
                }
            } catch (NumberFormatException e) {
                expr = args;
            }
        }

        if (expr == null || expr.isBlank()) {
            out.println(TerminalRenderer.ANSI_YELLOW + "No expression to benchmark. Usage: :perf [iterations] [expression]" + TerminalRenderer.ANSI_RESET);
            return;
        }

        try {
            ExpressionNode ast = parser.parseAndFold(expr);
            AstEvaluator evaluator = new AstEvaluator(context);

            out.println(TerminalRenderer.ANSI_CYAN + "[PERF] Warming up JIT compiler (5,000 iterations)..." + TerminalRenderer.ANSI_RESET);
            for (int i = 0; i < 5_000; i++) {
                ast.accept(evaluator);
            }

            out.println(TerminalRenderer.ANSI_CYAN + "[PERF] Benchmarking " + String.format("%,d", iterations) + " iterations for: " + expr + TerminalRenderer.ANSI_RESET);

            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                ast.accept(evaluator);
            }
            long totalNanos = System.nanoTime() - start;

            double totalMs = totalNanos / 1_000_000.0;
            double avgNanos = totalNanos / (double) iterations;
            double avgMicros = avgNanos / 1000.0;
            double opsPerSec = (iterations / (double) totalNanos) * 1_000_000_000.0;

            out.println(TerminalRenderer.ANSI_BOLD + "+---------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
            out.println(TerminalRenderer.ANSI_BOLD + "| Helix REPL Micro-Benchmark Results                |" + TerminalRenderer.ANSI_RESET);
            out.println(TerminalRenderer.ANSI_BOLD + "+---------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
            out.printf("| Total Iterations : %-30s |%n", String.format("%,d", iterations));
            out.printf("| Total Time       : %-30s |%n", String.format("%.3f ms", totalMs));
            out.printf("| Average Latency  : %-30s |%n", String.format("%.2f ns (%.3f µs)", avgNanos, avgMicros));
            out.printf("| Throughput       : %-30s |%n", String.format("%,.0f ops/sec", opsPerSec));
            out.println(TerminalRenderer.ANSI_BOLD + "+---------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
        } catch (Exception e) {
            out.println(TerminalRenderer.ANSI_RED + "[ERROR] Benchmark failed: " + e.getMessage() + TerminalRenderer.ANSI_RESET);
        }
    }

    private void setVariable(String arg) {
        if (arg.isEmpty()) {
            out.println(TerminalRenderer.ANSI_YELLOW + "Usage: :set <name>=<value> (e.g. :set amount=1500, :set user.age=28)" + TerminalRenderer.ANSI_RESET);
            return;
        }

        int eqIdx = arg.indexOf('=');
        String name;
        String valStr;

        if (eqIdx > 0) {
            name = arg.substring(0, eqIdx).trim();
            valStr = arg.substring(eqIdx + 1).trim();
        } else {
            String[] parts = arg.split("\\s+", 2);
            if (parts.length < 2) {
                out.println(TerminalRenderer.ANSI_YELLOW + "Usage: :set <name>=<value>" + TerminalRenderer.ANSI_RESET);
                return;
            }
            name = parts[0].trim();
            valStr = parts[1].trim();
        }

        Object val = parseValue(valStr);
        context.setVariable(name, val);
        String typeName = (val != null) ? val.getClass().getSimpleName() : "null";
        out.println(TerminalRenderer.ANSI_GREEN + "[OK] Set context variable: " + name + " = " + val + " (" + typeName + ")" + TerminalRenderer.ANSI_RESET);
    }

    private Object parseValue(String valStr) {
        if (valStr.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (valStr.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (valStr.equalsIgnoreCase("null")) return null;

        if ((valStr.startsWith("\"") && valStr.endsWith("\"")) || (valStr.startsWith("'") && valStr.endsWith("'"))) {
            return valStr.substring(1, valStr.length() - 1);
        }

        if (valStr.endsWith("L") || valStr.endsWith("l")) {
            try {
                return Long.parseLong(valStr.substring(0, valStr.length() - 1));
            } catch (NumberFormatException ignored) {}
        }

        try {
            if (valStr.contains(".")) {
                return Double.parseDouble(valStr);
            }
            return Integer.parseInt(valStr);
        } catch (NumberFormatException ignored) {}

        return valStr;
    }

    private void showContext() {
        Map<String, Object> vars = new TreeMap<>(context.getVariables());
        if (vars.isEmpty()) {
            out.println(TerminalRenderer.ANSI_YELLOW + "Execution context is empty. Set variables with: :set <name>=<value>" + TerminalRenderer.ANSI_RESET);
            return;
        }

        out.println(TerminalRenderer.ANSI_BOLD + "+-------------------------+--------------------+------------------------------+" + TerminalRenderer.ANSI_RESET);
        out.println(TerminalRenderer.ANSI_BOLD + "| Variable                | Type               | Current Value                |" + TerminalRenderer.ANSI_RESET);
        out.println(TerminalRenderer.ANSI_BOLD + "+-------------------------+--------------------+------------------------------+" + TerminalRenderer.ANSI_RESET);

        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            String name = entry.getKey();
            Object val = entry.getValue();
            String typeName = (val != null) ? val.getClass().getSimpleName() : "null";
            String valStr = String.valueOf(val);
            if (valStr.length() > 28) {
                valStr = valStr.substring(0, 25) + "...";
            }
            out.printf("| %-23s | %-18s | %-28s |%n", name, typeName, valStr);
        }
        out.println(TerminalRenderer.ANSI_BOLD + "+-------------------------+--------------------+------------------------------+" + TerminalRenderer.ANSI_RESET);
    }

    private void loadFile(String pathStr) {
        if (pathStr.isEmpty()) {
            out.println(TerminalRenderer.ANSI_YELLOW + "Usage: :load <file-path>" + TerminalRenderer.ANSI_RESET);
            return;
        }
        File file = new File(pathStr);
        if (!file.exists()) {
            out.println(TerminalRenderer.ANSI_RED + "[ERROR] File not found: " + file.getAbsolutePath() + TerminalRenderer.ANSI_RESET);
            return;
        }
        loadInitialFile(file);
    }

    private void clearScreen() {
        if (terminal != null) {
            terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
            terminal.flush();
        } else {
            out.print("\033[H\033[2J");
            out.flush();
        }
    }

    private void printWelcomeBanner() {
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        int processors = Runtime.getRuntime().availableProcessors();
        String jvmVersion = System.getProperty("java.version");
        String jvmVendor = System.getProperty("java.vendor");

        out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_CYAN + """
                 _   _      _ _       
                | | | | ___| (_)_  __ 
                | |_| |/ _ \\ | \\ \\/ / 
                |  _  |  __/ | |>  <  
                |_| |_|\\___|_|_/_/\\_\\ 
                """ + TerminalRenderer.ANSI_RESET);

        out.println(TerminalRenderer.ANSI_BOLD + "Helix JVM Scripting Engine & Profiler Interactive Shell (v1.0.0)" + TerminalRenderer.ANSI_RESET);
        out.println("\u001B[90mRunning on " + jvmVendor + " Java " + jvmVersion + " (" + processors + " CPUs, " + maxMem + "MB max heap)\u001B[0m");
        out.println("Type " + TerminalRenderer.ANSI_YELLOW + ":help" + TerminalRenderer.ANSI_RESET
                + " for commands overview, " + TerminalRenderer.ANSI_YELLOW + ":exit" + TerminalRenderer.ANSI_RESET + " to terminate session.\n");
        out.flush();
    }

    private void printHelp() {
        out.println("""
        Helix Interactive REPL Commands:
          <expression>       Directly parse, compile, and execute an expression
          :eval <expr>       Explicitly evaluate an expression and store in context
          :ast [expr]        Render colorized Unicode AST tree visualization
          :bytecode [expr]   Disassemble ASM bytecode matching javap -v
          :perf [n] [expr]   Benchmark throughput (default 100,000 iterations)
          :set <k>=<v>       Set context variable (e.g. :set amount=1500, :set user.age=25)
          :context / :vars   Display all currently active variables in context
          :load <path>       Load rule from JSON or expression file
          :clear             Clear terminal screen
          :help              Show this help cheat sheet
          :exit / :quit      Exit REPL session

        Grammar & Syntax Quick Reference:
          Comparisons:  >  <  >=  <=  ==  !=
          Logic:        &&  ||  !
          Arithmetic:   +  -  *  /  %
          Properties:   user.age, ctx.amount, order.customer.address.city
          Functions:    ML("fraud"), len(items), max(a, b), min(a, b), abs(x), now()
          Literals:     123, 1000L, 0.2, "string", 'string', true, false, null
          Last Result:  _  or  $it
        """);
        out.flush();
    }

    public void close() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception ignored) {}
        }
    }

    public ExecutionContext getContext() {
        return context;
    }

    public ExpressionNode getLastAst() {
        return lastAst;
    }

    public String getLastExpression() {
        return lastExpression;
    }

    public Object getLastResult() {
        return lastResult;
    }
}
