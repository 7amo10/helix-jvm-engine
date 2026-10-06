package com.helix.core.sandbox;

import com.helix.core.classloader.ClassLoadingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SandboxClassLoader Specification")
class SandboxClassLoaderTest {

    private SandboxClassLoader classLoader;

    @BeforeEach
    void setUp() {
        classLoader = new SandboxClassLoader("test-sandbox-loader", getClass().getClassLoader());
    }

    @AfterEach
    void tearDown() {
        if (classLoader != null && !classLoader.isClosed()) {
            classLoader.close();
        }
    }

    @Test
    @DisplayName("Allows loading standard safe utility classes")
    void testAllowsLoadingSafeClasses() throws Exception {
        Class<?> stringClass = classLoader.loadClass("java.lang.String");
        assertNotNull(stringClass);
        assertEquals("java.lang.String", stringClass.getName());

        Class<?> mathClass = classLoader.loadClass("java.lang.Math");
        assertNotNull(mathClass);

        Class<?> listClass = classLoader.loadClass("java.util.List");
        assertNotNull(listClass);
    }

    @Test
    @DisplayName("Blocks restricted classes by throwing ClassNotFoundException")
    void testBlocksRestrictedClasses() {
        assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass("java.lang.ProcessBuilder"));
        assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass("java.lang.Runtime"));
        assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass("java.io.FileOutputStream"));
        assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass("java.net.Socket"));
        assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass("java.lang.reflect.Method"));
    }

    @Test
    @DisplayName("Defines and instantiates dynamic rule bytecode cleanly")
    void testDefinesRuleBytecode() throws Exception {
        com.helix.core.bytecode.AsmClassBuilder builder = new com.helix.core.bytecode.AsmClassBuilder("test.TestSandboxRule");
        org.objectweb.asm.MethodVisitor mv = builder.createExecuteMethodVisitor();
        mv.visitCode();
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
        mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
        mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();
        byte[] simpleClassBytecode = builder.toByteArray();

        Class<?> defined = classLoader.defineRule("test.TestSandboxRule", simpleClassBytecode);
        assertNotNull(defined);
        assertEquals("test.TestSandboxRule", defined.getName());
        assertEquals(classLoader, defined.getClassLoader());
    }

    @Test
    @DisplayName("Tracking metrics and close lifecycle behaviour")
    void testMetricsAndClose() {
        assertNotNull(classLoader.getMetrics());
        assertFalse(classLoader.isClosed());

        classLoader.close();
        assertTrue(classLoader.isClosed());

        assertThrows(ClassLoadingException.class, () ->
                classLoader.defineRule("test.ClosedRule", new byte[]{1, 2, 3}));
    }
}
