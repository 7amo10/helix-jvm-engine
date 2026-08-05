package com.helix.experiments.metaspace;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

/**
 * Generator that creates unique dynamic classes in individual custom ClassLoaders.
 */
public class DynamicClassGenerator {

    private final ByteBuddy byteBuddy;

    public DynamicClassGenerator() {
        this.byteBuddy = new ByteBuddy();
    }

    /**
     * Dynamically generates a new unique class loaded in a fresh isolated URLClassLoader.
     */
    public GeneratedClassResult generateDynamicClass() {
        String uniqueClassName = "com.helix.generated.DynamicRule_" + UUID.randomUUID().toString().replace("-", "");
        URLClassLoader customLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());

        try {
            Class<?> loadedClass = byteBuddy
                    .subclass(Object.class)
                    .name(uniqueClassName)
                    .method(ElementMatchers.named("toString"))
                    .intercept(FixedValue.value("DynamicRuleInstance"))
                    .make()
                    .load(customLoader)
                    .getLoaded();

            return new GeneratedClassResult(loadedClass, customLoader);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate dynamic class: " + uniqueClassName, e);
        }
    }

    public record GeneratedClassResult(Class<?> generatedClass, URLClassLoader classLoader) {}
}
