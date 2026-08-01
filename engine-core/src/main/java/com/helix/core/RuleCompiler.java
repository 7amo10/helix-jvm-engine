package com.helix.core;

import com.helix.api.CompiledRule;
import com.helix.api.Rule;
import com.helix.api.RuleCompilationException;
import com.helix.core.bytecode.AsmGenerator;
import com.helix.core.bytecode.ByteBuddyGenerator;
import com.helix.core.bytecode.BytecodeGenerator;
import com.helix.core.bytecode.BytecodeOptimizer;
import com.helix.core.parser.RuleParser;
import com.helix.core.parser.TypeChecker;
import com.helix.core.parser.TypeContext;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * End-to-end rule compiler orchestrating JSON parsing, AST construction, static type checking,
 * AST optimization, and dynamic bytecode generation.
 */
public class RuleCompiler {

    private static final Logger log = LoggerFactory.getLogger(RuleCompiler.class);

    public enum GeneratorType {
        BYTE_BUDDY,
        ASM
    }

    private final RuleParser ruleParser;
    private final AstBuilder astBuilder;
    private final BytecodeOptimizer bytecodeOptimizer;
    private final GeneratorType defaultGeneratorType;

    public RuleCompiler() {
        this(GeneratorType.BYTE_BUDDY);
    }

    public RuleCompiler(GeneratorType defaultGeneratorType) {
        this.ruleParser = new RuleParser();
        this.astBuilder = new AstBuilder();
        this.bytecodeOptimizer = new BytecodeOptimizer(true);
        this.defaultGeneratorType = Objects.requireNonNull(defaultGeneratorType, "defaultGeneratorType cannot be null");
    }

    /**
     * Compiles a raw JSON rule string into an executable {@link CompiledRule}.
     *
     * @param jsonRule JSON rule payload
     * @return compiled rule instance
     * @throws RuleCompilationException if compilation fails at any pipeline stage
     */
    public CompiledRule compile(String jsonRule) throws RuleCompilationException {
        return compile(jsonRule, defaultGeneratorType);
    }

    /**
     * Compiles a raw JSON rule string using a specified {@link GeneratorType}.
     *
     * @param jsonRule      JSON rule payload
     * @param generatorType bytecode generator strategy
     * @return compiled rule instance
     * @throws RuleCompilationException if compilation fails at any stage
     */
    public CompiledRule compile(String jsonRule, GeneratorType generatorType) throws RuleCompilationException {
        long parseStart = System.nanoTime();
        Rule rule;
        try {
            rule = ruleParser.parse(jsonRule);
        } catch (Exception e) {
            throw new RuleCompilationException("Rule compilation failed during JSON parsing stage: " + e.getMessage(), e);
        }
        long parseTime = System.nanoTime() - parseStart;

        return compileRule(rule, generatorType, parseTime);
    }

    /**
     * Compiles a pre-parsed {@link Rule} object into an executable {@link CompiledRule}.
     *
     * @param rule rule definition
     * @return compiled rule instance
     * @throws RuleCompilationException if compilation fails
     */
    public CompiledRule compile(Rule rule) throws RuleCompilationException {
        return compile(rule, defaultGeneratorType);
    }

    public CompiledRule compile(Rule rule, GeneratorType generatorType) throws RuleCompilationException {
        return compileRule(rule, generatorType, 0L);
    }

    private CompiledRule compileRule(Rule rule, GeneratorType generatorType, long parseTimeNanos) throws RuleCompilationException {
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(generatorType, "generatorType cannot be null");

        // Stage 2: AST Construction
        long astStart = System.nanoTime();
        ExpressionNode astRoot;
        try {
            astRoot = astBuilder.buildAst(rule.getExpression());
        } catch (Exception e) {
            throw new RuleCompilationException("Rule compilation failed during AST building stage for rule '" + rule.getName() + "': " + e.getMessage(), e);
        }
        long astTime = System.nanoTime() - astStart;

        // Stage 3: Type Checking
        long typeCheckStart = System.nanoTime();
        try {
            TypeContext typeContext = new TypeContext(rule.getInputSchema());
            TypeChecker typeChecker = new TypeChecker(typeContext);
            typeChecker.check(astRoot);
        } catch (Exception e) {
            throw new RuleCompilationException("Rule compilation failed during type checking stage for rule '" + rule.getName() + "': " + e.getMessage(), e);
        }
        long typeCheckTime = System.nanoTime() - typeCheckStart;

        // Stage 4: Bytecode Optimization
        long optStart = System.nanoTime();
        ExpressionNode optimizedAst = bytecodeOptimizer.optimize(astRoot);
        long optTime = System.nanoTime() - optStart;

        // Stage 5: Bytecode Generation
        long genStart = System.nanoTime();
        CompiledRule compiledRule;
        try {
            BytecodeGenerator generator = selectGenerator(generatorType);
            compiledRule = generator.generate(rule, optimizedAst);
        } catch (Exception e) {
            throw new RuleCompilationException("Rule compilation failed during bytecode generation stage for rule '" + rule.getName() + "': " + e.getMessage(), e);
        }
        long genTime = System.nanoTime() - genStart;

        CompilationMetrics metrics = new CompilationMetrics(parseTimeNanos, astTime, typeCheckTime, optTime, genTime);
        log.info("Successfully compiled rule '{}' using {} generator. Metrics: {}", rule.getName(), generatorType, metrics);

        return compiledRule;
    }

    private BytecodeGenerator selectGenerator(GeneratorType generatorType) {
        return switch (generatorType) {
            case BYTE_BUDDY -> new ByteBuddyGenerator();
            case ASM -> new AsmGenerator();
        };
    }

    public BytecodeOptimizer getBytecodeOptimizer() {
        return bytecodeOptimizer;
    }
}
