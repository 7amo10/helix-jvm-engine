package com.helix.core.classloader;

import com.helix.core.bytecode.AsmClassBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.*;

class RuleClassLoaderTest implements Opcodes {

    private ClassLoaderMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new ClassLoaderMetrics();
    }

    private byte[] createDummyClassBytecode(String className) {
        String internalName = className.replace('.', '/');
        AsmClassBuilder builder = new AsmClassBuilder(className);
        MethodVisitor mv = builder.createExecuteMethodVisitor();
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1); // context
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();
        return builder.toByteArray();
    }

    @Test
    @DisplayName("Should define rule class from raw bytecode successfully")
    void testDefineRuleClass() throws Exception {
        RuleClassLoader classLoader = new RuleClassLoader("loader-1", getClass().getClassLoader(), metrics);

        byte[] bytecode = createDummyClassBytecode("com.helix.generated.SimpleLoaderRule");

        Class<?> definedClass = classLoader.defineRule("com.helix.generated.SimpleLoaderRule", bytecode);
        assertNotNull(definedClass);
        assertEquals("com.helix.generated.SimpleLoaderRule", definedClass.getName());

        assertTrue(classLoader.getLoadedClasses().containsKey("com.helix.generated.SimpleLoaderRule"));
        assertEquals(1, metrics.getTotalClassesLoaded());
        assertEquals(1, metrics.getActiveClassLoaders());

        classLoader.close();
        assertEquals(0, metrics.getActiveClassLoaders());
        assertEquals(1, metrics.getTotalClassLoadersClosed());
    }

    @Test
    @DisplayName("Should throw ClassLoadingException when defining rule on closed ClassLoader")
    void testDefineRuleOnClosedClassLoader() {
        RuleClassLoader classLoader = new RuleClassLoader("loader-2", getClass().getClassLoader(), metrics);
        classLoader.close();
        assertTrue(classLoader.isClosed());

        assertThrows(ClassLoadingException.class, () -> classLoader.defineRule("com.helix.generated.ClosedRule", new byte[]{1, 2, 3}));
    }

    @Test
    @DisplayName("Should allow ClassLoader GC unloading when unreferenced after close")
    void testClassLoaderUnloadingWithWeakReference() throws Exception {
        RuleClassLoader loader = new RuleClassLoader("gc-loader", getClass().getClassLoader(), metrics);

        byte[] bytecode = createDummyClassBytecode("com.helix.generated.GcRule");

        Class<?> clazz = loader.defineRule("com.helix.generated.GcRule", bytecode);
        assertNotNull(clazz);

        WeakReference<RuleClassLoader> weakRef = new WeakReference<>(loader);
        loader.close();
        loader = null;
        clazz = null;

        // Force GC sweeps
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(20);
        }

        assertNull(weakRef.get(), "RuleClassLoader should be garbage collected after close and reference clearing");
    }
}
