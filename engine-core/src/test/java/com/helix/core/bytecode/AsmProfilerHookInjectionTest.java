package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.profiler.node.AstNodeProfiler;
import com.helix.profiler.node.NodeStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AsmBytecodeGenerator Profiler Hook Injection Tests")
class AsmProfilerHookInjectionTest {

    @BeforeEach
    void setUp() {
        AstNodeProfiler.reset();
        AstNodeProfiler.setEnabled(true);
    }

    @Test
    @DisplayName("Should inject INVOKESTATIC AstNodeProfiler.recordEntry and recordExit opcodes when profiling is enabled")
    void testDisassembledBytecodeContainsProfilerHooks() throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        ExpressionNode ast = parser.parse("amount > 1000");
        Rule rule = new RuleNode("ProfileHookRule", "amount > 1000", Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator(true); // profiling enabled
        byte[] classBytes = generator.generateBytecode("com.helix.compiled.asm.ProfileHookRule", rule, ast);

        assertNotNull(classBytes);
        assertTrue(classBytes.length > 0);

        StringWriter sw = new StringWriter();
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(new TraceClassVisitor(new PrintWriter(sw)), 0);
        String disassembly = sw.toString();

        assertTrue(disassembly.contains("com/helix/profiler/node/AstNodeProfiler"),
                "Bytecode must contain references to AstNodeProfiler");
        assertTrue(disassembly.contains("recordEntry"),
                "Bytecode must invoke AstNodeProfiler.recordEntry");
        assertTrue(disassembly.contains("recordExit"),
                "Bytecode must invoke AstNodeProfiler.recordExit");
    }

    @Test
    @DisplayName("Should automatically record node statistics in AstNodeProfiler upon compiled rule execution")
    void testCompiledRuleUpdatesAstNodeProfiler() throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        ExpressionNode ast = parser.parse("amount > 1000");
        Rule rule = new RuleNode("AutoProfileRule", "amount > 1000", Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator(true);
        CompiledRule compiled = generator.generate(rule, ast);

        // Execute passing transaction ($2500)
        ExecutionContext ctxPass = new ExecutionContext(Map.of("amount", 2500.0));
        ExecutionResult res1 = compiled.execute(ctxPass);
        assertTrue(res1.isSuccess());
        assertEquals(Boolean.TRUE, res1.getResult().orElse(null));

        // Execute failing transaction ($500)
        ExecutionContext ctxFail = new ExecutionContext(Map.of("amount", 500.0));
        ExecutionResult res2 = compiled.execute(ctxFail);
        assertTrue(res2.isSuccess());
        assertEquals(Boolean.FALSE, res2.getResult().orElse(null));

        // Verify AstNodeProfiler stats
        Map<String, NodeStats> stats = AstNodeProfiler.getAllStats();
        assertFalse(stats.isEmpty(), "AstNodeProfiler must contain recorded node stats");

        long totalExecutions = stats.values().stream().mapToLong(NodeStats::getExecutionCount).sum();
        long totalFailures = stats.values().stream().mapToLong(NodeStats::getFailureCount).sum();

        assertTrue(totalExecutions >= 2L, "Should have at least 2 recorded executions");
        assertTrue(totalFailures >= 1L, "Should have at least 1 failure/short-circuit recorded");
    }

    @Test
    @DisplayName("Should omit profiler hooks when profiling is disabled to avoid overhead")
    void testOmitProfilerHooksWhenDisabled() throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        ExpressionNode ast = parser.parse("amount > 1000");
        Rule rule = new RuleNode("NoProfileHookRule", "amount > 1000", Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator(false); // profiling disabled
        byte[] classBytes = generator.generateBytecode("com.helix.compiled.asm.NoProfileHookRule", rule, ast);

        StringWriter sw = new StringWriter();
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(new TraceClassVisitor(new PrintWriter(sw)), 0);
        String disassembly = sw.toString();

        assertFalse(disassembly.contains("com/helix/profiler/node/AstNodeProfiler"),
                "Bytecode must NOT contain AstNodeProfiler references when disabled");
    }
}
