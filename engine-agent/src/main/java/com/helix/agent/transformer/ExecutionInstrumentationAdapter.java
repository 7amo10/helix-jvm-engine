package com.helix.agent.transformer;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * Method visitor using AdviceAdapter to inject entry and exit hooks into rule methods.
 */
public class ExecutionInstrumentationAdapter extends AdviceAdapter {

    private final String className;
    private final String methodName;

    public ExecutionInstrumentationAdapter(int api, MethodVisitor methodVisitor, int access, String name, String descriptor, String className) {
        super(api, methodVisitor, access, name, descriptor);
        this.className = className;
        this.methodName = name;
    }

    @Override
    protected void onMethodEnter() {
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("[HelixAgent] Entering " + className + "." + methodName);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    }

    @Override
    protected void onMethodExit(int opcode) {
        if (opcode != Opcodes.ATHROW) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("[HelixAgent] Exiting " + className + "." + methodName);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }
    }
}
