package com.helix.api.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CapabilityPolicy SPI Contract Specification")
class CapabilityPolicyTest {

    @Test
    @DisplayName("CapabilityPolicy implementation enforces checkMethodCall contract")
    void testCheckMethodCallContract() {
        CapabilityPolicy policy = (className, methodName) -> {
            if ("java.lang.System".equals(className) && "exit".equals(methodName)) {
                throw new CapabilityViolationException(className, methodName);
            }
        };

        assertDoesNotThrow(() -> policy.checkMethodCall("java.lang.Math", "max"));
        assertThrows(CapabilityViolationException.class,
                () -> policy.checkMethodCall("java.lang.System", "exit"));
    }

    @Test
    @DisplayName("Default isPermitted method returns boolean without throwing exception")
    void testIsPermittedDefaultMethod() {
        CapabilityPolicy policy = (className, methodName) -> {
            if ("java.lang.Runtime".equals(className)) {
                throw new CapabilityViolationException(className, methodName);
            }
        };

        assertTrue(policy.isPermitted("java.lang.String", "length"));
        assertFalse(policy.isPermitted("java.lang.Runtime", "getRuntime"));
    }

    @Test
    @DisplayName("Default checkPackageAccess forwards to checkMethodCall with wildcard or package name")
    void testCheckPackageAccessDefaultMethod() {
        CapabilityPolicy policy = (className, methodName) -> {
            if (className.startsWith("java.net.")) {
                throw new CapabilityViolationException(className, methodName);
            }
        };

        assertDoesNotThrow(() -> policy.checkPackageAccess("java.util"));
        assertThrows(CapabilityViolationException.class,
                () -> policy.checkPackageAccess("java.net.Socket"));
    }
}
