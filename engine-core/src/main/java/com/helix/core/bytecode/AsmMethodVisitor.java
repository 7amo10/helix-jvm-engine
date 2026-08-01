package com.helix.core.bytecode;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Low-level ASM helper for emitting stack bytecode instructions.
 */
public class AsmMethodVisitor implements Opcodes {

    private final MethodVisitor mv;

    public AsmMethodVisitor(MethodVisitor mv) {
        this.mv = mv;
    }

    public void emitLoadConstant(Object val) {
        if (val instanceof Integer i) {
            emitIntConst(i);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (val instanceof Long l) {
            mv.visitLdcInsn(l);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        } else if (val instanceof Double d) {
            mv.visitLdcInsn(d);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        } else if (val instanceof Boolean b) {
            mv.visitInsn(b ? ICONST_1 : ICONST_0);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        } else if (val instanceof String s) {
            mv.visitLdcInsn(s);
        } else {
            mv.visitLdcInsn(val.toString());
        }
    }

    public void emitIntConst(int val) {
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
}
