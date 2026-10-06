package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.api.Rule;
import com.helix.core.RuleCompiler;
import com.helix.core.parser.RuleParser;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.sandbox.CapabilityPolicyEnforcer;
import com.helix.core.sandbox.DefaultCapabilityPolicy;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite measuring compilation overhead and AST capability policy enforcement
 * across trusted and untrusted/sandboxed rule execution paths.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Thread)
@Fork(0)
public class SandboxOverheadBenchmark {

    private RuleCompiler compiler;
    private RuleParser parser;
    private AstBuilder astBuilder;
    private CapabilityPolicyEnforcer enforcer;

    private String ruleJson;
    private Rule parsedRule;
    private ExpressionNode astRoot;

    @Setup
    public void setup() throws Exception {
        this.compiler = new RuleCompiler(RuleCompiler.GeneratorType.BYTE_BUDDY);
        this.parser = new RuleParser();
        this.astBuilder = new AstBuilder();
        this.enforcer = new CapabilityPolicyEnforcer(new DefaultCapabilityPolicy());

        this.ruleJson = """
                {
                    "name": "BenchmarkSecurityRule",
                    "expression": "(userAge >= 21 && creditScore > 650) || vipMember == true",
                    "inputSchema": {
                        "userAge": "integer",
                        "creditScore": "integer",
                        "vipMember": "boolean"
                    }
                }
                """;

        this.parsedRule = parser.parse(ruleJson);
        this.astRoot = astBuilder.buildAst(parsedRule.getExpression());
    }

    /**
     * Measures baseline compilation of a rule via standard trusted path.
     */
    @Benchmark
    public CompiledRule benchmarkTrustedCompilation() throws Exception {
        return compiler.compile(ruleJson);
    }

    /**
     * Measures compilation latency when enforcing AST capability policy (untrusted sandboxed path).
     */
    @Benchmark
    public CompiledRule benchmarkSandboxedCompilation() throws Exception {
        ExpressionNode ast = astBuilder.buildAst(parsedRule.getExpression());
        enforcer.enforce(ast);
        return compiler.compile(parsedRule);
    }

    /**
     * Measures pure AST capability policy traversal overhead (walk only).
     */
    @Benchmark
    public void benchmarkAstCapabilityWalkOnly() {
        enforcer.enforce(astRoot);
    }
}
