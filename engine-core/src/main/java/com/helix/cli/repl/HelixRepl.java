package com.helix.cli.repl;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.bytecode.AstEvaluator;
import com.helix.core.bytecode.BytecodeCompiler;
import com.helix.core.debug.ConditionClause;
import com.helix.core.debug.DebugHook;
import com.helix.core.debug.DebugSession;
import com.helix.core.debug.EvaluationFrame;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private final BytecodeCompiler bytecodeCompiler = new BytecodeCompiler();
    private DebugSession debugSession = new DebugSession();
    private boolean debugMode = false;
    private ExecutorService debugExecutor;
    private Future<ExecutionResult> activeExecution;

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
            case ":debug" -> handleDebug(arg);
            case ":break" -> handleBreak(arg);
            case ":step" -> handleStep();
            case ":inspect" -> handleInspect(arg);
            case ":continue", ":c" -> handleContinue();
            case ":exit", ":quit", ":q" -> running = false;
            default -> out.println(TerminalRenderer.ANSI_RED + "Unknown command: '" + cmd + "'. Type ':help' for available commands." + TerminalRenderer.ANSI_RESET);
        }
    }

    private void evaluateExpression(String expr) {
        try {
            long parseStart = System.nanoTime();
            ExpressionNode folded = parser.parseAndFold(expr);
            long parseDuration = System.nanoTime() - parseStart;

            this.lastAst = folded;
            this.lastExpression = expr;

            boolean isDebugRun = debugMode || !debugSession.getBreakpoints().isEmpty() || debugSession.isStepMode();

            if (!isDebugRun) {
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
                return;
            }

            // Debug evaluation path
            List<ConditionClause> clauses = bytecodeCompiler.extractClauses(folded);
            debugSession.setClauses(clauses);
            DebugHook.setActiveSession(debugSession);

            Rule rule = new RuleNode("ReplDebugRule", expr, Collections.emptyMap(), folded);
            CompiledRule compiledRule = bytecodeCompiler.compile(rule, folded, true);

            if (activeExecution != null && !activeExecution.isDone()) {
                debugSession.continueExecution();
            }

            long initialPauseCount = debugSession.getPauseCount();
            activeExecution = getDebugExecutor().submit(() -> compiledRule.execute(context));
            waitForPauseOrCompletion("Hit breakpoint at", initialPauseCount);

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
        String rawDisassembly = disassembler.disassembleExpression(targetExpr, debugMode);
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

    private void handleDebug(String arg) {
        if ("on".equalsIgnoreCase(arg)) {
            debugMode = true;
        } else if ("off".equalsIgnoreCase(arg)) {
            debugMode = false;
        } else if (arg.isEmpty()) {
            debugMode = !debugMode;
        } else {
            out.println(TerminalRenderer.ANSI_YELLOW + "Usage: :debug [on|off]" + TerminalRenderer.ANSI_RESET);
            return;
        }
        if (debugMode) {
            out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_GREEN + "[DEBUG] Debug mode ENABLED." + TerminalRenderer.ANSI_RESET
                    + " Dynamic ASM bytecode probe hooks active.");
        } else {
            out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_YELLOW + "[DEBUG] Debug mode DISABLED." + TerminalRenderer.ANSI_RESET
                    + " Zero-overhead standard compilation active.");
        }
    }

    private void handleBreak(String arg) {
        if ("clear".equalsIgnoreCase(arg)) {
            debugSession.clearAllBreakpoints();
            out.println(TerminalRenderer.ANSI_GREEN + "[DEBUG] All breakpoints cleared." + TerminalRenderer.ANSI_RESET);
            return;
        }

        if (arg.isEmpty() || "list".equalsIgnoreCase(arg)) {
            List<ConditionClause> clauses = debugSession.getClauses();
            if (clauses.isEmpty() && lastAst != null) {
                clauses = bytecodeCompiler.extractClauses(lastAst);
                debugSession.setClauses(clauses);
            }

            if (clauses.isEmpty()) {
                out.println(TerminalRenderer.ANSI_YELLOW + "No condition clauses identified in current expression." + TerminalRenderer.ANSI_RESET);
                if (!debugSession.getBreakpoints().isEmpty()) {
                    out.println("Active breakpoint indices: " + debugSession.getBreakpoints());
                }
                return;
            }

            out.println(TerminalRenderer.ANSI_BOLD + "+-------+-------+-----------------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
            out.println(TerminalRenderer.ANSI_BOLD + "| Index | Break | AST Condition Clause                                      |" + TerminalRenderer.ANSI_RESET);
            out.println(TerminalRenderer.ANSI_BOLD + "+-------+-------+-----------------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
            for (ConditionClause clause : clauses) {
                boolean bp = debugSession.hasBreakpoint(clause.getIndex());
                String bpMark = bp ? (TerminalRenderer.ANSI_RED + " [*] " + TerminalRenderer.ANSI_RESET) : " [ ] ";
                String desc = clause.getDescription();
                if (desc.length() > 55) desc = desc.substring(0, 52) + "...";
                out.printf("| %-5d | %s | %-57s |%n", clause.getIndex(), bpMark, desc);
            }
            out.println(TerminalRenderer.ANSI_BOLD + "+-------+-------+-----------------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
            return;
        }

        try {
            int index = Integer.parseInt(arg);
            if (debugSession.hasBreakpoint(index)) {
                debugSession.clearBreakpoint(index);
                out.println(TerminalRenderer.ANSI_YELLOW + "[DEBUG] Breakpoint removed from clause [" + index + "]." + TerminalRenderer.ANSI_RESET);
            } else {
                debugSession.setBreakpoint(index);
                out.println(TerminalRenderer.ANSI_GREEN + "[DEBUG] Breakpoint set at clause [" + index + "]." + TerminalRenderer.ANSI_RESET);
            }
        } catch (NumberFormatException e) {
            out.println(TerminalRenderer.ANSI_YELLOW + "Usage: :break [clause-index | list | clear]" + TerminalRenderer.ANSI_RESET);
        }
    }

    private void handleStep() {
        if (!debugSession.isPaused()) {
            debugSession.setStepMode(true);
            out.println(TerminalRenderer.ANSI_YELLOW + "[DEBUG] Single-step mode armed for next evaluation." + TerminalRenderer.ANSI_RESET);
            return;
        }

        long initialPauseCount = debugSession.getPauseCount();
        debugSession.step();
        waitForPauseOrCompletion("Stepped to", initialPauseCount);
    }

    private void handleContinue() {
        if (!debugSession.isPaused()) {
            out.println(TerminalRenderer.ANSI_YELLOW + "[DEBUG] Engine is not currently paused at a breakpoint." + TerminalRenderer.ANSI_RESET);
            return;
        }

        long initialPauseCount = debugSession.getPauseCount();
        debugSession.continueExecution();
        waitForPauseOrCompletion("Hit breakpoint at", initialPauseCount);
    }

    private void handleInspect(String arg) {
        boolean showAll = "all".equalsIgnoreCase(arg);

        if (showAll) {
            List<EvaluationFrame> history = debugSession.getHistory();
            if (history.isEmpty()) {
                out.println(TerminalRenderer.ANSI_YELLOW + "[DEBUG] No evaluation frames recorded yet." + TerminalRenderer.ANSI_RESET);
                return;
            }
            out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_CYAN + "=== Helix Evaluation Frame History (" + history.size() + " frames) ===" + TerminalRenderer.ANSI_RESET);
            for (EvaluationFrame f : history) {
                renderSingleFrame(f);
            }
            return;
        }

        EvaluationFrame frame = debugSession.getCurrentFrame();
        if (frame == null) {
            List<EvaluationFrame> history = debugSession.getHistory();
            if (!history.isEmpty()) {
                frame = history.get(history.size() - 1);
            }
        }

        if (frame == null) {
            out.println(TerminalRenderer.ANSI_YELLOW + "[DEBUG] No active frame or history available to inspect. Run an expression in :debug mode or hit a breakpoint." + TerminalRenderer.ANSI_RESET);
            return;
        }

        out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_CYAN + "=== Helix ASM Frame Stack Inspector ===" + TerminalRenderer.ANSI_RESET);
        renderSingleFrame(frame);
    }

    private void renderSingleFrame(EvaluationFrame frame) {
        String leftType = frame.getLeftValue() != null ? frame.getLeftValue().getClass().getSimpleName() : "null";
        String rightType = frame.getRightValue() != null ? frame.getRightValue().getClass().getSimpleName() : "null";
        String outcomeStr = frame.isOutcome() ? (TerminalRenderer.ANSI_GREEN + "true (BRANCH TAKEN)" + TerminalRenderer.ANSI_RESET)
                : (TerminalRenderer.ANSI_RED + "false (FALLTHROUGH)" + TerminalRenderer.ANSI_RESET);

        out.println(TerminalRenderer.ANSI_BOLD + "+-------------------------------------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
        out.printf("| Condition Clause Index : %-60s |%n", "[" + frame.getClauseIndex() + "]");
        out.printf("| AST Expression         : %-60s |%n", frame.getDescription());
        out.printf("| Operator               : %-60s |%n", frame.getOperator());
        out.printf("| Left Operand           : %-60s |%n", frame.getLeftValue() + " (" + leftType + ")");
        out.printf("| Right Operand          : %-60s |%n", frame.getRightValue() + " (" + rightType + ")");
        out.printf("| Boolean Outcome        : %-70s |%n", outcomeStr);
        out.println(TerminalRenderer.ANSI_BOLD + "+-------------------------------------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
        out.println(TerminalRenderer.ANSI_BOLD + "| Local Variables Snapshot (Context at Frame Evaluation)                        |" + TerminalRenderer.ANSI_RESET);
        out.println(TerminalRenderer.ANSI_BOLD + "+-------------------------------------------------------------------------------+" + TerminalRenderer.ANSI_RESET);

        Map<String, Object> vars = frame.getVariables();
        if (vars.isEmpty()) {
            out.println("| (No variables in context)                                                     |");
        } else {
            for (Map.Entry<String, Object> e : vars.entrySet()) {
                String valStr = String.valueOf(e.getValue());
                String vType = e.getValue() != null ? e.getValue().getClass().getSimpleName() : "null";
                String entryStr = e.getKey() + " = " + valStr + " (" + vType + ")";
                if (entryStr.length() > 77) entryStr = entryStr.substring(0, 74) + "...";
                out.printf("| %-77s |%n", entryStr);
            }
        }
        out.println(TerminalRenderer.ANSI_BOLD + "+-------------------------------------------------------------------------------+" + TerminalRenderer.ANSI_RESET);
    }

    private void waitForPauseOrCompletion(String actionName, long initialPauseCount) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (debugSession.isPaused() && debugSession.getPauseCount() > initialPauseCount) {
                EvaluationFrame frame = debugSession.getCurrentFrame();
                if (frame != null) {
                    printFrameBanner(actionName, frame);
                }
                return;
            }
            if (activeExecution != null && activeExecution.isDone()) {
                try {
                    ExecutionResult res = activeExecution.get();
                    Object val = res.getResult().orElse(null);
                    this.lastResult = val;
                    context.setVariable("_", val);
                    context.setVariable("$it", val);
                    String typeName = (val != null) ? val.getClass().getSimpleName() : "null";
                    double evalMicros = res.getExecutionTimeNanos() / 1000.0;
                    out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_GREEN + "=> " + val + TerminalRenderer.ANSI_RESET
                            + "  \u001B[90m(" + typeName + ") [" + String.format("%.2f", evalMicros) + " µs debug eval]\u001B[0m");
                } catch (Exception e) {
                    out.println(TerminalRenderer.ANSI_RED + "[ERROR] Evaluation error: " + e.getMessage() + TerminalRenderer.ANSI_RESET);
                }
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {}
        }
        if (debugSession.isPaused() && debugSession.getPauseCount() > initialPauseCount) {
            EvaluationFrame frame = debugSession.getCurrentFrame();
            if (frame != null) {
                printFrameBanner(actionName, frame);
            }
        }
    }

    private void printFrameBanner(String action, EvaluationFrame frame) {
        out.println(TerminalRenderer.ANSI_BOLD + TerminalRenderer.ANSI_YELLOW + "[DEBUG] " + action + " clause ["
                + frame.getClauseIndex() + "]: " + TerminalRenderer.ANSI_CYAN + frame.getDescription() + TerminalRenderer.ANSI_RESET);
        out.println("        Left: " + TerminalRenderer.ANSI_BOLD + frame.getLeftValue() + TerminalRenderer.ANSI_RESET
                + "  " + frame.getOperator() + "  Right: " + TerminalRenderer.ANSI_BOLD + frame.getRightValue() + TerminalRenderer.ANSI_RESET
                + "  => Outcome: " + (frame.isOutcome() ? (TerminalRenderer.ANSI_GREEN + "true") : (TerminalRenderer.ANSI_RED + "false"))
                + TerminalRenderer.ANSI_RESET);
        out.println("\u001B[90mType :inspect to examine frame stack, :step to advance, :continue to resume.\u001B[0m");
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
          :debug [on|off]    Toggle or set ASM dynamic debug instrumentation mode
          :break [idx|clear] Set/toggle breakpoint on condition clause, list, or clear
          :step              Step to next condition clause and record frame state
          :inspect [all]     Inspect current evaluation frame, operands, and variables
          :continue / :c     Continue evaluation past breakpoints to completion
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

    private synchronized ExecutorService getDebugExecutor() {
        if (debugExecutor == null || debugExecutor.isShutdown()) {
            debugExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Helix-Repl-Debug-Worker");
                t.setDaemon(true);
                return t;
            });
        }
        return debugExecutor;
    }

    public void close() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception ignored) {}
        }
        if (debugSession != null) {
            debugSession.close();
        }
        if (debugExecutor != null) {
            debugExecutor.shutdownNow();
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

    public DebugSession getDebugSession() {
        return debugSession;
    }

    public void setDebugSession(DebugSession debugSession) {
        this.debugSession = debugSession;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
}
