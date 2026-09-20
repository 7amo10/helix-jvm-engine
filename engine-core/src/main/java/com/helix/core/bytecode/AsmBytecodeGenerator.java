package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.classloader.RuleClassLoader;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.OnnxInferenceNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.core.parser.ast.VariableNode;
import com.helix.profiler.node.AstNodeProfiler;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-level ASM bytecode generator for high-performance rule compilation with direct ONNX inference invocations.
 *
 * <p>Emits ultra-low-latency JVM bytecode instructions ({@code ALOAD}, {@code LDC}, {@code INVOKESTATIC},
 * {@code FCMP}/{@code DCMP}) bypassing reflection and dynamic dispatch, achieving sub-millisecond execution latency.</p>
 */
public class AsmBytecodeGenerator implements BytecodeGenerator, Opcodes {

    private final boolean profilingEnabled;

    public AsmBytecodeGenerator() {
        this(false);
    }

    public AsmBytecodeGenerator(boolean profilingEnabled) {
        this.profilingEnabled = profilingEnabled;
    }

    public boolean isProfilingEnabled() {
        return profilingEnabled;
    }

    private static final Logger log = LoggerFactory.getLogger(AsmBytecodeGenerator.class);
    private static final AtomicLong classCounter = new AtomicLong(0);

    /**
     * Generates raw JVM bytecode for the given rule without loading the class into the JVM.
     *
     * @param rule    rule definition
     * @param astRoot AST expression tree
     * @return raw class byte array
     * @throws BytecodeGenerationException if generation fails
     */
    public byte[] generateBytecode(Rule rule, ExpressionNode astRoot) throws BytecodeGenerationException {
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(astRoot, "astRoot cannot be null");
        String className = "com.helix.compiled.asm.AsmRule_" + sanitizeName(rule.getName()) + "_" + classCounter.incrementAndGet();
        return generateBytecode(className, rule, astRoot);
    }

    /**
     * Generates raw JVM bytecode for the specified class name.
     *
     * @param className fully qualified class name to generate
     * @param rule      rule definition
     * @param astRoot   AST expression tree
     * @return raw class byte array
     * @throws BytecodeGenerationException if generation fails
     */
    public byte[] generateBytecode(String className, Rule rule, ExpressionNode astRoot) throws BytecodeGenerationException {
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(astRoot, "astRoot cannot be null");

        try {
            AsmClassBuilder classBuilder = new AsmClassBuilder(className);
            MethodVisitor mv = classBuilder.createExecuteMethodVisitor();

            mv.visitCode();

            Label startLabel = new Label();
            Label endLabel = new Label();
            Label catchLabel = new Label();

            mv.visitTryCatchBlock(startLabel, endLabel, catchLabel, "java/lang/Throwable");

            // Store nanoTime at start: long startTime = System.nanoTime();
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
            int startTimeVar = 2; // local var slot 2 (slot 0 is 'this', slot 1 is 'context')
            mv.visitVarInsn(LSTORE, startTimeVar);

            mv.visitLabel(startLabel);

            int resultVar = 4; // slot 4 for result Object

            if (isDirectlyCompilable(astRoot)) {
                if (astRoot instanceof OnnxInferenceNode onnx) {
                    emitOnnxInference(onnx, mv);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    mv.visitVarInsn(ASTORE, resultVar);
                } else {
                    emitBooleanExpr(astRoot, mv, classBuilder.getClassNameInternal(), rule);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    mv.visitVarInsn(ASTORE, resultVar);
                }
            } else {
                // Fallback to AstEvaluator for complex non-ML expressions
                int evaluatorVar = 4;
                mv.visitTypeInsn(NEW, "com/helix/core/bytecode/AstEvaluator");
                mv.visitInsn(DUP);
                mv.visitVarInsn(ALOAD, 1); // context
                mv.visitMethodInsn(INVOKESPECIAL, "com/helix/core/bytecode/AstEvaluator", "<init>", "(Lcom/helix/api/ExecutionContext;)V", false);
                mv.visitVarInsn(ASTORE, evaluatorVar);

                mv.visitVarInsn(ALOAD, evaluatorVar);
                mv.visitVarInsn(ALOAD, 0); // this
                mv.visitFieldInsn(GETFIELD, classBuilder.getClassNameInternal(), "astRoot", "Lcom/helix/core/parser/ast/ExpressionNode;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "com/helix/core/bytecode/AstEvaluator", "evaluate", "(Lcom/helix/core/parser/ast/ExpressionNode;)Ljava/lang/Object;", false);
                mv.visitVarInsn(ASTORE, resultVar);
            }

            // long duration = System.nanoTime() - startTime;
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
            mv.visitVarInsn(LLOAD, startTimeVar);
            mv.visitInsn(LSUB);
            int durationVar = 5;
            mv.visitVarInsn(LSTORE, durationVar);

            // return ExecutionResult.success(result, duration);
            mv.visitVarInsn(ALOAD, resultVar);
            mv.visitVarInsn(LLOAD, durationVar);
            mv.visitMethodInsn(INVOKESTATIC, "com/helix/api/ExecutionResult", "success", "(Ljava/lang/Object;J)Lcom/helix/api/ExecutionResult;", false);
            mv.visitInsn(ARETURN);

            mv.visitLabel(endLabel);

            // Exception handler block: catch (Throwable t)
            mv.visitLabel(catchLabel);
            int exVar = 6;
            mv.visitVarInsn(ASTORE, exVar);

            // long errDuration = System.nanoTime() - startTime;
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
            mv.visitVarInsn(LLOAD, startTimeVar);
            mv.visitInsn(LSUB);
            int errDurationVar = 7;
            mv.visitVarInsn(LSTORE, errDurationVar);

            // return ExecutionResult.failure(t, errDuration);
            mv.visitVarInsn(ALOAD, exVar);
            mv.visitVarInsn(LLOAD, errDurationVar);
            mv.visitMethodInsn(INVOKESTATIC, "com/helix/api/ExecutionResult", "failure", "(Ljava/lang/Throwable;J)Lcom/helix/api/ExecutionResult;", false);
            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();

            return classBuilder.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate ASM bytecode for rule: {}", rule.getName(), e);
            throw new BytecodeGenerationException("ASM class generation failed for rule '" + rule.getName() + "': " + e.getMessage(), e);
        }
    }

    @Override
    public CompiledRule generate(Rule rule, ExpressionNode astRoot) throws BytecodeGenerationException {
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(astRoot, "astRoot cannot be null");

        String className = "com.helix.compiled.asm.AsmRule_" + sanitizeName(rule.getName()) + "_" + classCounter.incrementAndGet();

        try {
            byte[] byteCode = generateBytecode(className, rule, astRoot);

            // Load and instantiate class using isolated RuleClassLoader
            RuleClassLoader classLoader = new RuleClassLoader("rule-loader-" + classCounter.get(), AsmBytecodeGenerator.class.getClassLoader());
            Class<?> clazz = classLoader.defineRule(className, byteCode);

            Constructor<?> constructor = clazz.getConstructor(String.class, String.class, ExpressionNode.class);
            return (CompiledRule) constructor.newInstance(rule.getName(), rule.getVersion(), astRoot);
        } catch (Exception e) {
            log.error("Failed to instantiate ASM rule class: {}", rule.getName(), e);
            throw new BytecodeGenerationException("ASM class loading failed for rule '" + rule.getName() + "': " + e.getMessage(), e);
        }
    }

    private boolean isDirectlyCompilable(ExpressionNode node) {
        if (node instanceof OnnxInferenceNode || node instanceof LiteralNode || node instanceof VariableNode) {
            return true;
        }
        if (node instanceof UnaryOpNode u) {
            return u.getOperator() == UnaryOpNode.Operator.NOT && isDirectlyCompilable(u.getOperand());
        }
        if (node instanceof BinaryOpNode b) {
            return switch (b.getOperator()) {
                case AND, OR, GREATER_THAN, GREATER_EQUAL, LESS_THAN, LESS_EQUAL, EQUAL, NOT_EQUAL ->
                        isDirectlyCompilable(b.getLeft()) && isDirectlyCompilable(b.getRight());
                default -> false;
            };
        }
        return false;
    }

    private void emitBooleanExpr(ExpressionNode node, MethodVisitor mv, String classNameInternal, Rule rule) {
        if (node instanceof BinaryOpNode b) {
            switch (b.getOperator()) {
                case AND -> {
                    Label falseLbl = new Label();
                    Label endLbl = new Label();
                    emitBooleanExpr(b.getLeft(), mv, classNameInternal, rule);
                    mv.visitJumpInsn(IFEQ, falseLbl);
                    emitBooleanExpr(b.getRight(), mv, classNameInternal, rule);
                    mv.visitJumpInsn(IFEQ, falseLbl);
                    mv.visitInsn(ICONST_1);
                    mv.visitJumpInsn(GOTO, endLbl);
                    mv.visitLabel(falseLbl);
                    mv.visitInsn(ICONST_0);
                    mv.visitLabel(endLbl);
                    return;
                }
                case OR -> {
                    Label trueLbl = new Label();
                    Label endLbl = new Label();
                    emitBooleanExpr(b.getLeft(), mv, classNameInternal, rule);
                    mv.visitJumpInsn(IFNE, trueLbl);
                    emitBooleanExpr(b.getRight(), mv, classNameInternal, rule);
                    mv.visitJumpInsn(IFNE, trueLbl);
                    mv.visitInsn(ICONST_0);
                    mv.visitJumpInsn(GOTO, endLbl);
                    mv.visitLabel(trueLbl);
                    mv.visitInsn(ICONST_1);
                    mv.visitLabel(endLbl);
                    return;
                }
                case GREATER_THAN, GREATER_EQUAL, LESS_THAN, LESS_EQUAL, EQUAL, NOT_EQUAL -> {
                    if (profilingEnabled) {
                        String nodeId = determineNodeId(rule, b);
                        AstNodeProfiler.registerNode(rule != null ? rule.getName() : "unknown", nodeId);
                        emitProfilerEntry(mv, nodeId);
                        emitComparisonExpr(b, mv);
                        emitProfilerExit(mv, nodeId);
                    } else {
                        emitComparisonExpr(b, mv);
                    }
                    return;
                }
                default -> throw new IllegalArgumentException("Unsupported binary operator in boolean expression: " + b.getOperator());
            }
        }

        if (node instanceof UnaryOpNode u && u.getOperator() == UnaryOpNode.Operator.NOT) {
            Label trueLbl = new Label();
            Label endLbl = new Label();
            emitBooleanExpr(u.getOperand(), mv, classNameInternal, rule);
            mv.visitJumpInsn(IFEQ, trueLbl);
            mv.visitInsn(ICONST_0);
            mv.visitJumpInsn(GOTO, endLbl);
            mv.visitLabel(trueLbl);
            mv.visitInsn(ICONST_1);
            mv.visitLabel(endLbl);
            return;
        }

        if (node instanceof VariableNode v) {
            if (profilingEnabled) {
                String nodeId = determineNodeId(rule, v);
                AstNodeProfiler.registerNode(rule != null ? rule.getName() : "unknown", nodeId);
                emitProfilerEntry(mv, nodeId);
                mv.visitVarInsn(ALOAD, 1); // ExecutionContext
                mv.visitLdcInsn(v.getName());
                mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/bytecode/AsmBytecodeGenerator", "resolveBoolean",
                        "(Lcom/helix/api/ExecutionContext;Ljava/lang/String;)Z", false);
                emitProfilerExit(mv, nodeId);
            } else {
                mv.visitVarInsn(ALOAD, 1); // ExecutionContext
                mv.visitLdcInsn(v.getName());
                mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/bytecode/AsmBytecodeGenerator", "resolveBoolean",
                        "(Lcom/helix/api/ExecutionContext;Ljava/lang/String;)Z", false);
            }
            return;
        }

        if (node instanceof LiteralNode lit && lit.getValue() instanceof Boolean b) {
            mv.visitInsn(b ? ICONST_1 : ICONST_0);
            return;
        }

        if (node instanceof OnnxInferenceNode onnx) {
            if (profilingEnabled) {
                String nodeId = determineNodeId(rule, onnx);
                AstNodeProfiler.registerNode(rule != null ? rule.getName() : "unknown", nodeId);
                emitProfilerEntry(mv, nodeId);
                emitOnnxInference(onnx, mv);
                mv.visitLdcInsn(0.5f);
                mv.visitInsn(FCMPG);
                emitComparisonJump(BinaryOpNode.Operator.GREATER_THAN, mv);
                emitProfilerExit(mv, nodeId);
            } else {
                emitOnnxInference(onnx, mv);
                mv.visitLdcInsn(0.5f);
                mv.visitInsn(FCMPG);
                emitComparisonJump(BinaryOpNode.Operator.GREATER_THAN, mv);
            }
            return;
        }

        throw new IllegalArgumentException("Cannot emit node as boolean expression: " + node);
    }

    private void emitComparisonExpr(BinaryOpNode b, MethodVisitor mv) {
        // Object / String equality
        if (b.getOperator() == BinaryOpNode.Operator.EQUAL || b.getOperator() == BinaryOpNode.Operator.NOT_EQUAL) {
            if (isStringOrObject(b.getLeft()) || isStringOrObject(b.getRight())) {
                emitObjectExpr(b.getLeft(), mv);
                emitObjectExpr(b.getRight(), mv);
                mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                if (b.getOperator() == BinaryOpNode.Operator.NOT_EQUAL) {
                    Label trueLbl = new Label();
                    Label endLbl = new Label();
                    mv.visitJumpInsn(IFEQ, trueLbl);
                    mv.visitInsn(ICONST_0);
                    mv.visitJumpInsn(GOTO, endLbl);
                    mv.visitLabel(trueLbl);
                    mv.visitInsn(ICONST_1);
                    mv.visitLabel(endLbl);
                }
                return;
            }
        }

        // Check if comparing OnnxInferenceNode with a LiteralNode
        if (b.getLeft() instanceof OnnxInferenceNode onnx && b.getRight() instanceof LiteralNode lit && lit.getValue() instanceof Number n) {
            emitOnnxInference(onnx, mv);
            mv.visitLdcInsn(n.floatValue());
            mv.visitInsn(FCMPG);
            emitComparisonJump(b.getOperator(), mv);
            return;
        }
        if (b.getRight() instanceof OnnxInferenceNode onnx && b.getLeft() instanceof LiteralNode lit && lit.getValue() instanceof Number n) {
            mv.visitLdcInsn(n.floatValue());
            emitOnnxInference(onnx, mv);
            mv.visitInsn(FCMPG);
            emitComparisonJump(b.getOperator(), mv);
            return;
        }

        // Numeric comparison via double
        emitNumericExpr(b.getLeft(), mv);
        emitNumericExpr(b.getRight(), mv);
        mv.visitInsn(DCMPG);
        emitComparisonJump(b.getOperator(), mv);
    }

    private void emitProfilerEntry(MethodVisitor mv, String nodeId) {
        mv.visitLdcInsn(nodeId);
        mv.visitMethodInsn(INVOKESTATIC, "com/helix/profiler/node/AstNodeProfiler", "recordEntry", "(Ljava/lang/String;)V", false);
    }

    private void emitProfilerExit(MethodVisitor mv, String nodeId) {
        mv.visitInsn(DUP);
        mv.visitLdcInsn(nodeId);
        mv.visitInsn(SWAP);
        mv.visitMethodInsn(INVOKESTATIC, "com/helix/profiler/node/AstNodeProfiler", "recordExit", "(Ljava/lang/String;Z)V", false);
    }

    private String determineNodeId(Rule rule, ExpressionNode node) {
        if (node instanceof BinaryOpNode b) {
            if (b.getLeft() instanceof OnnxInferenceNode onnx) {
                return "clause_ml_" + sanitizeName(onnx.getModelName()) + "_" + b.getOperator().name().toLowerCase();
            } else if (b.getRight() instanceof OnnxInferenceNode onnx) {
                return "clause_ml_" + sanitizeName(onnx.getModelName()) + "_" + b.getOperator().name().toLowerCase();
            } else if (b.getLeft() instanceof VariableNode var) {
                return "clause_" + sanitizeName(var.getName()) + "_" + b.getOperator().name().toLowerCase();
            } else if (b.getRight() instanceof VariableNode var) {
                return "clause_" + sanitizeName(var.getName()) + "_" + b.getOperator().name().toLowerCase();
            } else {
                return "clause_cmp_" + b.getOperator().name().toLowerCase();
            }
        } else if (node instanceof OnnxInferenceNode onnx) {
            return "clause_ml_" + sanitizeName(onnx.getModelName());
        } else if (node instanceof VariableNode var) {
            return "clause_var_" + sanitizeName(var.getName());
        }
        return "clause_node_" + Math.abs(Objects.hash(rule != null ? rule.getName() : "", node));
    }

    private boolean isStringOrObject(ExpressionNode node) {
        if (node instanceof LiteralNode lit && (lit.getValue() instanceof String || !(lit.getValue() instanceof Number || lit.getValue() instanceof Boolean))) {
            return true;
        }
        return false;
    }

    private void emitObjectExpr(ExpressionNode node, MethodVisitor mv) {
        if (node instanceof LiteralNode lit) {
            Object val = lit.getValue();
            if (val == null) {
                mv.visitInsn(ACONST_NULL);
            } else {
                mv.visitLdcInsn(String.valueOf(val));
            }
        } else if (node instanceof VariableNode var) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(var.getName());
            mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/bytecode/AsmBytecodeGenerator", "resolveObject",
                    "(Lcom/helix/api/ExecutionContext;Ljava/lang/String;)Ljava/lang/Object;", false);
        } else {
            throw new IllegalArgumentException("Unsupported object expression node: " + node);
        }
    }

    private void emitNumericExpr(ExpressionNode node, MethodVisitor mv) {
        if (node instanceof LiteralNode lit && lit.getValue() instanceof Number n) {
            mv.visitLdcInsn(n.doubleValue());
        } else if (node instanceof VariableNode var) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(var.getName());
            mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/bytecode/AsmBytecodeGenerator", "resolveDouble",
                    "(Lcom/helix/api/ExecutionContext;Ljava/lang/String;)D", false);
        } else if (node instanceof OnnxInferenceNode onnx) {
            emitOnnxInference(onnx, mv);
            mv.visitInsn(F2D);
        } else {
            throw new IllegalArgumentException("Unsupported numeric node: " + node);
        }
    }

    private void emitComparisonJump(BinaryOpNode.Operator op, MethodVisitor mv) {
        Label trueLbl = new Label();
        Label endLbl = new Label();
        int jumpOpcode = switch (op) {
            case GREATER_THAN -> IFGT;
            case GREATER_EQUAL -> IFGE;
            case LESS_THAN -> IFLT;
            case LESS_EQUAL -> IFLE;
            case EQUAL -> IFEQ;
            case NOT_EQUAL -> IFNE;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + op);
        };
        mv.visitJumpInsn(jumpOpcode, trueLbl);
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, endLbl);
        mv.visitLabel(trueLbl);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(endLbl);
    }

    private void emitOnnxInference(OnnxInferenceNode node, MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 1); // ExecutionContext context
        mv.visitLdcInsn(node.getModelName());
        if (node.getOutputTensorName() == null || ("probabilities".equals(node.getOutputTensorName()) && node.getOutputIndex() == 1)) {
            mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/ml/OnnxSessionPool", "run",
                    "(Lcom/helix/api/ExecutionContext;Ljava/lang/String;)F", false);
        } else {
            mv.visitLdcInsn(node.getOutputTensorName());
            emitIntConst(mv, node.getOutputIndex());
            mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/ml/OnnxSessionPool", "run",
                    "(Lcom/helix/api/ExecutionContext;Ljava/lang/String;Ljava/lang/String;I)F", false);
        }
    }

    private void emitIntConst(MethodVisitor mv, int val) {
        if (val >= -1 && val <= 5) {
            mv.visitInsn(ICONST_0 + val);
        } else if (val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, val);
        } else if (val >= Short.MIN_VALUE && val <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, val);
        } else {
            mv.visitLdcInsn(val);
        }
    }

    /**
     * Runtime helper invoked from compiled bytecode to retrieve a double variable from context.
     */
    public static double resolveDouble(ExecutionContext context, String name) {
        Object val = context.getVariable(name)
                .orElseThrow(() -> new IllegalArgumentException("Missing variable in execution context: " + name));
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s) {
            return Double.parseDouble(s);
        }
        throw new IllegalArgumentException("Variable '" + name + "' is not numeric: " + val);
    }

    /**
     * Runtime helper invoked from compiled bytecode to retrieve a boolean variable from context.
     */
    public static boolean resolveBoolean(ExecutionContext context, String name) {
        Object val = context.getVariable(name)
                .orElseThrow(() -> new IllegalArgumentException("Missing variable in execution context: " + name));
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        throw new IllegalArgumentException("Variable '" + name + "' is not boolean: " + val);
    }

    /**
     * Runtime helper invoked from compiled bytecode to retrieve an object variable from context.
     */
    public static Object resolveObject(ExecutionContext context, String name) {
        return context.getVariable(name)
                .orElseThrow(() -> new IllegalArgumentException("Missing variable in execution context: " + name));
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
