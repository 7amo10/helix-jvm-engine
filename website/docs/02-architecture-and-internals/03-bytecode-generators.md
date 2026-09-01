---
id: bytecode-generators
title: ByteBuddy vs ASM Generators
sidebar_position: 3
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Bytecode Generators: ByteBuddy vs ASM

Helix provides two swappable bytecode generator implementations: **ByteBuddy** (`ByteBuddyRuleGenerator`) and **ASM** (`AsmRuleGenerator`).

---

## Technical Comparison Matrix

| Architectural Dimension | ByteBuddy Generator | ASM Generator |
|---|---|---|
| **Compilation Latency** | `~5.1 ms` / rule | `~1.7 ms` / rule (**3x faster**) |
| **Generated Class Size** | `~1,180 bytes` | `~640 bytes` (**45% smaller**) |
| **Metaspace Impact** | Higher initial metadata | Minimal class footprint |
| **Safety & Verification** | Fluent high-level type safety | Direct low-level opcode emission |
| **Hotspot Inlining Friendly** | High | Maximum |
| **Recommended Use Case** | Default for general enterprise rules | High-frequency bulk compilation (>1,000 rules/sec) |

---

## Generator Implementations

<Tabs>
  <TabItem value="bytebuddy" label="ByteBuddy Generator" default>

```java
public class ByteBuddyRuleGenerator implements RuleGenerator {
    @Override
    public byte[] generateBytecode(String className, ASTNode root, Map<String, Class<?>> schema) {
        return new ByteBuddy()
            .subclass(CompiledRule.class)
            .name(className)
            .method(ElementMatchers.named("eval"))
            .intercept(new AstByteBuddyImplementation(root, schema))
            .make()
            .getBytes();
    }
}
```

  </TabItem>
  <TabItem value="asm" label="ASM Generator (Low-Level Opcodes)">

```java
public class AsmRuleGenerator implements RuleGenerator {
    @Override
    public byte[] generateBytecode(String className, ASTNode root, Map<String, Class<?>> schema) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, 
                 className.replace('.', '/'), null, "java/lang/Object", 
                 new String[]{"com/helix/api/CompiledRule"});

        // Emit constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Emit eval(ExecutionContext) bytecode
        MethodVisitor evalMv = cw.visitMethod(Opcodes.ACC_PUBLIC, "eval", 
            "(Lcom/helix/api/ExecutionContext;)Z", null, null);
        evalMv.visitCode();
        
        // Traverse AST and emit arithmetic / logic opcodes
        emitAstNode(evalMv, root, schema);

        evalMv.visitInsn(Opcodes.IRETURN);
        evalMv.visitMaxs(0, 0);
        evalMv.visitEnd();
        cw.visitEnd();

        return cw.toByteArray();
    }
}
```

  </TabItem>
</Tabs>

---

## When to Choose Which Generator?

- **Use ByteBuddy When:** You want standard safety, readable stack traces, and standard runtime rule generation.
- **Use ASM When:** You are bulk compiling thousands of dynamic rules per second, or running in memory-constrained environments where Metaspace and bytecode class size must be minimized.
