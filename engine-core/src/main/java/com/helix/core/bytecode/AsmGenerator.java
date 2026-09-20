package com.helix.core.bytecode;

/**
 * Low-level ASM bytecode generator for high-performance rule compilation.
 *
 * <p>Subclasses {@link AsmBytecodeGenerator} for backwards compatibility with existing pipelines.</p>
 */
public class AsmGenerator extends AsmBytecodeGenerator {

    public AsmGenerator() {
        super();
    }
}
