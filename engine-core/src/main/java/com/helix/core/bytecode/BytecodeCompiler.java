package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.debug.ConditionClause;
import com.helix.core.parser.ExpressionParseException;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.BinaryExpressionNode;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ComparisonNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ASM Bytecode Compiler capable of standard zero-overhead compilation and
 * dynamic debug instrumentation injecting probe hooks before condition branch jumps.
 */
public class BytecodeCompiler implements Opcodes {

    private static final AtomicLong classCounter = new AtomicLong(0);
    private final ExpressionRuleParser expressionParser = new ExpressionRuleParser();

    /**
     * Compiles an expression in non-debug mode (zero overhead).
     */
    public CompiledRule compile(String expression) throws BytecodeGenerationException {
        return compile(expression, false);
    }

    /**
     * Compiles an expression with optional debug instrumentation.
     */
    public CompiledRule compile(String expression, boolean debug) throws BytecodeGenerationException {
        try {
            ExpressionNode ast = expressionParser.parseAndFold(expression);
            Rule rule = new RuleNode("BytecodeRule_" + classCounter.incrementAndGet(), expression, Collections.emptyMap(), ast);
            return compile(rule, ast, debug);
        } catch (ExpressionParseException e) {
            throw new BytecodeGenerationException("Syntax error in expression: " + e.getMessage(), e);
        }
    }

    /**
     * Compiles a Rule in non-debug mode (zero overhead).
     */
    public CompiledRule compile(Rule rule) throws BytecodeGenerationException {
        return compile(rule, false);
    }

    /**
     * Compiles a Rule with optional debug instrumentation.
     */
    public CompiledRule compile(Rule rule, boolean debug) throws BytecodeGenerationException {
        Objects.requireNonNull(rule, "rule cannot be null");
        try {
            ExpressionNode ast;
            if (rule instanceof RuleNode rn) {
                ast = rn.getRootNode();
            } else {
                ast = expressionParser.parseAndFold(rule.getExpression());
            }
            return compile(rule, ast, debug);
        } catch (ExpressionParseException e) {
            throw new BytecodeGenerationException("Syntax error in rule expression: " + e.getMessage(), e);
        }
    }

    public CompiledRule compile(Rule rule, ExpressionNode ast, boolean debug) throws BytecodeGenerationException {
        String className = "com.helix.compiled.asm." + (debug ? "AsmDebugRule_" : "AsmRule_")
                + sanitizeName(rule.getName()) + "_" + classCounter.incrementAndGet();

        byte[] byteCode = generateBytecode(className, rule, ast, debug);

        try {
            DynamicClassLoader classLoader = new DynamicClassLoader(BytecodeCompiler.class.getClassLoader());
            Class<?> clazz = classLoader.defineClass(className, byteCode);
            Constructor<?> constructor = clazz.getConstructor(String.class, String.class, ExpressionNode.class);
            return (CompiledRule) constructor.newInstance(rule.getName(), rule.getVersion(), ast);
        } catch (Exception e) {
            throw new BytecodeGenerationException("Failed to instantiate compiled rule '" + rule.getName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Generates class bytecode with or without debug instrumentation.
     */
    public byte[] generateBytecode(Rule rule, boolean debug) throws BytecodeGenerationException {
        try {
            ExpressionNode ast = (rule instanceof RuleNode rn) ? rn.getRootNode() : expressionParser.parseAndFold(rule.getExpression());
            String className = "com.helix.compiled.asm." + (debug ? "AsmDebugRule_" : "AsmRule_")
                    + sanitizeName(rule.getName()) + "_" + classCounter.incrementAndGet();
            return generateBytecode(className, rule, ast, debug);
        } catch (ExpressionParseException e) {
            throw new BytecodeGenerationException("Failed to generate bytecode: " + e.getMessage(), e);
        }
    }

    public byte[] generateBytecode(String className, Rule rule, ExpressionNode ast, boolean debug) throws BytecodeGenerationException {
        Objects.requireNonNull(className, "className cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(ast, "ast cannot be null");

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String internalName = className.replace('.', '/');

        // public class <className> implements CompiledRule
        cw.visit(V21, ACC_PUBLIC | ACC_SUPER, internalName, null, "java/lang/Object",
                new String[]{"com/helix/api/CompiledRule"});

        // Fields
        cw.visitField(ACC_PRIVATE | ACC_FINAL, "name", "Ljava/lang/String;", null, null).visitEnd();
        cw.visitField(ACC_PRIVATE | ACC_FINAL, "version", "Ljava/lang/String;", null, null).visitEnd();
        cw.visitField(ACC_PRIVATE | ACC_FINAL, "astRoot", "Lcom/helix/core/parser/ast/ExpressionNode;", null, null).visitEnd();

        // Constructor
        generateConstructor(cw, internalName);
        generateGetters(cw, internalName);

        // Execute method
        generateExecuteMethod(cw, internalName, ast, debug);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateConstructor(ClassWriter cw, String internalName) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/String;Ljava/lang/String;Lcom/helix/core/parser/ast/ExpressionNode;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, internalName, "name", "Ljava/lang/String;");

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitFieldInsn(PUTFIELD, internalName, "version", "Ljava/lang/String;");

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitFieldInsn(PUTFIELD, internalName, "astRoot", "Lcom/helix/core/parser/ast/ExpressionNode;");

        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 4);
        mv.visitEnd();
    }

    private void generateGetters(ClassWriter cw, String internalName) {
        MethodVisitor mv1 = cw.visitMethod(ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null);
        mv1.visitCode();
        mv1.visitVarInsn(ALOAD, 0);
        mv1.visitFieldInsn(GETFIELD, internalName, "name", "Ljava/lang/String;");
        mv1.visitInsn(ARETURN);
        mv1.visitMaxs(1, 1);
        mv1.visitEnd();

        MethodVisitor mv2 = cw.visitMethod(ACC_PUBLIC, "getVersion", "()Ljava/lang/String;", null, null);
        mv2.visitCode();
        mv2.visitVarInsn(ALOAD, 0);
        mv2.visitFieldInsn(GETFIELD, internalName, "version", "Ljava/lang/String;");
        mv2.visitInsn(ARETURN);
        mv2.visitMaxs(1, 1);
        mv2.visitEnd();
    }

    private void generateExecuteMethod(ClassWriter cw, String internalName, ExpressionNode astRoot, boolean debug) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "execute", "(Lcom/helix/api/ExecutionContext;)Lcom/helix/api/ExecutionResult;", null, null);
        mv.visitCode();

        // long startTime = System.nanoTime(); (slot 2)
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
        mv.visitVarInsn(LSTORE, 2);

        // AstEvaluator evaluator = new AstEvaluator(context); (slot 4)
        mv.visitTypeInsn(NEW, "com/helix/core/bytecode/AstEvaluator");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, "com/helix/core/bytecode/AstEvaluator", "<init>", "(Lcom/helix/api/ExecutionContext;)V", false);
        mv.visitVarInsn(ASTORE, 4);

        // Instrument each condition clause prior to branch jumps only in debug mode
        if (debug) {
            List<ConditionClause> clauses = extractClauses(astRoot);
            for (ConditionClause clause : clauses) {
                emitClauseEvaluation(mv, internalName, clause);
            }
        }

        // Object result = evaluator.evaluate(this.astRoot); (slot 5)
        mv.visitVarInsn(ALOAD, 4);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, "astRoot", "Lcom/helix/core/parser/ast/ExpressionNode;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "com/helix/core/bytecode/AstEvaluator", "evaluate", "(Lcom/helix/core/parser/ast/ExpressionNode;)Ljava/lang/Object;", false);
        mv.visitVarInsn(ASTORE, 5);

        // long duration = System.nanoTime() - startTime; (slot 6)
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
        mv.visitVarInsn(LLOAD, 2);
        mv.visitInsn(LSUB);
        mv.visitVarInsn(LSTORE, 6);

        // return ExecutionResult.success(result, duration);
        mv.visitVarInsn(ALOAD, 5);
        mv.visitVarInsn(LLOAD, 6);
        mv.visitMethodInsn(INVOKESTATIC, "com/helix/api/ExecutionResult", "success", "(Ljava/lang/Object;J)Lcom/helix/api/ExecutionResult;", false);
        mv.visitInsn(ARETURN);

        mv.visitMaxs(10, 12);
        mv.visitEnd();
    }

    private void emitClauseEvaluation(MethodVisitor mv, String internalName, ConditionClause clause) {
        // Evaluate left operand in slot 8
        mv.visitVarInsn(ALOAD, 4); // evaluator
        emitNodeReference(mv, internalName, clause.getIndex(), true);
        mv.visitMethodInsn(INVOKEVIRTUAL, "com/helix/core/bytecode/AstEvaluator", "evaluate", "(Lcom/helix/core/parser/ast/ExpressionNode;)Ljava/lang/Object;", false);
        mv.visitVarInsn(ASTORE, 8);

        // Evaluate right operand in slot 9
        mv.visitVarInsn(ALOAD, 4); // evaluator
        emitNodeReference(mv, internalName, clause.getIndex(), false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "com/helix/core/bytecode/AstEvaluator", "evaluate", "(Lcom/helix/core/parser/ast/ExpressionNode;)Ljava/lang/Object;", false);
        mv.visitVarInsn(ASTORE, 9);

        // Compare operands to produce boolean outcome in slot 10
        mv.visitVarInsn(ALOAD, 8);
        mv.visitVarInsn(ALOAD, 9);
        mv.visitLdcInsn(clause.getOperator());
        mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/bytecode/BytecodeCompiler", "compareOperands", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Z", false);
        mv.visitVarInsn(ISTORE, 10);

        // Emits: INVOKESTATIC DebugHook.onCondition prior to branch jump instructions (IFNE)
        pushInt(mv, clause.getIndex());
        mv.visitLdcInsn(clause.getDescription());
        mv.visitVarInsn(ALOAD, 8); // left
        mv.visitVarInsn(ALOAD, 9); // right
        mv.visitVarInsn(ILOAD, 10); // outcome
        mv.visitVarInsn(ALOAD, 1);  // context
        mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/debug/DebugHook", "onCondition",
                "(ILjava/lang/String;Ljava/lang/Object;Ljava/lang/Object;ZLcom/helix/api/ExecutionContext;)V", false);

        // Branch jump instruction: IFNE
        Label trueLabel = new Label();
        Label endLabel = new Label();
        mv.visitVarInsn(ILOAD, 10);
        mv.visitJumpInsn(IFNE, trueLabel);
        mv.visitJumpInsn(GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitLabel(endLabel);
    }

    private void emitNodeReference(MethodVisitor mv, String internalName, int clauseIndex, boolean isLeft) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, "astRoot", "Lcom/helix/core/parser/ast/ExpressionNode;");
        pushInt(mv, clauseIndex);
        mv.visitInsn(isLeft ? ICONST_1 : ICONST_0);
        mv.visitMethodInsn(INVOKESTATIC, "com/helix/core/bytecode/BytecodeCompiler", "lookupClauseNode",
                "(Lcom/helix/core/parser/ast/ExpressionNode;IZ)Lcom/helix/core/parser/ast/ExpressionNode;", false);
    }

    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    public static ExpressionNode lookupClauseNode(ExpressionNode root, int clauseIndex, boolean isLeft) {
        List<ConditionClause> list = extractClausesStatic(root);
        if (clauseIndex >= 0 && clauseIndex < list.size()) {
            ConditionClause clause = list.get(clauseIndex);
            return isLeft ? clause.getLeftNode() : clause.getRightNode();
        }
        return root;
    }

    public static boolean compareOperands(Object left, Object right, String op) {
        if (left == null || right == null) {
            return "==".equals(op) ? Objects.equals(left, right) : !Objects.equals(left, right);
        }
        if (left instanceof Number n1 && right instanceof Number n2) {
            double d1 = n1.doubleValue();
            double d2 = n2.doubleValue();
            return switch (op) {
                case ">" -> d1 > d2;
                case "<" -> d1 < d2;
                case ">=" -> d1 >= d2;
                case "<=" -> d1 <= d2;
                case "==" -> Double.compare(d1, d2) == 0;
                case "!=" -> Double.compare(d1, d2) != 0;
                default -> false;
            };
        }
        if (left instanceof Comparable && right instanceof Comparable && left.getClass().equals(right.getClass())) {
            @SuppressWarnings("unchecked")
            int cmp = ((Comparable<Object>) left).compareTo(right);
            return switch (op) {
                case ">" -> cmp > 0;
                case "<" -> cmp < 0;
                case ">=" -> cmp >= 0;
                case "<=" -> cmp <= 0;
                case "==" -> cmp == 0;
                case "!=" -> cmp != 0;
                default -> false;
            };
        }
        return switch (op) {
            case "==" -> Objects.equals(left, right);
            case "!=" -> !Objects.equals(left, right);
            default -> false;
        };
    }

    /**
     * Extracts inspectable condition clauses from an AST expression tree.
     */
    public List<ConditionClause> extractClauses(ExpressionNode root) {
        return extractClausesStatic(root);
    }

    public static List<ConditionClause> extractClausesStatic(ExpressionNode root) {
        List<ConditionClause> list = new ArrayList<>();
        collectClauses(root, list);
        return list;
    }

    private static void collectClauses(ExpressionNode node, List<ConditionClause> list) {
        if (node == null) return;

        if (node instanceof RuleNode rn) {
            collectClauses(rn.getRootNode(), list);
            return;
        }

        if (node instanceof ComparisonNode comp) {
            int index = list.size();
            list.add(new ConditionClause(index, comp.toString().replaceAll("[()]", ""), comp.getOperator().getSymbol(), comp.getLeft(), comp.getRight()));
            return;
        }

        if (node instanceof BinaryOpNode bin) {
            collectClauses(bin.getLeft(), list);
            collectClauses(bin.getRight(), list);
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
