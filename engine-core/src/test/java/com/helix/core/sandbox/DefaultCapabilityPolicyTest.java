package com.helix.core.sandbox;

import com.helix.api.sandbox.CapabilityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultCapabilityPolicy Specification")
class DefaultCapabilityPolicyTest {

    private DefaultCapabilityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DefaultCapabilityPolicy();
    }

    @Nested
    @DisplayName("Allowed Operations")
    class AllowedOperationsTests {

        @ParameterizedTest(name = "Math operation: {0}")
        @ValueSource(strings = {"abs", "min", "max", "sqrt", "pow", "round", "ceil", "floor"})
        @DisplayName("Allows standard java.lang.Math operations")
        void testAllowsMathOperations(String method) {
            assertDoesNotThrow(() -> policy.checkMethodCall("java.lang.Math", method));
            assertTrue(policy.isPermitted("java.lang.Math", method));
        }

        @ParameterizedTest(name = "String operation: {0}")
        @ValueSource(strings = {"length", "equals", "equalsIgnoreCase", "substring", "contains", "startsWith", "toLowerCase", "trim", "matches"})
        @DisplayName("Allows standard java.lang.String operations")
        void testAllowsStringOperations(String method) {
            assertDoesNotThrow(() -> policy.checkMethodCall("java.lang.String", method));
            assertTrue(policy.isPermitted("java.lang.String", method));
        }

        @ParameterizedTest(name = "Numeric wrapper: {0}")
        @CsvSource({
                "java.lang.Integer, parseInt",
                "java.lang.Integer, valueOf",
                "java.lang.Double, parseDouble",
                "java.lang.Double, valueOf",
                "java.lang.Long, parseLong",
                "java.lang.Boolean, parseBoolean",
                "java.math.BigDecimal, valueOf",
                "java.math.BigInteger, valueOf"
        })
        @DisplayName("Allows numeric and boolean wrapper utility methods")
        void testAllowsNumericWrappers(String className, String method) {
            assertDoesNotThrow(() -> policy.checkMethodCall(className, method));
            assertTrue(policy.isPermitted(className, method));
        }

        @ParameterizedTest(name = "Collection method: {0}.{1}")
        @CsvSource({
                "java.util.List, size",
                "java.util.List, get",
                "java.util.List, contains",
                "java.util.Map, get",
                "java.util.Map, containsKey",
                "java.util.Set, contains",
                "java.util.Arrays, binarySearch",
                "java.util.Objects, equals",
                "java.util.Objects, requireNonNull"
        })
        @DisplayName("Allows safe collection and array utilities")
        void testAllowsSafeCollections(String className, String method) {
            assertDoesNotThrow(() -> policy.checkMethodCall(className, method));
            assertTrue(policy.isPermitted(className, method));
        }

        @ParameterizedTest(name = "Time method: {0}.{1}")
        @CsvSource({
                "java.time.Instant, now",
                "java.time.LocalDate, now",
                "java.time.Duration, ofSeconds",
                "java.time.LocalDateTime, parse"
        })
        @DisplayName("Allows java.time temporal data access")
        void testAllowsJavaTime(String className, String method) {
            assertDoesNotThrow(() -> policy.checkMethodCall(className, method));
            assertTrue(policy.isPermitted(className, method));
        }

        @Test
        @DisplayName("Allows safe non-mutating System timestamp access")
        void testAllowsSafeSystemTimestamp() {
            assertDoesNotThrow(() -> policy.checkMethodCall("java.lang.System", "currentTimeMillis"));
            assertDoesNotThrow(() -> policy.checkMethodCall("java.lang.System", "nanoTime"));
        }
    }

    @Nested
    @DisplayName("Forbidden Operations - System Calls & Runtime")
    class ForbiddenSystemTests {

        @ParameterizedTest(name = "Forbidden System call: {0}")
        @ValueSource(strings = {"exit", "gc", "runFinalization", "load", "loadLibrary", "setSecurityManager", "setProperty", "setProperties", "clearProperty"})
        @DisplayName("Blocks dangerous java.lang.System method calls")
        void testBlocksDangerousSystemCalls(String method) {
            CapabilityViolationException ex = assertThrows(
                    CapabilityViolationException.class,
                    () -> policy.checkMethodCall("java.lang.System", method)
            );
            assertEquals("java.lang.System", ex.getViolatingClass());
            assertEquals(method, ex.getViolatingMethod());
            assertFalse(policy.isPermitted("java.lang.System", method));
        }

        @ParameterizedTest(name = "Runtime call: {0}")
        @ValueSource(strings = {"exec", "getRuntime", "halt", "load", "loadLibrary", "addShutdownHook"})
        @DisplayName("Blocks all java.lang.Runtime invocations")
        void testBlocksRuntimeCalls(String method) {
            assertThrows(CapabilityViolationException.class, () -> policy.checkMethodCall("java.lang.Runtime", method));
            assertFalse(policy.isPermitted("java.lang.Runtime", method));
        }

        @Test
        @DisplayName("Blocks java.lang.ProcessBuilder completely")
        void testBlocksProcessBuilder() {
            assertThrows(CapabilityViolationException.class, () -> policy.checkMethodCall("java.lang.ProcessBuilder", "start"));
            assertThrows(CapabilityViolationException.class, () -> policy.checkPackageAccess("java.lang.ProcessBuilder"));
        }
    }

    @Nested
    @DisplayName("Forbidden Operations - File I/O, Network & Reflection")
    class ForbiddenPackageTests {

        @ParameterizedTest(name = "Blocked package class: {0}")
        @ValueSource(strings = {
                "java.io.File",
                "java.io.FileOutputStream",
                "java.io.FileInputStream",
                "java.nio.file.Files",
                "java.nio.file.Paths",
                "java.net.Socket",
                "java.net.ServerSocket",
                "java.net.URL",
                "java.net.http.HttpClient",
                "java.lang.reflect.Method",
                "java.lang.reflect.Field",
                "java.lang.reflect.Constructor",
                "java.lang.invoke.MethodHandles",
                "java.lang.Thread",
                "java.util.concurrent.Executors",
                "java.util.concurrent.ForkJoinPool",
                "java.lang.ClassLoader",
                "java.lang.instrument.Instrumentation",
                "java.security.AccessController",
                "javax.script.ScriptEngineManager",
                "sun.misc.Unsafe",
                "jdk.internal.misc.Unsafe"
        })
        @DisplayName("Blocks dangerous package prefixes and internal JVM APIs")
        void testBlocksDangerousPackages(String className) {
            CapabilityViolationException ex = assertThrows(
                    CapabilityViolationException.class,
                    () -> policy.checkMethodCall(className, "anyMethod")
            );
            assertEquals(className, ex.getViolatingClass());
            assertEquals("anyMethod", ex.getViolatingMethod());
            assertFalse(policy.isPermitted(className, "anyMethod"));
        }

        @ParameterizedTest(name = "Class reflection: {0}")
        @ValueSource(strings = {"forName", "getDeclaredMethod", "getDeclaredField", "newInstance", "getClassLoader"})
        @DisplayName("Blocks reflective methods on java.lang.Class")
        void testBlocksClassReflection(String method) {
            assertThrows(CapabilityViolationException.class, () -> policy.checkMethodCall("java.lang.Class", method));
            assertFalse(policy.isPermitted("java.lang.Class", method));
        }
    }

    @Nested
    @DisplayName("Customization and Builder API")
    class CustomizationTests {

        @Test
        @DisplayName("Builder allows adding custom blocked classes and custom permitted prefixes")
        void testCustomPolicyBuilder() {
            DefaultCapabilityPolicy customPolicy = DefaultCapabilityPolicy.builder()
                    .blockPrefix("com.mycompany.internal.")
                    .blockMethod("java.lang.Math", "random")
                    .allowPrefix("java.io.ByteArrayInputStream")
                    .build();

            // Custom blocked method
            assertThrows(CapabilityViolationException.class,
                    () -> customPolicy.checkMethodCall("java.lang.Math", "random"));

            // Custom blocked prefix
            assertThrows(CapabilityViolationException.class,
                    () -> customPolicy.checkMethodCall("com.mycompany.internal.SecretService", "execute"));

            // Custom allowed prefix overriding default block
            assertDoesNotThrow(() -> customPolicy.checkMethodCall("java.io.ByteArrayInputStream", "read"));

            // Standard rules still apply
            assertThrows(CapabilityViolationException.class,
                    () -> customPolicy.checkMethodCall("java.lang.System", "exit"));
        }
    }
}
