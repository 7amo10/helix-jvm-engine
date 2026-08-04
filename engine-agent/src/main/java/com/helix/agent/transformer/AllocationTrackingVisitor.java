package com.helix.agent.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM ClassVisitor intercepting NEW, NEWARRAY, and ANEWARRAY bytecode instructions to inject allocation callbacks.
 */
public class AllocationTrackingVisitor extends ClassVisitor {

    private final String className;

    public AllocationTrackingVisitor(int api, ClassVisitor classVisitor, String className) {
        super(api, classVisitor);
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (mv == null) {
            return null;
        }
        return new MethodVisitor(api, mv) {
            @Override
            public void visitTypeInsn(int opcode, String type) {
                super.visitTypeInsn(opcode, type);
                if (opcode == Opcodes.NEW || opcode == Opcodes.ANEWARRAY) {
                    mv.visitLdcInsn(type);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/helix/agent/transformer/AllocationInterceptor",
                            "onAllocation",
                            "(Ljava/lang/String;)V",
                            false);
                }
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                super.visitIntInsn(opcode, operand);
                if (opcode == Opcodes.NEWARRAY) {
                    mv.visitLdcInsn("PrimitiveArray");
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/helix/agent/transformer/AllocationInterceptor",
                            "onAllocation",
                            "(Ljava/lang/String;)V",
                            false);
                }
            }
        };
    }
}
