package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.parser.ast.ExpressionNode;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-level ASM bytecode generator for experimental POC rule compilation.
 *
 * <h2>ASM vs ByteBuddy Comparison & Documentation</h2>
 * <ul>
 *   <li><b>ByteBuddy:</b> High-level, type-safe API for dynamic class creation and method delegation.
 *       Recommended for standard production rules requiring complex method calls, reflection, and safety.</li>
 *   <li><b>ASM:</b> Low-level direct JVM bytecode instructions manipulation (`ClassWriter`, `MethodVisitor`).
 *       Recommended for ultra low-latency critical path execution where reflection and delegation overhead must be zero.</li>
 * </ul>
 */
public class AsmGenerator implements BytecodeGenerator, Opcodes {

    private static final Logger log = LoggerFactory.getLogger(AsmGenerator.class);
    private static final AtomicLong classCounter = new AtomicLong(0);

    @Override
    public CompiledRule generate(Rule rule, ExpressionNode astRoot) throws BytecodeGenerationException {
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(astRoot, "astRoot cannot be null");

        String className = "com.helix.compiled.asm.AsmRule_" + sanitizeName(rule.getName()) + "_" + classCounter.incrementAndGet();

        try {
            AsmClassBuilder classBuilder = new AsmClassBuilder(className);
            MethodVisitor mv = classBuilder.createExecuteMethodVisitor();

            mv.visitCode();

            // Store nanoTime at start: long startTime = System.nanoTime();
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
            int startTimeVar = 2; // local var slot 2 (slot 0 is 'this', slot 1 is 'context')
            mv.visitVarInsn(LSTORE, startTimeVar);

            // Execute evaluation via AstEvaluator
            // AstEvaluator evaluator = new AstEvaluator(context);
            mv.visitTypeInsn(NEW, "com/helix/core/bytecode/AstEvaluator");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 1); // context
            mv.visitMethodInsn(INVOKESPECIAL, "com/helix/core/bytecode/AstEvaluator", "<init>", "(Lcom/helix/api/ExecutionContext;)V", false);
            int evaluatorVar = 4; // slot 4
            mv.visitVarInsn(ASTORE, evaluatorVar);

            // Object result = evaluator.evaluate(this.astRoot);
            mv.visitVarInsn(ALOAD, evaluatorVar);
            mv.visitVarInsn(ALOAD, 0); // this
            mv.visitFieldInsn(GETFIELD, classBuilder.getClassNameInternal(), "astRoot", "Lcom/helix/core/parser/ast/ExpressionNode;");
            mv.visitMethodInsn(INVOKEVIRTUAL, "com/helix/core/bytecode/AstEvaluator", "evaluate", "(Lcom/helix/core/parser/ast/ExpressionNode;)Ljava/lang/Object;", false);
            int resultVar = 5;
            mv.visitVarInsn(ASTORE, resultVar);

            // long duration = System.nanoTime() - startTime;
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
            mv.visitVarInsn(LLOAD, startTimeVar);
            mv.visitInsn(LSUB);
            int durationVar = 6;
            mv.visitVarInsn(LSTORE, durationVar);

            // return ExecutionResult.success(result, duration);
            mv.visitVarInsn(ALOAD, resultVar);
            mv.visitVarInsn(LLOAD, durationVar);
            mv.visitMethodInsn(INVOKESTATIC, "com/helix/api/ExecutionResult", "success", "(Ljava/lang/Object;J)Lcom/helix/api/ExecutionResult;", false);

            mv.visitInsn(ARETURN);
            mv.visitMaxs(5, 8);
            mv.visitEnd();

            byte[] byteCode = classBuilder.toByteArray();

            // Load and instantiate class
            DynamicClassLoader classLoader = new DynamicClassLoader(AsmGenerator.class.getClassLoader());
            Class<?> clazz = classLoader.defineClass(className, byteCode);

            Constructor<?> constructor = clazz.getConstructor(String.class, String.class, ExpressionNode.class);
            return (CompiledRule) constructor.newInstance(rule.getName(), rule.getVersion(), astRoot);

        } catch (Exception e) {
            log.error("Failed to generate ASM bytecode for rule: {}", rule.getName(), e);
            throw new BytecodeGenerationException("ASM class generation failed for rule '" + rule.getName() + "': " + e.getMessage(), e);
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static class DynamicClassLoader extends ClassLoader {
        public DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }
}
