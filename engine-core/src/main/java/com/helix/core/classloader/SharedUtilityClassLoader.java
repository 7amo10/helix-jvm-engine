package com.helix.core.classloader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton shared ClassLoader for common utility classes and shared dependencies.
 *
 * <p>Serves as the parent ClassLoader for all {@link RuleClassLoader} instances to ensure
 * shared classes (such as API contracts, Jackson nodes, and utility classes) are loaded
 * once in Metaspace, avoiding redundant class definitions and reducing memory overhead.</p>
 */
public class SharedUtilityClassLoader extends URLClassLoader {

    private static final Set<String> SHARED_PACKAGES = Collections.unmodifiableSet(Set.of(
            "com.helix.api",
            "com.helix.core.parser.ast",
            "com.fasterxml.jackson",
            "org.slf4j"
    ));

    private final Set<String> sharedLoadedClassNames = ConcurrentHashMap.newKeySet();

    private SharedUtilityClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    private static class Holder {
        private static final SharedUtilityClassLoader INSTANCE =
                new SharedUtilityClassLoader(SharedUtilityClassLoader.class.getClassLoader());
    }

    /**
     * Retrieves the singleton instance of {@link SharedUtilityClassLoader}.
     *
     * @return singleton SharedUtilityClassLoader instance
     */
    public static SharedUtilityClassLoader getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Checks if a given class name falls under the packages shared by this ClassLoader.
     *
     * @param className FQCN to check
     * @return true if the class belongs to a shared package prefix
     */
    public boolean isSharedPackage(String className) {
        if (className == null) {
            return false;
        }
        for (String pkg : SHARED_PACKAGES) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isSharedPackage(name)) {
            sharedLoadedClassNames.add(name);
        }
        return super.loadClass(name, resolve);
    }

    public Set<String> getSharedLoadedClassNames() {
        return Collections.unmodifiableSet(sharedLoadedClassNames);
    }

    public Set<String> getSharedPackages() {
        return SHARED_PACKAGES;
    }
}
