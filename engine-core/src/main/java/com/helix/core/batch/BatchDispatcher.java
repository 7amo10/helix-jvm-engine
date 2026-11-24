package com.helix.core.batch;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.RuleExecutionException;
import com.helix.core.bytecode.AstEvaluator;
import com.helix.core.executor.SyncExecutor;
import com.helix.core.parser.RuleParser;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;

/**
 * Orchestrator for batch rule evaluation that dynamically routes vectorizable rules
 * to hardware SIMD vector strides (via {@link VectorizedBatchEvaluator} and {@link ColumnExtractor})
 * and transparently falls back to scalar evaluation when rules are non-vectorizable,
 * hardware lacks SIMD support, or batch sizes are smaller than the vector lane count.
 */
public class BatchDispatcher {

    private static final Logger log = LoggerFactory.getLogger(BatchDispatcher.class);

    private static final VectorSpecies<Double> PREFERRED_SPECIES;
    private static final boolean SIMD_CAPABLE;
    private static final int SIMD_LANE_COUNT;

    static {
        VectorSpecies<Double> species;
        boolean capable;
        int laneCount;
        try {
            species = DoubleVector.SPECIES_PREFERRED;
            laneCount = (species != null) ? species.length() : 1;
            capable = laneCount > 1;
        } catch (Throwable t) {
            species = null;
            laneCount = 1;
            capable = false;
        }
        PREFERRED_SPECIES = species;
        SIMD_CAPABLE = capable;
        SIMD_LANE_COUNT = laneCount;
    }

    /**
     * Probes if the underlying JVM runtime and hardware support SIMD vectorization.
     *
     * @return true if preferred vector species has length &gt; 1
     */
    public static boolean isSimdCapable() {
        return SIMD_CAPABLE;
    }

    /**
     * Returns the preferred vector lane count for double-precision floating point numbers.
     *
     * @return SIMD lane count (e.g. 4 for 256-bit AVX2, 8 for 512-bit AVX-512)
     */
    public static int getSimdLaneCount() {
        return SIMD_LANE_COUNT;
    }

    /**
     * Returns the host's preferred {@link VectorSpecies} for double precision.
     *
     * @return preferred double species
     */
    public static VectorSpecies<Double> getPreferredSpecies() {
        return PREFERRED_SPECIES;
    }

    /**
     * Evaluation mode selected by the dispatcher.
     */
    public enum EvaluationMode {
        SIMD,
        SCALAR
    }

    /**
     * Holds the output outcome of a batch dispatch execution.
     *
     * @param results            boolean array outcome for each row in the batch
     * @param executionResults   list of individual {@link ExecutionResult} instances
     * @param evaluationMode     execution mode (SIMD or SCALAR)
     * @param executionTimeNanos total execution duration in nanoseconds
     */
    public record DispatchResult(
            boolean[] results,
            List<ExecutionResult> executionResults,
            EvaluationMode evaluationMode,
            long executionTimeNanos
    ) {
        public boolean isSimd() {
            return evaluationMode == EvaluationMode.SIMD;
        }

        public boolean isScalar() {
            return evaluationMode == EvaluationMode.SCALAR;
        }

        public int getBatchSize() {
            return results != null ? results.length : (executionResults != null ? executionResults.size() : 0);
        }
    }

    /**
     * Internal metadata cache for registered rules.
     */
    public record RegisteredRule(
            String name,
            String expression,
            ExpressionNode ast,
            List<String> fields,
            boolean vectorizable,
            CompiledRule compiledRule
    ) {}

    private final VectorSpecies<Double> species;
    private final boolean simdActive;
    private final VectorizabilityClassifier classifier;
    private final VectorizedBatchEvaluator vectorizedEvaluator;
    private final SyncExecutor syncExecutor;
    private final int parallelism;
    private final AstBuilder astBuilder;
    private final RuleParser ruleParser;
    private final ConcurrentMap<String, RegisteredRule> ruleRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, RegisteredRule> classRegistry = new ConcurrentHashMap<>();

    public BatchDispatcher() {
        this(PREFERRED_SPECIES, !SIMD_CAPABLE, new VectorizabilityClassifier(),
                new VectorizedBatchEvaluator(), new SyncExecutor(),
                Runtime.getRuntime().availableProcessors());
    }

    public BatchDispatcher(boolean forceSimdDisabled) {
        this(PREFERRED_SPECIES, forceSimdDisabled, new VectorizabilityClassifier(),
                new VectorizedBatchEvaluator(), new SyncExecutor(),
                Runtime.getRuntime().availableProcessors());
    }

    public BatchDispatcher(VectorSpecies<Double> species) {
        this(species, species == null || species.length() <= 1, new VectorizabilityClassifier(),
                new VectorizedBatchEvaluator(species != null ? species : DoubleVector.SPECIES_PREFERRED),
                new SyncExecutor(), Runtime.getRuntime().availableProcessors());
    }

    public BatchDispatcher(SyncExecutor syncExecutor, int parallelism) {
        this(PREFERRED_SPECIES, !SIMD_CAPABLE, new VectorizabilityClassifier(),
                new VectorizedBatchEvaluator(), syncExecutor, parallelism);
    }

    public BatchDispatcher(VectorSpecies<Double> species,
                           boolean forceSimdDisabled,
                           VectorizabilityClassifier classifier,
                           VectorizedBatchEvaluator vectorizedEvaluator,
                           SyncExecutor syncExecutor,
                           int parallelism) {
        this.species = (species != null) ? species : PREFERRED_SPECIES;
        this.simdActive = !forceSimdDisabled && this.species != null && this.species.length() > 1;
        this.classifier = Objects.requireNonNull(classifier, "classifier cannot be null");
        this.vectorizedEvaluator = Objects.requireNonNull(vectorizedEvaluator, "vectorizedEvaluator cannot be null");
        this.syncExecutor = Objects.requireNonNull(syncExecutor, "syncExecutor cannot be null");
        this.parallelism = Math.max(1, parallelism);
        this.astBuilder = new AstBuilder();
        this.ruleParser = new RuleParser();
    }

    public boolean isSimdActive() {
        return simdActive;
    }

    public int getLaneCount() {
        return species != null ? species.length() : 1;
    }

    public VectorSpecies<Double> getSpecies() {
        return species;
    }

    public VectorizabilityClassifier getClassifier() {
        return classifier;
    }

    public SyncExecutor getSyncExecutor() {
        return syncExecutor;
    }

    // =========================================================================
    // Rule Registration
    // =========================================================================

    public void registerRule(String ruleName, String expression) {
        Objects.requireNonNull(ruleName, "ruleName cannot be null");
        Objects.requireNonNull(expression, "expression cannot be null");
        try {
            ExpressionNode ast = astBuilder.buildAst(expression);
            boolean isVec = classifier.classify(ast);
            List<String> fields = isVec ? classifier.extractReferencedFields(ast) : Collections.emptyList();
            RegisteredRule reg = new RegisteredRule(ruleName, expression, ast, fields, isVec, null);
            ruleRegistry.put(ruleName, reg);
            classifier.register(ruleName, isVec);
        } catch (Exception e) {
            log.warn("Failed to parse and register rule '{}': {}", ruleName, e.getMessage(), e);
        }
    }

    public void registerRule(Rule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        try {
            ExpressionNode ast = astBuilder.buildAst(rule.getExpression());
            boolean isVec = classifier.classify(ast, rule.getInputSchema());
            List<String> fields = isVec ? classifier.extractReferencedFields(ast) : Collections.emptyList();
            RegisteredRule reg = new RegisteredRule(rule.getName(), rule.getExpression(), ast, fields, isVec, null);
            ruleRegistry.put(rule.getName(), reg);
            classifier.register(rule.getName(), isVec);
        } catch (Exception e) {
            log.warn("Failed to register rule '{}': {}", rule.getName(), e.getMessage(), e);
        }
    }

    public void registerRule(String ruleName, ExpressionNode ast, List<String> fields) {
        Objects.requireNonNull(ruleName, "ruleName cannot be null");
        Objects.requireNonNull(ast, "ast cannot be null");
        boolean isVec = classifier.classify(ast);
        List<String> resolvedFields = (fields != null) ? fields : classifier.extractReferencedFields(ast);
        RegisteredRule reg = new RegisteredRule(ruleName, null, ast, resolvedFields, isVec, null);
        ruleRegistry.put(ruleName, reg);
        classifier.register(ruleName, isVec);
    }

    public void registerRule(CompiledRule compiledRule, Rule rule) {
        Objects.requireNonNull(compiledRule, "compiledRule cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");
        try {
            ExpressionNode ast = astBuilder.buildAst(rule.getExpression());
            boolean isVec = classifier.classify(ast, rule.getInputSchema());
            List<String> fields = isVec ? classifier.extractReferencedFields(ast) : Collections.emptyList();
            RegisteredRule reg = new RegisteredRule(rule.getName(), rule.getExpression(), ast, fields, isVec, compiledRule);
            ruleRegistry.put(rule.getName(), reg);
            classRegistry.put(compiledRule.getClass(), reg);
            classifier.register(compiledRule, isVec);
        } catch (Exception e) {
            log.warn("Failed to register compiled rule '{}': {}", rule.getName(), e.getMessage(), e);
        }
    }

    // =========================================================================
    // Batch Dispatching
    // =========================================================================

    public DispatchResult dispatch(ExpressionNode ast, List<String> fields, List<ExecutionContext> contexts) {
        return dispatch(ast, fields, toArray(contexts));
    }

    public DispatchResult dispatch(ExpressionNode ast, List<String> fields, ExecutionContext[] contexts) {
        if (contexts == null || contexts.length == 0) {
            return emptyResult();
        }
        Objects.requireNonNull(ast, "ast cannot be null");

        int batchSize = contexts.length;
        boolean canVectorize = simdActive && batchSize >= getLaneCount() && classifier.classify(ast);

        if (canVectorize) {
            List<String> fieldList = (fields != null) ? fields : classifier.extractReferencedFields(ast);
            try {
                return executeSimd(ast, fieldList, contexts);
            } catch (Exception e) {
                log.warn("SIMD execution failed, falling back to scalar: {}", e.getMessage(), e);
            }
        }

        return executeScalarAst(ast, contexts);
    }

    public DispatchResult dispatch(String expressionOrRuleName, List<ExecutionContext> contexts) {
        return dispatch(expressionOrRuleName, toArray(contexts));
    }

    public DispatchResult dispatch(String expressionOrRuleName, ExecutionContext[] contexts) {
        if (contexts == null || contexts.length == 0) {
            return emptyResult();
        }
        Objects.requireNonNull(expressionOrRuleName, "expressionOrRuleName cannot be null");

        // Check if identifier refers to a registered rule
        RegisteredRule reg = ruleRegistry.get(expressionOrRuleName);
        if (reg != null) {
            return dispatchRegisteredRule(reg, contexts);
        }

        ExpressionNode ast;
        try {
            ast = astBuilder.buildAst(expressionOrRuleName);
        } catch (Exception e) {
            log.debug("Failed to build AST for expression: {}", expressionOrRuleName, e);
            return emptyResult();
        }

        List<String> fields = classifier.extractReferencedFields(ast);
        return dispatch(ast, fields, contexts);
    }

    public DispatchResult dispatchNamedRule(String ruleName, List<ExecutionContext> contexts) {
        return dispatchNamedRule(ruleName, toArray(contexts));
    }

    public DispatchResult dispatchNamedRule(String ruleName, ExecutionContext[] contexts) {
        if (contexts == null || contexts.length == 0) {
            return emptyResult();
        }
        Objects.requireNonNull(ruleName, "ruleName cannot be null");

        RegisteredRule reg = ruleRegistry.get(ruleName);
        if (reg != null) {
            return dispatchRegisteredRule(reg, contexts);
        }

        throw new IllegalArgumentException("Rule not found in registry: " + ruleName);
    }

    private DispatchResult dispatchRegisteredRule(RegisteredRule reg, ExecutionContext[] contexts) {
        if (reg.ast() != null) {
            return dispatch(reg.ast(), reg.fields(), contexts);
        } else if (reg.expression() != null) {
            return dispatch(reg.expression(), contexts);
        } else if (reg.compiledRule() != null) {
            return dispatch(reg.compiledRule(), contexts);
        }
        return emptyResult();
    }

    public DispatchResult dispatch(Rule rule, List<ExecutionContext> contexts) {
        return dispatch(rule, toArray(contexts));
    }

    public DispatchResult dispatch(Rule rule, ExecutionContext[] contexts) {
        if (contexts == null || contexts.length == 0) {
            return emptyResult();
        }
        Objects.requireNonNull(rule, "rule cannot be null");

        ExpressionNode ast;
        if (rule instanceof RuleNode rn) {
            ast = rn.getRootNode();
        } else {
            try {
                ast = astBuilder.buildAst(rule.getExpression());
            } catch (Exception e) {
                log.debug("Failed to parse rule expression '{}': {}", rule.getName(), e.getMessage(), e);
                return emptyResult();
            }
        }

        List<String> fields = classifier.extractReferencedFields(ast);
        int batchSize = contexts.length;
        boolean canVectorize = simdActive && batchSize >= getLaneCount() && classifier.classify(ast, rule.getInputSchema());

        if (canVectorize) {
            try {
                return executeSimd(ast, fields, contexts);
            } catch (Exception e) {
                log.warn("SIMD execution failed for rule '{}', falling back to scalar: {}", rule.getName(), e.getMessage(), e);
            }
        }

        return executeScalarAst(ast, contexts);
    }

    public DispatchResult dispatch(CompiledRule compiledRule, List<ExecutionContext> contexts) {
        return dispatch(compiledRule, toArray(contexts));
    }

    public DispatchResult dispatch(CompiledRule compiledRule, ExecutionContext[] contexts) {
        if (contexts == null || contexts.length == 0) {
            return emptyResult();
        }
        Objects.requireNonNull(compiledRule, "compiledRule cannot be null");

        int batchSize = contexts.length;
        boolean isVectorizable = classifier.classify(compiledRule);

        if (simdActive && batchSize >= getLaneCount() && isVectorizable) {
            RegisteredRule reg = resolveRegisteredRule(compiledRule);
            ExpressionNode ast = (reg != null) ? reg.ast() : extractAstFromCompiledRule(compiledRule);

            if (ast != null) {
                List<String> fields = (reg != null && reg.fields() != null) ? reg.fields() : classifier.extractReferencedFields(ast);
                try {
                    return executeSimd(ast, fields, contexts);
                } catch (Exception e) {
                    log.warn("SIMD execution failed for compiled rule '{}', falling back to scalar: {}", compiledRule.getName(), e.getMessage(), e);
                }
            }
        }

        return executeScalarCompiled(compiledRule, contexts);
    }

    /**
     * Executes a batch of contexts against a compiled rule, returning the results as a {@link List} of {@link ExecutionResult}.
     * Drops in seamlessly for {@link com.helix.core.executor.BatchExecutor}.
     *
     * @param compiledRule compiled rule instance
     * @param contexts     list of contexts
     * @return list of execution results
     */
    public List<ExecutionResult> executeBatch(CompiledRule compiledRule, List<ExecutionContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return Collections.emptyList();
        }
        DispatchResult result = dispatch(compiledRule, contexts);
        return result.executionResults();
    }

    // =========================================================================
    // Execution Engines
    // =========================================================================

    private DispatchResult executeSimd(ExpressionNode ast, List<String> fields, ExecutionContext[] contexts) {
        long startTime = System.nanoTime();
        int batchSize = contexts.length;

        boolean[] results;
        try (Arena arena = Arena.ofConfined()) {
            Map<String, MemorySegment> columns = ColumnExtractor.extract(contexts, fields, arena);
            results = vectorizedEvaluator.evaluate(ast, columns, batchSize);
        }

        long totalDuration = System.nanoTime() - startTime;
        long perContextDuration = totalDuration / Math.max(1, batchSize);

        List<ExecutionResult> executionResults = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            executionResults.add(ExecutionResult.success(results[i], perContextDuration));
            syncExecutor.getMetrics().recordExecution(true, perContextDuration);
        }

        return new DispatchResult(results, Collections.unmodifiableList(executionResults), EvaluationMode.SIMD, totalDuration);
    }

    private DispatchResult executeScalarCompiled(CompiledRule compiledRule, ExecutionContext[] contexts) {
        long startTime = System.nanoTime();
        int batchSize = contexts.length;
        List<ExecutionResult> executionResults;

        ForkJoinPool customPool = new ForkJoinPool(parallelism);
        try {
            executionResults = customPool.submit(() ->
                    java.util.Arrays.stream(contexts)
                            .parallel()
                            .map(ctx -> {
                                try {
                                    return syncExecutor.execute(compiledRule, ctx);
                                } catch (RuleExecutionException e) {
                                    return ExecutionResult.failure(e, 0);
                                }
                            })
                            .toList()
            ).get();
        } catch (Exception e) {
            executionResults = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                executionResults.add(ExecutionResult.failure(new RuleExecutionException("Batch scalar error: " + e.getMessage(), e), 0));
            }
        } finally {
            customPool.shutdown();
        }

        long totalDuration = System.nanoTime() - startTime;
        boolean[] results = extractBooleanArray(executionResults);
        return new DispatchResult(results, Collections.unmodifiableList(executionResults), EvaluationMode.SCALAR, totalDuration);
    }

    private DispatchResult executeScalarAst(ExpressionNode ast, ExecutionContext[] contexts) {
        long startTime = System.nanoTime();
        int batchSize = contexts.length;
        List<ExecutionResult> executionResults = new ArrayList<>(batchSize);

        for (ExecutionContext ctx : contexts) {
            long itemStart = System.nanoTime();
            try {
                ExecutionContext effectiveCtx = (ctx != null) ? ctx : new ExecutionContext();
                AstEvaluator evaluator = new AstEvaluator(effectiveCtx);
                Object val = evaluator.evaluate(ast);
                long itemDuration = System.nanoTime() - itemStart;
                executionResults.add(ExecutionResult.success(val, itemDuration));
                syncExecutor.getMetrics().recordExecution(true, itemDuration);
            } catch (Exception e) {
                long itemDuration = System.nanoTime() - itemStart;
                executionResults.add(ExecutionResult.failure(e, itemDuration));
                syncExecutor.getMetrics().recordExecution(false, itemDuration);
            }
        }

        long totalDuration = System.nanoTime() - startTime;
        boolean[] results = extractBooleanArray(executionResults);
        return new DispatchResult(results, Collections.unmodifiableList(executionResults), EvaluationMode.SCALAR, totalDuration);
    }

    private boolean[] extractBooleanArray(List<ExecutionResult> executionResults) {
        boolean[] results = new boolean[executionResults.size()];
        for (int i = 0; i < executionResults.size(); i++) {
            ExecutionResult res = executionResults.get(i);
            if (res.isSuccess() && res.getResult().isPresent()) {
                Object val = res.getResult().get();
                if (val instanceof Boolean b) {
                    results[i] = b;
                } else if (val instanceof Number n) {
                    results[i] = n.doubleValue() != 0.0;
                } else {
                    results[i] = false;
                }
            } else {
                results[i] = false;
            }
        }
        return results;
    }

    private RegisteredRule resolveRegisteredRule(CompiledRule compiledRule) {
        RegisteredRule reg = classRegistry.get(compiledRule.getClass());
        if (reg != null) {
            return reg;
        }
        if (compiledRule.getName() != null) {
            return ruleRegistry.get(compiledRule.getName());
        }
        return null;
    }

    private ExpressionNode extractAstFromCompiledRule(CompiledRule compiledRule) {
        try {
            Field f = compiledRule.getClass().getDeclaredField("astRoot");
            f.setAccessible(true);
            Object obj = f.get(compiledRule);
            if (obj instanceof ExpressionNode ast) {
                return ast;
            }
        } catch (NoSuchFieldException ignored) {
            // Field not present in this generator strategy
        } catch (Exception e) {
            log.debug("Could not extract astRoot field from compiled rule: {}", e.getMessage());
        }
        return null;
    }

    private static ExecutionContext[] toArray(List<ExecutionContext> contexts) {
        if (contexts == null) {
            return null;
        }
        return contexts.toArray(new ExecutionContext[0]);
    }

    private static DispatchResult emptyResult() {
        return new DispatchResult(new boolean[0], Collections.emptyList(), EvaluationMode.SCALAR, 0L);
    }
}
