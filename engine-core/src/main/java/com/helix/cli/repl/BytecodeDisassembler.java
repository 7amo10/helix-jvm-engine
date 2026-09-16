package com.helix.cli.repl;

import com.helix.api.Rule;
import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.bytecode.AsmGenerator;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Disassembles compiled JVM bytecode into human-readable HotSpot assembly representation
 * matching {@code javap -v} output, powered by ASM's {@link TraceClassVisitor}.
 */
public class BytecodeDisassembler {

    private final AsmGenerator asmGenerator;
    private final ExpressionRuleParser expressionParser;

    public BytecodeDisassembler() {
        this.asmGenerator = new AsmGenerator();
        this.expressionParser = new ExpressionRuleParser();
    }

    /**
     * Disassembles raw JVM class bytecode into readable assembly output.
     *
     * @param classBytes compiled JVM class bytecode
     * @return disassembled textual bytecode matching javap format
     */
    public String disassemble(byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return "// No bytecode available to disassemble";
        }
        try {
            ClassReader reader = new ClassReader(classBytes);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintWriter pw = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
                TraceClassVisitor tracer = new TraceClassVisitor(null, new Textifier(), pw);
                reader.accept(tracer, ClassReader.EXPAND_FRAMES);
            }
            return baos.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "// Bytecode disassembly error: " + e.getMessage();
        }
    }

    /**
     * Compiles an expression using ASM and disassembles the generated class bytecode.
     *
     * @param expression rule expression text
     * @return disassembled textual bytecode
     */
    public String disassembleExpression(String expression) {
        try {
            ExpressionNode ast = expressionParser.parseAndFold(expression);
            Rule rule = new RuleNode("ReplRule", expression, Collections.emptyMap(), ast);
            byte[] bytecode = asmGenerator.generateBytecode(rule, ast);
            return disassemble(bytecode);
        } catch (Exception e) {
            return "// Failed to compile and disassemble expression: " + e.getMessage();
        }
    }

    /**
     * Compiles a RuleNode and disassembles the generated class bytecode.
     *
     * @param rule rule definition with AST
     * @return disassembled textual bytecode
     */
    public String disassembleRule(Rule rule, ExpressionNode ast) {
        try {
            byte[] bytecode = asmGenerator.generateBytecode(rule, ast);
            return disassemble(bytecode);
        } catch (Exception e) {
            return "// Failed to compile and disassemble rule: " + e.getMessage();
        }
    }

    /**
     * Colorizes disassembled bytecode lines for high-readability terminal presentation.
     *
     * @param rawDisassembly raw text from TraceClassVisitor
     * @return colorized ANSI string
     */
    public String colorize(String rawDisassembly) {
        if (rawDisassembly == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = rawDisassembly.split("\r?\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//")) {
                // Comments in dim / grey
                sb.append("\u001B[90m").append(line).append(TerminalRenderer.ANSI_RESET).append("\n");
            } else if (trimmed.startsWith("public") || trimmed.startsWith("private") || trimmed.startsWith("class")
                    || trimmed.startsWith("implements") || trimmed.startsWith("extends")) {
                // Class & member signatures in bold cyan
                sb.append(TerminalRenderer.ANSI_BOLD).append(TerminalRenderer.ANSI_CYAN).append(line).append(TerminalRenderer.ANSI_RESET).append("\n");
            } else if (trimmed.startsWith("L") && trimmed.contains(" ")) {
                // Labels in yellow
                sb.append(TerminalRenderer.ANSI_YELLOW).append(line).append(TerminalRenderer.ANSI_RESET).append("\n");
            } else if (trimmed.startsWith("LINENUMBER")) {
                // Line numbers in blue
                sb.append("\u001B[34m").append(line).append(TerminalRenderer.ANSI_RESET).append("\n");
            } else if (trimmed.startsWith("FRAME")) {
                // Stack map frames in magenta
                sb.append("\u001B[35m").append(line).append(TerminalRenderer.ANSI_RESET).append("\n");
            } else if (trimmed.startsWith("MAXSTACK") || trimmed.startsWith("MAXLOCALS")) {
                // Stack specs in bold yellow
                sb.append(TerminalRenderer.ANSI_BOLD).append(TerminalRenderer.ANSI_YELLOW).append(line).append(TerminalRenderer.ANSI_RESET).append("\n");
            } else if (!trimmed.isEmpty() && Character.isUpperCase(trimmed.charAt(0))) {
                // Bytecode opcodes (e.g., ALOAD, INVOKESTATIC, IADD) in green
                sb.append("    ").append(TerminalRenderer.ANSI_GREEN).append(trimmed).append(TerminalRenderer.ANSI_RESET).append("\n");
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
