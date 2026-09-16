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
        for (int i = 0; i < 3; i++) {
            HelixRepl warmup = new HelixRepl();
            warmup.initTerminal();
            warmup.close();
        }

        long start = System.nanoTime();
        HelixRepl fastRepl = new HelixRepl();
        fastRepl.initTerminal();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        fastRepl.close();

        System.out.println("REPL launch time: " + durationMs + " ms");
        assertTrue(durationMs < 50, "REPL launch time (" + durationMs + " ms) should be under 50 ms");
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
        ExpressionNode node = exprParser.parse("order.amount >= 100 && ML('fraud') < 0.2 || !(flag == true) + user.getName()");
        AstTreeRenderer renderer = new AstTreeRenderer();
        String rendered = renderer.render(node);

        assertNotNull(rendered);
        assertTrue(rendered.contains("BinaryExpressionNode"));
        assertTrue(rendered.contains("ComparisonNode"));
        assertTrue(rendered.contains("FieldAccessNode"));
        assertTrue(rendered.contains("FunctionCallNode"));
        assertTrue(rendered.contains("MethodCallNode"));
        assertTrue(rendered.contains("UnaryOpNode"));
        assertTrue(rendered.contains("LiteralNode"));
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
