package com.helix.api;

import java.util.Map;

/**
 * Utility helper class for building mock objects and test instances.
 */
public final class TestUtils {

    private TestUtils() {
    }

    public static Rule createSampleRule(String name, String expression) {
        return new Rule() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public String getDescription() {
                return "Test rule description";
            }

            @Override
            public String getExpression() {
                return expression;
            }

            @Override
            public Map<String, Class<?>> getInputSchema() {
                return Map.of("x", Integer.class, "y", Integer.class);
            }
        };
    }
}
