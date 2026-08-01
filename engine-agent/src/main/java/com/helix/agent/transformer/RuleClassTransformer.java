package com.helix.agent.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * ClassFileTransformer targeting rule execution classes and transforming bytecode via ASM.
 */
public class RuleClassTransformer implements ClassFileTransformer {

    private static final Logger log = LoggerFactory.getLogger(RuleClassTransformer.class);
    private final String targetPackagePrefix;

    public RuleClassTransformer() {
        this("com/helix");
    }

    public RuleClassTransformer(String targetPackagePrefix) {
        this.targetPackagePrefix = targetPackagePrefix != null ? targetPackagePrefix.replace('.', '/') : "com/helix";
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null || !className.startsWith(targetPackagePrefix)) {
            return null; // Skip non-matching classes
        }

        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            RuleInstrumentationVisitor visitor = new RuleInstrumentationVisitor(Opcodes.ASM9, writer, className);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            log.error("Failed to transform class {}", className, t);
            throw new TransformationException("ASM transformation failed for class " + className, t);
        }
    }
}
