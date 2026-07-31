package com.helix.core.bytecode;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Low-level ASM class builder for generating JVM bytecode structures.
 */
public class AsmClassBuilder implements Opcodes {

    private final ClassWriter classWriter;
    private final String classNameInternal;

    public AsmClassBuilder(String className) {
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        this.classNameInternal = className.replace('.', '/');

        // Define class: public class <className> implements CompiledRule
        classWriter.visit(
                V17,
                ACC_PUBLIC | ACC_SUPER,
                classNameInternal,
                null,
                "java/lang/Object",
                new String[]{"com/helix/api/CompiledRule"}
        );

        // Fields
        classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "name", "Ljava/lang/String;", null, null).visitEnd();
        classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "version", "Ljava/lang/String;", null, null).visitEnd();
        classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "astRoot", "Lcom/helix/core/parser/ast/ExpressionNode;", null, null).visitEnd();

        generateConstructor();
        generateGetName();
        generateGetVersion();
    }

    private void generateConstructor() {
        MethodVisitor mv = classWriter.visitMethod(
                ACC_PUBLIC,
                "<init>",
                "(Ljava/lang/String;Ljava/lang/String;Lcom/helix/core/parser/ast/ExpressionNode;)V",
                null,
                null
        );
        mv.visitCode();

        // super()
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        // this.name = name
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, classNameInternal, "name", "Ljava/lang/String;");

        // this.version = version
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitFieldInsn(PUTFIELD, classNameInternal, "version", "Ljava/lang/String;");

        // this.astRoot = astRoot
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitFieldInsn(PUTFIELD, classNameInternal, "astRoot", "Lcom/helix/core/parser/ast/ExpressionNode;");

        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 4);
        mv.visitEnd();
    }

    private void generateGetName() {
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, classNameInternal, "name", "Ljava/lang/String;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private void generateGetVersion() {
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, "getVersion", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, classNameInternal, "version", "Ljava/lang/String;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    public MethodVisitor createExecuteMethodVisitor() {
        return classWriter.visitMethod(
                ACC_PUBLIC,
                "execute",
                "(Lcom/helix/api/ExecutionContext;)Lcom/helix/api/ExecutionResult;",
                null,
                null
        );
    }

    public byte[] toByteArray() {
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    public String getClassNameInternal() {
        return classNameInternal;
    }
}
