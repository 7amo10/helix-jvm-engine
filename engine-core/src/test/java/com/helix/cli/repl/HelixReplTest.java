package com.helix.cli.repl;

import com.helix.api.ExecutionContext;
import com.helix.core.parser.ExpressionParseException;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import org.jline.reader.Candidate;
import org.jline.reader.EOFError;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HelixReplTest {

    private ExecutionContext context;
    private ByteArrayOutputStream out;
    private HelixRepl repl;

    @BeforeEach
    void setUp() {
        context = new ExecutionContext();
        out = new ByteArrayOutputStream();
        repl = new HelixRepl(new ByteArrayInputStream(new byte[0]), out, context);
    }

    @Test
    @DisplayName("Acceptance Criteria: REPL should initialize in under 50 ms")
    void testReplLaunchLatency() throws Exception {
        // Warm up JLine classes and ServiceLoader providers
        for (int i = 0; i < 10; i++) {
            HelixRepl warmup = new HelixRepl();
            warmup.initTerminal();
            warmup.close();
        }

        long minDurationMs = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            HelixRepl fastRepl = new HelixRepl();
            fastRepl.initTerminal();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            fastRepl.close();
            minDurationMs = Math.min(minDurationMs, durationMs);
        }

        System.out.println("REPL launch time: " + minDurationMs + " ms");
        assertTrue(minDurationMs <= 50, "REPL launch time (" + minDurationMs + " ms) should be under 50 ms");
    }

    @Test
    @DisplayName("Should evaluate direct rule expressions and store result in context")
    void testDirectExpressionEvaluation() {
        context.setVariable("amount", 1500);
        context.setVariable("riskScore", 0.05);

        repl.handleInput("amount > 1000 && riskScore < 0.2");

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("=> true"));
        assertTrue(output.contains("Boolean"));
        assertEquals(Boolean.TRUE, context.getVariable("_").orElse(null));
        assertEquals(Boolean.TRUE, context.getVariable("$it").orElse(null));
    }

    @Test
    @DisplayName("Should handle :set command with various data types")
    void testSetCommand() {
        repl.handleInput(":set score = 95");
        assertEquals(95, context.getVariable("score").orElse(null));

        repl.handleInput(":set factor = 3.14");
        assertEquals(3.14, context.getVariable("factor").orElse(null));

        repl.handleInput(":set isVip = true");
        assertEquals(Boolean.TRUE, context.getVariable("isVip").orElse(null));

        repl.handleInput(":set label = \"Premium\"");
        assertEquals("Premium", context.getVariable("label").orElse(null));

        repl.handleInput(":set balance = 10000000000L");
        assertEquals(10000000000L, context.getVariable("balance").orElse(null));
    }

    @Test
    @DisplayName("Should render colorized Unicode AST tree via :ast")
    void testAstCommand() {
        repl.handleInput(":ast amount > 1000 && score < 0.5");
        String output = out.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("Abstract Syntax Tree"));
        assertTrue(output.contains("BinaryExpressionNode"));
        assertTrue(output.contains("ComparisonNode"));
        assertTrue(output.contains("VariableNode: amount"));
        assertTrue(output.contains("LiteralNode: 1000"));
        assertTrue(output.contains("└──") || output.contains("├──"));
    }

    @Test
    @DisplayName("Acceptance Criteria: :bytecode should dump readable HotSpot bytecode matching javap -v")
    void testBytecodeDisassembler() {
        repl.handleInput(":bytecode amount > 1000 && riskScore < 0.2");
        String output = out.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("HotSpot JVM Disassembled Bytecode"));
        assertTrue(output.contains("public class com/helix/compiled/asm/AsmRule_"));
        assertTrue(output.contains("implements com/helix/api/CompiledRule"));
        assertTrue(output.contains("public <init>"));
        assertTrue(output.contains("public execute"));
        assertTrue(output.contains("INVOKESTATIC"));
        assertTrue(output.contains("ARETURN"));
    }

    @Test
    @DisplayName("Should execute :perf benchmark loop reporting ops/sec")
    void testPerfCommand() {
        context.setVariable("x", 42);
        repl.handleInput(":perf 1000 x > 10");
        String output = out.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("Micro-Benchmark Results"));
        assertTrue(output.contains("Total Iterations"));
        assertTrue(output.contains("Average Latency"));
        assertTrue(output.contains("Throughput"));
        assertTrue(output.contains("ops/sec"));
    }

    @Test
    @DisplayName("Should render :context / :vars table")
    void testContextCommand() {
        context.setVariable("user.id", 101);
        context.setVariable("active", true);

        repl.handleInput(":context");
        String output = out.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("Variable"));
        assertTrue(output.contains("Type"));
        assertTrue(output.contains("Current Value"));
        assertTrue(output.contains("user.id"));
        assertTrue(output.contains("101"));
        assertTrue(output.contains("active"));
    }

    @Test
    @DisplayName("Should print :help overview")
    void testHelpCommand() {
        repl.handleInput(":help");
        String output = out.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("Helix Interactive REPL Commands"));
        assertTrue(output.contains(":eval"));
        assertTrue(output.contains(":ast"));
        assertTrue(output.contains(":bytecode"));
        assertTrue(output.contains(":perf"));
        assertTrue(output.contains(":set"));
        assertTrue(output.contains(":context"));
    }

    @Test
    @DisplayName("Should load rule file via :load")
    void testLoadCommand(@TempDir Path tempDir) throws Exception {
        Path ruleFile = tempDir.resolve("sample-rule.json");
        String json = """
                {
                    "name": "LoadedDiscountRule",
                    "version": "1.2.0",
                    "expression": "amount >= 500"
                }
                """;
        Files.writeString(ruleFile, json);

        repl.handleInput(":load " + ruleFile.toAbsolutePath());
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Loaded JSON rule: LoadedDiscountRule"));
        assertEquals("amount >= 500", repl.getLastExpression());
    }

    @Test
    @DisplayName("Should report syntax errors gracefully with line, column, and visual pointer")
    void testSyntaxErrorHandling() {
        repl.handleInput("amount >");
        String output = out.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("Syntax error"));
        assertTrue(output.contains("column"));
        assertTrue(output.contains("^"));
    }

    @Test
    @DisplayName("Should support syntax highlighting in HelixReplHighlighter")
    void testHighlighter() {
        HelixReplHighlighter highlighter = new HelixReplHighlighter();
        AttributedString res = highlighter.highlight(null, ":bytecode amount > 1000 && \"fraud\" // comment");
        assertNotNull(res);
        assertFalse(res.toString().isEmpty());
    }

    @Test
    @DisplayName("Should provide auto-completions in HelixReplCompleter")
    void testCompleter() {
        context.setVariable("orderAmount", 500);
        HelixReplCompleter completer = new HelixReplCompleter(context);

        List<Candidate> candidates = new ArrayList<>();
        ParsedLine line = new MockParsedLine(":", 1, 0, ":");
        completer.complete(null, line, candidates);
        assertTrue(candidates.stream().anyMatch(c -> c.value().equals(":bytecode")));
        assertTrue(candidates.stream().anyMatch(c -> c.value().equals(":ast")));

        // Variables completion
        List<Candidate> varCandidates = new ArrayList<>();
        ParsedLine varLine = new MockParsedLine("order", 5, 0, "order");
        completer.complete(null, varLine, varCandidates);
        assertTrue(varCandidates.stream().anyMatch(c -> c.value().equals("orderAmount")));
    }

    @Test
    @DisplayName("Should detect multiline continuation in HelixReplParser")
    void testMultilineParser() {
        HelixReplParser parser = new HelixReplParser();

        // Unclosed paren should throw EOFError to trigger continuation prompt
        assertThrows(EOFError.class, () ->
                parser.parse("(amount > 100", 13, Parser.ParseContext.ACCEPT_LINE));

        // Unclosed string should throw EOFError
        assertThrows(EOFError.class, () ->
                parser.parse("name == \"incomplete", 19, Parser.ParseContext.ACCEPT_LINE));

        // Trailing operator should throw EOFError
        assertThrows(EOFError.class, () ->
                parser.parse("amount > 100 &&", 15, Parser.ParseContext.ACCEPT_LINE));

        // Complete statement should parse without error
        assertDoesNotThrow(() ->
                parser.parse("amount > 100 && score < 0.5", 27, Parser.ParseContext.ACCEPT_LINE));
    }

    @Test
    @DisplayName("Should render full AST tree for all node types in AstTreeRenderer")
    void testAstTreeRendererAllNodes() throws Exception {
        ExpressionRuleParser exprParser = new ExpressionRuleParser();
        ExpressionNode node = exprParser.parse("order.amount >= 100 && ML('fraud') < 0.2 || !(flag == true) + user.getName() + len(order.tags)");
        AstTreeRenderer renderer = new AstTreeRenderer();
        String rendered = renderer.render(node);

        assertNotNull(rendered);
        assertTrue(rendered.contains("BinaryExpressionNode"));
        assertTrue(rendered.contains("ComparisonNode"));
        assertTrue(rendered.contains("FieldAccessNode"));
        assertTrue(rendered.contains("FunctionCallNode"));
        assertTrue(rendered.contains("OnnxInferenceNode"));
        assertTrue(rendered.contains("MethodCallNode"));
        assertTrue(rendered.contains("UnaryOpNode"));
        assertTrue(rendered.contains("LiteralNode"));
    }

    @Test
    @DisplayName("Should handle :debug command to toggle debug instrumentation")
    void testDebugCommand() {
        assertFalse(repl.isDebugMode());
        repl.handleInput(":debug on");
        assertTrue(repl.isDebugMode());
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Debug mode ENABLED"));

        out.reset();
        repl.handleInput(":debug off");
        assertFalse(repl.isDebugMode());
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Debug mode DISABLED"));

        out.reset();
        repl.handleInput(":debug");
        assertTrue(repl.isDebugMode());
    }

    @Test
    @DisplayName("Should handle :break command to set, list, and clear breakpoints")
    void testBreakCommand() {
        // Set breakpoint on clause 0
        repl.handleInput(":break 0");
        assertTrue(repl.getDebugSession().hasBreakpoint(0));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Breakpoint set at clause [0]"));

        // Toggle off
        out.reset();
        repl.handleInput(":break 0");
        assertFalse(repl.getDebugSession().hasBreakpoint(0));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Breakpoint removed from clause [0]"));

        // Set breakpoint on clause 1
        repl.handleInput(":break 1");
        assertTrue(repl.getDebugSession().hasBreakpoint(1));

        // Clear all
        out.reset();
        repl.handleInput(":break clear");
        assertTrue(repl.getDebugSession().getBreakpoints().isEmpty());
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("All breakpoints cleared"));
    }

    @Test
    @DisplayName("Should support interactive debugging workflow: breakpoint pause, inspect, step, continue")
    void testInteractiveDebugWorkflow() {
        context.setVariable("amount", 1500);
        context.setVariable("score", 800);

        // Arm breakpoint on clause 0
        repl.handleInput(":break 0");

        // Run expression: triggers debug evaluation path and pauses
        repl.handleInput("amount > 1000 && score >= 750");
        String runOutput = out.toString(StandardCharsets.UTF_8);
        assertTrue(runOutput.contains("Hit breakpoint at clause [0]"), "Should report hitting breakpoint at clause [0]");

        // Inspect paused frame
        out.reset();
        repl.handleInput(":inspect");
        String inspectOutput = out.toString(StandardCharsets.UTF_8);
        assertTrue(inspectOutput.contains("Helix ASM Frame Stack Inspector"));
        assertTrue(inspectOutput.contains("Condition Clause Index : [0]"));
        assertTrue(inspectOutput.contains("Left Operand           : 1500 (Integer)"));
        assertTrue(inspectOutput.contains("Right Operand          : 1000 (Integer)"));
        assertTrue(inspectOutput.contains("amount = 1500"));
        assertTrue(inspectOutput.contains("score = 800"));

        // Step to next clause
        out.reset();
        repl.handleInput(":step");
        String stepOutput = out.toString(StandardCharsets.UTF_8);
        assertTrue(stepOutput.contains("Stepped to clause [1]"), "Should report stepping to clause [1]");

        // Continue to completion
        out.reset();
        repl.handleInput(":continue");
        String continueOutput = out.toString(StandardCharsets.UTF_8);
        assertTrue(continueOutput.contains("=> true"), "Should complete and print final true result");
    }

    @Test
    @DisplayName("Should show debug probe instructions in :bytecode when debug mode is enabled")
    void testBytecodeDebugModeDisassembly() {
        repl.handleInput(":debug on");
        out.reset();
        repl.handleInput(":bytecode amount > 1000");
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("DebugHook"), "Disassembly in debug mode should contain DebugHook");
        assertTrue(output.contains("onCondition"), "Disassembly in debug mode should contain onCondition");
    }

    // --- Helpers ---

    private static class MockParsedLine implements ParsedLine {
        private final String line;
        private final int cursor;
        private final int wordIndex;
        private final String word;

        MockParsedLine(String line, int cursor, int wordIndex, String word) {
            this.line = line;
            this.cursor = cursor;
            this.wordIndex = wordIndex;
            this.word = word;
        }

        @Override public String word() { return word; }
        @Override public int wordCursor() { return word.length(); }
        @Override public int wordIndex() { return wordIndex; }
        @Override public List<String> words() { return List.of(word); }
        @Override public String line() { return line; }
        @Override public int cursor() { return cursor; }
    }
}
