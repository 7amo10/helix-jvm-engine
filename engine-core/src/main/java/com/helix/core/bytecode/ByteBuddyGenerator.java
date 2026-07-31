package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.parser.ast.ExpressionNode;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ByteBuddy-based bytecode generator that dynamically generates Java classes implementing {@link CompiledRule}.
 */
public class ByteBuddyGenerator implements BytecodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(ByteBuddyGenerator.class);
    private static final AtomicLong classCounter = new AtomicLong(0);

    @Override
    public CompiledRule generate(Rule rule, ExpressionNode astRoot) throws BytecodeGenerationException {
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(astRoot, "astRoot cannot be null");

        String className = "com.helix.compiled.Rule_" + sanitizeName(rule.getName()) + "_" + classCounter.incrementAndGet();

        try {
            RuleInterceptor interceptor = new RuleInterceptor(rule.getName(), rule.getVersion(), astRoot);

            Class<?> dynamicType = new ByteBuddy()
                    .subclass(Object.class)
                    .implement(CompiledRule.class)
                    .name(className)
                    .method(ElementMatchers.named("getName"))
                    .intercept(MethodDelegation.to(interceptor))
                    .method(ElementMatchers.named("getVersion"))
                    .intercept(MethodDelegation.to(interceptor))
                    .method(ElementMatchers.named("execute"))
                    .intercept(MethodDelegation.to(interceptor))
                    .make()
                    .load(ByteBuddyGenerator.class.getClassLoader())
                    .getLoaded();

            Constructor<?> constructor = dynamicType.getConstructor();
            return (CompiledRule) constructor.newInstance();

        } catch (Exception e) {
            log.error("Failed to generate ByteBuddy bytecode for rule: {}", rule.getName(), e);
            throw new BytecodeGenerationException("ByteBuddy class generation failed for rule '" + rule.getName() + "': " + e.getMessage(), e);
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Runtime delegation handler for generated CompiledRule instances.
     */
    public static class RuleInterceptor {
        private final String ruleName;
        private final String ruleVersion;
        private final ExpressionNode astRoot;

        public RuleInterceptor(String ruleName, String ruleVersion, ExpressionNode astRoot) {
            this.ruleName = ruleName;
            this.ruleVersion = ruleVersion;
            this.astRoot = astRoot;
        }

        public String getName() {
            return ruleName;
        }

        public String getVersion() {
            return ruleVersion;
        }

        @RuntimeType
        public ExecutionResult execute(@Argument(0) ExecutionContext context) {
            long startTime = System.nanoTime();
            try {
                AstEvaluator evaluator = new AstEvaluator(context);
                Object result = evaluator.evaluate(astRoot);
                long duration = System.nanoTime() - startTime;
                return ExecutionResult.success(result, duration);
            } catch (Exception e) {
                long duration = System.nanoTime() - startTime;
                return ExecutionResult.failure(e, duration);
            }
        }
    }
}
