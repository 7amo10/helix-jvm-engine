package com.helix.agent.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * Class visitor intercepting methods of rule classes to inject instrumentation adapters.
 */
public class RuleInstrumentationVisitor extends ClassVisitor {

    private final String className;

    public RuleInstrumentationVisitor(int api, ClassVisitor classVisitor, String className) {
        super(api, classVisitor);
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (mv != null && ("execute".equals(name) || "evaluate".equals(name))) {
            return new ExecutionInstrumentationAdapter(api, mv, access, name, descriptor, className);
        }
        return mv;
    }
}
