# JVM Scripting Engine & Profiler - Design Specification

**Date:** 2026-07-30  
**Status:** Approved  
**Author:** Design Session

## Executive Summary

A comprehensive, production-grade JVM internals exploration platform that combines a dynamic rules engine with deep profiling capabilities. This project enables hands-on learning of class loading, bytecode generation, JIT compilation, and garbage collection through a professionally structured multi-module Maven application.

## Project Goals

1. **Educational Excellence** - Provide a practical platform to understand JVM internals (class loading, bytecode, JIT, GC)
2. **Production Quality** - Follow industry best practices for code organization, testing, and observability
3. **Experimental Framework** - Enable controlled experiments to observe JVM behavior under various conditions
4. **Performance Analysis** - Integrate profiling tools (async-profiler, JFR, JMX) for deep performance insights

## System Overview

The system consists of five main modules:

- **engine-api**: Public contracts and interfaces (dependency-free)
- **engine-core**: Rules engine with bytecode generation and execution
- **engine-profiler**: JIT/GC monitoring and profiling tools
- **engine-agent**: Java Agent for instrumentation and memory analysis
- **engine-experiments**: Test scenarios, benchmarks, and JVM experiments

## High-Level Architecture

```
┌─────────────────────────────────────────────────┐
│  API Layer (engine-api)                         │
│  RuleEngine, Profiler, Agent interfaces         │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│  Core Engine (engine-core)                      │
│  ┌──────────────┐  ┌──────────────┐             │
│  │ JSON Parser  │→ │ ByteBuddy    │→ Bytecode   │
│  │              │  │ Generator    │             │
│  └──────────────┘  └──────────────┘             │
│                                                 │
│  ┌──────────────────────────────────────────┐   │
│  │ Hierarchical ClassLoader Manager         │   │
│  └──────────────────────────────────────────┘   │
│                                                 │
│  ┌──────────────────────────────────────────┐   │
│  │ Execution Engine (sync/async/batch)      │   │
│  └──────────────────────────────────────────┘   │
│                                                 │
│  ┌──────────────────────────────────────────┐   │
│  │ Tiered Cache (L1:Strong/L2:Soft/L3:Weak) │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
                      ↓
┌────────────────────────────────────────────────┐
│  Profiler Module (engine-profiler)             │
│  ┌──────────────┐  ┌──────────────┐            │
│  │ JIT Monitor  │  │ GC Analyzer  │            │
│  └──────────────┘  └──────────────┘            │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │ async-profiler Integration               │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │ JFR Custom Events                        │  │
│  └──────────────────────────────────────────┘  │
└────────────────────────────────────────────────┘
                      ↓
┌────────────────────────────────────────────────┐
│  Java Agent (engine-agent) - Separate JAR      │
│  ┌──────────────┐  ┌──────────────┐            │
│  │ Instrumenta- │  │ JOL          │            │
│  │ tion API     │  │ Integration  │            │
│  └──────────────┘  └──────────────┘            │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │ ASM Transformer (low-level intercepts)   │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │ JMX MBeans (runtime control)             │  │
│  └──────────────────────────────────────────┘  │
└────────────────────────────────────────────────┘
```

## Technology Stack

- **Build System:** Maven 3.9+
- **Java Version:** JDK 17+ (LTS)
- **Bytecode Generation:** ByteBuddy 1.14+, ASM 9.5+
- **Profiling:** async-profiler 2.9+, JOL (Java Object Layout) 0.17+
- **Testing:** JUnit 5, JMH (Java Microbenchmark Harness)
- **Observability:** SLF4J + Logback, Micrometer, JMX, JFR
- **Cache:** Caffeine 3.1+ (for tiered implementation)

---


## 1. Project Structure & Module Organization

### 1.1 Maven Multi-Module Layout

```
jvm-scripting-engine/
├── pom.xml                          (parent POM)
│
├── engine-api/                      (public contracts)
│   ├── src/main/java/
│   │   └── com/jvm/engine/api/
│   │       ├── Rule.java
│   │       ├── RuleEngine.java
│   │       ├── ExecutionContext.java
│   │       ├── ExecutionResult.java
│   │       └── profiler/
│   │           ├── Profiler.java
│   │           └── ProfileEvent.java
│   └── pom.xml
│
├── engine-core/                     (main implementation)
│   ├── src/main/java/
│   │   └── com/jvm/engine/core/
│   │       ├── parser/              (JSON → AST)
│   │       │   ├── RuleParser.java
│   │       │   ├── JsonRuleLoader.java
│   │       │   └── ast/
│   │       │       ├── ExpressionNode.java
│   │       │       ├── BinaryOpNode.java
│   │       │       └── LiteralNode.java
│   │       ├── bytecode/            (bytecode generators)
│   │       │   ├── ByteBuddyGenerator.java
│   │       │   ├── AsmGenerator.java
│   │       │   └── BytecodeOptimizer.java
│   │       ├── classloader/         (hierarchical loaders)
│   │       │   ├── RuleClassLoader.java
│   │       │   ├── SharedUtilityClassLoader.java
│   │       │   └── ClassLoaderManager.java
│   │       ├── executor/            (sync/async/batch)
│   │       │   ├── RuleExecutor.java
│   │       │   ├── SyncExecutor.java
│   │       │   ├── AsyncExecutor.java
│   │       │   └── BatchExecutor.java
│   │       ├── cache/               (tiered cache)
│   │       │   ├── TieredRuleCache.java
│   │       │   ├── CacheTier.java
│   │       │   └── ReferenceManager.java
│   │       └── events/              (internal event bus)
│   │           ├── EventBus.java
│   │           └── EngineEvent.java
│   ├── src/test/java/
│   │   ├── unit/
│   │   ├── integration/
│   │   └── experiments/
│   └── pom.xml
│
├── engine-profiler/                 (JIT/GC monitoring)
│   ├── src/main/java/
│   │   └── com/jvm/engine/profiler/
│   │       ├── jit/
│   │       │   ├── JitCompilationMonitor.java
│   │       │   ├── CompilationEvent.java
│   │       │   └── TieredCompilationTracker.java
│   │       ├── gc/
│   │       │   ├── GcLogAnalyzer.java
│   │       │   ├── GcEvent.java
│   │       │   └── GcStatistics.java
│   │       ├── async/
│   │       │   ├── AsyncProfilerIntegration.java
│   │       │   └── FlameGraphGenerator.java
│   │       ├── jfr/
│   │       │   ├── CustomJfrEvents.java
│   │       │   └── JfrRecordingManager.java
│   │       └── interactive/
│   │           ├── ProfilerUI.java
│   │           └── LiveDashboard.java
│   ├── src/main/resources/
│   │   └── jfr/
│   │       └── engine-events.jfc
│   └── pom.xml
│
├── engine-agent/                    (Java agent)
│   ├── src/main/java/
│   │   └── com/jvm/engine/agent/
│   │       ├── AgentMain.java
│   │       ├── transformer/
│   │       │   ├── RuleClassTransformer.java
│   │       │   └── AllocationTracker.java
│   │       ├── jol/
│   │       │   ├── MemoryAnalyzer.java
│   │       │   └── ObjectLayoutInspector.java
│   │       └── jmx/
│   │           ├── EngineControlMBean.java
│   │           └── ProfilerControlMBean.java
│   ├── src/main/resources/
│   │   └── META-INF/
│   │       └── MANIFEST.MF
│   └── pom.xml
│
├── engine-experiments/              (test scenarios)
│   ├── src/main/java/
│   │   └── com/jvm/engine/experiments/
│   │       ├── scenarios/
│   │       │   ├── MetaspaceLeakExperiment.java
│   │       │   ├── JitCompilationExperiment.java
│   │       │   ├── GcStressExperiment.java
│   │       │   ├── ObjectLayoutExperiment.java
│   │       │   └── SafepointExperiment.java
│   │       └── benchmarks/
│   │           ├── CompilationBenchmark.java
│   │           ├── ExecutionBenchmark.java
│   │           ├── CacheBenchmark.java
│   │           └── ClassLoaderBenchmark.java
│   └── pom.xml
│
└── README.md
```

### 1.2 Module Dependencies

```
engine-api         (no dependencies - pure interfaces)
     ↑
     │
engine-core        (depends on: engine-api, ByteBuddy, ASM, Caffeine)
     ↑
     │
engine-profiler    (depends on: engine-api, engine-core, async-profiler)
     ↑
     │
engine-agent       (depends on: engine-api, ASM, JOL)
     │
     ↓
engine-experiments (depends on: all modules, JMH)
```

### 1.3 Key Design Decisions

1. **engine-api is dependency-free** - Users depend only on interfaces without implementation dependencies
2. **engine-agent builds to a fat JAR** - Shades ASM and JOL to avoid classpath conflicts during attachment
3. **engine-experiments is optional** - Not required for production, only for learning and benchmarking
4. **Profiler is separate from core** - Can be disabled or swapped with alternative profiling implementations
5. **Clear package boundaries** - Each module maps to a specific JVM concept (class loading, bytecode, JIT, GC)

### 1.4 Parent POM Configuration

Key aspects of the parent POM:

- Java 17+ language level
- Maven Compiler Plugin 3.11+
- Maven Shade Plugin for engine-agent fat JAR
- Dependency management for shared libraries
- Common plugin configurations (Surefire, Failsafe)
- Profile for JMH benchmark execution

---


## 2. Core Engine - Rule Parsing & Bytecode Generation

### 2.1 JSON Rule Format

Rules are defined in JSON format with strong typing and full expression language support:

```json
{
  "ruleName": "fraudDetection",
  "version": "1.0",
  "description": "Detects potentially fraudulent transactions",
  "input": {
    "amount": "double",
    "country": "String",
    "userId": "long",
    "timestamp": "long"
  },
  "expressions": [
    {
      "type": "condition",
      "expression": "amount > 1000 && country.equals(\"US\")"
    },
    {
      "type": "action",
      "expression": "return \"FLAGGED\""
    }
  ],
  "metadata": {
    "priority": "high",
    "category": "fraud"
  }
}
```

### 2.2 Processing Pipeline

```
JSON → JSONParser → AST → ExpressionCompiler → ByteBuddy → Class Definition → ClassLoader → Executable Rule
```

**Pipeline Stages:**

1. **JSON Parsing** - Parse JSON into intermediate representation, validate schema
2. **AST Construction** - Build Abstract Syntax Tree from expression strings
3. **Type Checking** - Validate types and resolve method references
4. **Bytecode Generation** - Convert AST to JVM bytecode using ByteBuddy
5. **Class Loading** - Load generated class via custom ClassLoader
6. **Instantiation** - Create executable rule instance

### 2.3 Expression Language Support

**Operators:**
- Arithmetic: `+`, `-`, `*`, `/`, `%`
- Comparison: `>`, `<`, `>=`, `<=`, `==`, `!=`
- Logical: `&&`, `||`, `!`
- Bitwise: `&`, `|`, `^`, `~`, `<<`, `>>`

**String Operations:**
- `equals(String)` - Exact match
- `equalsIgnoreCase(String)` - Case-insensitive match
- `contains(String)` - Substring check
- `matches(String)` - Regex matching
- `startsWith(String)`, `endsWith(String)`

**Collection Operations:**
- `in` - Membership test (`value in [1, 2, 3]`)
- `contains` - Collection contains element
- `size()` - Collection size
- `isEmpty()` - Empty check

**Functions:**
- Math functions: `Math.abs()`, `Math.max()`, `Math.min()`, `Math.sqrt()`
- String functions: `String.format()`, `String.valueOf()`
- Custom functions: User-defined functions registered in context

**Variables:**
- Input variables from rule definition
- Local variable assignment: `var temp = amount * 1.1`
- Context variables from ExecutionContext

### 2.4 AST Node Types

```java
// Base node
public interface ExpressionNode {
    Class<?> getType();
    Object evaluate(ExecutionContext context);
}

// Binary operations
public class BinaryOpNode implements ExpressionNode {
    private ExpressionNode left;
    private ExpressionNode right;
    private BinaryOperator operator; // +, -, *, /, &&, ||, etc.
}

// Literals
public class LiteralNode implements ExpressionNode {
    private Object value;
    private Class<?> type;
}

// Variable references
public class VariableNode implements ExpressionNode {
    private String variableName;
}

// Method calls
public class MethodCallNode implements ExpressionNode {
    private ExpressionNode target;
    private String methodName;
    private List<ExpressionNode> arguments;
}
```

### 2.5 ByteBuddy vs ASM Usage

**ByteBuddy (Primary - 90% of use cases):**
- High-level rule compilation
- Method generation from AST
- Field injection
- Interface implementation
- Annotation processing

**Example ByteBuddy Usage:**
```java
Class<?> ruleClass = new ByteBuddy()
    .subclass(CompiledRule.class)
    .name("GeneratedRule_" + ruleName)
    .method(named("execute"))
    .intercept(MethodDelegation.to(new ExecutionInterceptor(ast)))
    .make()
    .load(classLoader, ClassLoadingStrategy.Default.INJECTION)
    .getLoaded();
```

**ASM (Experimental - 10% for low-level control):**
- Manual stack manipulation experiments
- Custom bytecode patterns
- Bytecode size tuning for JIT experiments
- Direct opcode manipulation

**Example ASM Usage:**
```java
ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
MethodVisitor mv = cw.visitMethod(
    ACC_PUBLIC, "execute", "()Ljava/lang/Object;", null, null);
mv.visitCode();
// Manual bytecode generation
mv.visitLdcInsn("result");
mv.visitInsn(ARETURN);
mv.visitMaxs(1, 1);
mv.visitEnd();
```

### 2.6 Bytecode Optimization

**Optimizations Applied:**
1. **Constant Folding** - Evaluate constants at compile time
2. **Dead Code Elimination** - Remove unreachable code
3. **Inline Constants** - Replace variable reads with constant values where possible
4. **Method Inlining Hints** - Keep methods small (<35 bytes) to encourage JIT inlining
5. **Stack Optimization** - Minimize local variable usage

### 2.7 Error Handling

**Compile-Time Errors:**
- Syntax errors in expressions → `RuleSyntaxException`
- Type mismatches → `TypeCheckException`
- Undefined variables → `UndefinedVariableException`
- Bytecode generation failures → `BytecodeGenerationException`

**Runtime Errors:**
- Execution exceptions wrapped in `RuleExecutionException`
- Timeout handling for long-running rules
- Stack overflow protection
- ClassLoader errors → `ClassLoadingException`

---


## 3. Hierarchical ClassLoader Strategy

### 3.1 ClassLoader Hierarchy

```
Bootstrap ClassLoader (JVM core classes)
         ↓
Platform/Extension ClassLoader (JDK modules)
         ↓
Application ClassLoader (application classes)
         ↓
┌────────────────────────────────────────────┐
│ SharedUtilityClassLoader                   │
│ (common rule utilities, shared deps)       │
└────────────────────────────────────────────┘
         ↓
    ┌────┴────┬────────┬────────┐
    ↓         ↓        ↓        ↓
RuleLoader1 RuleLoader2 ... RuleLoaderN
(isolated   (isolated       (isolated
 rule 1)     rule 2)         rule N)
```

### 3.2 ClassLoader Implementations

**RuleClassLoader:**
```java
public class RuleClassLoader extends URLClassLoader implements AutoCloseable {
    private final String ruleId;
    private final Set<String> loadedClasses = new HashSet<>();
    private final ClassLoaderMetrics metrics;
    
    public RuleClassLoader(String ruleId, ClassLoader parent) {
        super(new URL[0], parent);
        this.ruleId = ruleId;
    }
    
    public Class<?> defineRule(String className, byte[] bytecode) {
        loadedClasses.add(className);
        metrics.recordClassLoad(className, bytecode.length);
        return defineClass(className, bytecode, 0, bytecode.length);
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        metrics.recordClassLoaderClosed(ruleId, loadedClasses.size());
    }
}
```

**SharedUtilityClassLoader:**
```java
public class SharedUtilityClassLoader extends URLClassLoader {
    private static final SharedUtilityClassLoader INSTANCE = 
        new SharedUtilityClassLoader();
    
    private SharedUtilityClassLoader() {
        super(new URL[0], ClassLoader.getSystemClassLoader());
        // Load common utilities once
        loadSharedDependencies();
    }
    
    public static SharedUtilityClassLoader getInstance() {
        return INSTANCE;
    }
}
```

**ClassLoaderManager:**
```java
public class ClassLoaderManager {
    private final Map<String, RuleClassLoader> activeLoaders = 
        new ConcurrentHashMap<>();
    private final SharedUtilityClassLoader sharedLoader = 
        SharedUtilityClassLoader.getInstance();
    
    public RuleClassLoader createLoaderForRule(String ruleId, 
                                                IsolationMode mode) {
        ClassLoader parent = switch (mode) {
            case ISOLATED -> sharedLoader;
            case SHARED -> getOrCreateSharedLoader();
            case HIERARCHICAL -> determineParentFromRuleFamily(ruleId);
        };
        
        RuleClassLoader loader = new RuleClassLoader(ruleId, parent);
        activeLoaders.put(ruleId, loader);
        return loader;
    }
    
    public void closeLoader(String ruleId) {
        RuleClassLoader loader = activeLoaders.remove(ruleId);
        if (loader != null) {
            loader.close();
            // Loader now eligible for GC along with loaded classes
        }
    }
}
```

### 3.3 Class Loading Strategies

**Isolated Mode (Default):**
- Each rule gets its own RuleClassLoader
- Complete isolation between rules
- Best for Metaspace experiments
- Maximum safety, highest memory usage

**Shared Mode:**
- Multiple rules share one loader
- Lower memory footprint
- Useful for caching experiments
- Rules cannot be unloaded independently

**Hierarchical Mode:**
- Rule families share parent loaders
- Balance between isolation and efficiency
- Example: all "fraud" rules share a FraudRuleClassLoader parent

### 3.4 Metaspace Management

**Monitoring Metaspace Usage:**
```java
public class MetaspaceMonitor {
    private final MemoryPoolMXBean metaspacePool;
    
    public MetaspaceMonitor() {
        this.metaspacePool = ManagementFactory.getMemoryPoolMXBeans()
            .stream()
            .filter(pool -> pool.getName().contains("Metaspace"))
            .findFirst()
            .orElseThrow();
    }
    
    public MetaspaceStats getStats() {
        MemoryUsage usage = metaspacePool.getUsage();
        return new MetaspaceStats(
            usage.getUsed(),
            usage.getCommitted(),
            usage.getMax(),
            calculateUtilization(usage)
        );
    }
}
```

### 3.5 Metaspace Leak Experiments

**Scenario 1: Intentional Leak**
```java
public void demonstrateLeak() {
    List<RuleClassLoader> leakedLoaders = new ArrayList<>();
    
    for (int i = 0; i < 5000; i++) {
        RuleClassLoader loader = new RuleClassLoader("rule_" + i, sharedLoader);
        String className = "GeneratedRule_" + i;
        byte[] bytecode = generateBytecode(className);
        
        // Load class
        Class<?> ruleClass = loader.defineRule(className, bytecode);
        
        // MISTAKE: Keep loader reference → prevents GC
        leakedLoaders.add(loader);
        
        if (i % 100 == 0) {
            logMetaspaceUsage();
        }
    }
    // Result: OutOfMemoryError: Metaspace
}
```

**Scenario 2: Proper Cleanup**
```java
public void demonstrateCleanup() {
    for (int i = 0; i < 5000; i++) {
        try (RuleClassLoader loader = new RuleClassLoader("rule_" + i, sharedLoader)) {
            String className = "GeneratedRule_" + i;
            byte[] bytecode = generateBytecode(className);
            
            Class<?> ruleClass = loader.defineRule(className, bytecode);
            // Execute rule
            Object instance = ruleClass.getDeclaredConstructor().newInstance();
            
        } // AutoCloseable: loader.close() called here
        
        if (i % 500 == 0) {
            System.gc(); // Request collection
            Thread.sleep(100); // Give GC time
            logMetaspaceUsage(); // Observe stable or decreasing usage
        }
    }
}
```

### 3.6 Class Unloading Verification

**Monitoring Class Unloading:**
```bash
# JVM flags for class unloading visibility
-XX:+TraceClassUnloading
-Xlog:class+unload=info

# Expected output:
[info][class,unload] unloading class GeneratedRule_123 0x00007f8a4c001000
```

**Programmatic Verification:**
```java
public void verifyClassUnloading() {
    WeakReference<ClassLoader> loaderRef;
    WeakReference<Class<?>> classRef;
    
    {
        RuleClassLoader loader = new RuleClassLoader("test", sharedLoader);
        Class<?> ruleClass = loader.defineRule("TestRule", bytecode);
        
        loaderRef = new WeakReference<>(loader);
        classRef = new WeakReference<>(ruleClass);
        
        // Clear strong references
        loader = null;
        ruleClass = null;
    }
    
    // Force GC
    System.gc();
    System.gc(); // May need multiple cycles
    
    // Verify collection
    assertNull(loaderRef.get(), "ClassLoader should be collected");
    assertNull(classRef.get(), "Class should be unloaded");
}
```

### 3.7 ClassLoader Leak Detection

**Warning Signs:**
- Metaspace usage continuously grows
- Number of active ClassLoaders doesn't decrease
- `java.lang.OutOfMemoryError: Metaspace`

**Detection Mechanism:**
```java
public class ClassLoaderLeakDetector {
    private final Map<String, Long> loaderCreationTimes = new ConcurrentHashMap<>();
    
    public void checkForLeaks() {
        long now = System.currentTimeMillis();
        int activeLoaders = classLoaderManager.getActiveLoaderCount();
        
        if (activeLoaders > EXPECTED_THRESHOLD) {
            List<String> oldLoaders = loaderCreationTimes.entrySet().stream()
                .filter(e -> now - e.getValue() > MAX_LIFETIME_MS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            if (!oldLoaders.isEmpty()) {
                logger.warn("Potential ClassLoader leak detected: {} old loaders", 
                           oldLoaders.size());
                // Emit JFR event
                emitLeakDetectionEvent(oldLoaders);
            }
        }
    }
}
```

---


## 4. Execution Engine - Sync, Async & Batch

### 4.1 Execution Modes

**Synchronous Execution:**
```java
public interface SyncExecutor {
    ExecutionResult execute(CompiledRule rule, ExecutionContext context);
}

// Usage
ExecutionResult result = ruleEngine.execute(rule, context);
// Blocks until completion
```

**Asynchronous Execution:**
```java
public interface AsyncExecutor {
    CompletableFuture<ExecutionResult> executeAsync(
        CompiledRule rule, ExecutionContext context);
}

// Usage
CompletableFuture<ExecutionResult> future = ruleEngine.executeAsync(rule, context);
future.thenAccept(result -> processResult(result))
      .exceptionally(ex -> handleError(ex));
```

**Batch Execution:**
```java
public interface BatchExecutor {
    List<ExecutionResult> executeBatch(
        CompiledRule rule, List<ExecutionContext> contexts);
    
    Stream<ExecutionResult> executeBatchStream(
        CompiledRule rule, Stream<ExecutionContext> contexts, int parallelism);
}

// Usage
List<ExecutionResult> results = ruleEngine.executeBatch(rule, contexts);
```

### 4.2 Executor Architecture

```java
public interface RuleExecutor {
    ExecutionResult execute(CompiledRule rule, ExecutionContext context);
}

public class SyncExecutor implements RuleExecutor {
    @Override
    public ExecutionResult execute(CompiledRule rule, ExecutionContext context) {
        long startTime = System.nanoTime();
        try {
            Object result = rule.evaluate(context);
            long duration = System.nanoTime() - startTime;
            return ExecutionResult.success(result, duration);
        } catch (Exception e) {
            return ExecutionResult.failure(e, System.nanoTime() - startTime);
        }
    }
}

public class AsyncExecutor implements RuleExecutor {
    private final ExecutorService executorService;
    
    public CompletableFuture<ExecutionResult> executeAsync(
            CompiledRule rule, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> 
            syncExecutor.execute(rule, context), executorService);
    }
}

public class BatchExecutor implements RuleExecutor {
    private final ForkJoinPool forkJoinPool;
    
    public List<ExecutionResult> executeBatch(
            CompiledRule rule, List<ExecutionContext> contexts) {
        return contexts.parallelStream()
            .map(ctx -> syncExecutor.execute(rule, ctx))
            .collect(Collectors.toList());
    }
}
```

### 4.3 Thread Pool Configuration

```java
public class ExecutorConfiguration {
    
    public ExecutorService createRuleExecutorPool() {
        int coreThreads = Runtime.getRuntime().availableProcessors();
        int maxThreads = coreThreads * 2;
        long keepAliveTime = 60L;
        
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("rule-executor-%d")
            .setDaemon(false)
            .setPriority(Thread.NORM_PRIORITY)
            .setUncaughtExceptionHandler(this::handleUncaughtException)
            .build();
        
        return new ThreadPoolExecutor(
            coreThreads,
            maxThreads,
            keepAliveTime,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
```

### 4.4 Execution Context

```java
public class ExecutionContext {
    private final Map<String, Object> variables;
    private final ClassLoader classLoader;
    private final ExecutionMetrics metrics;
    private final Optional<ProfilingSession> profilingSession;
    private final long timeoutMs;
    
    public ExecutionContext() {
        this.variables = new ConcurrentHashMap<>();
        this.classLoader = Thread.currentThread().getContextClassLoader();
        this.metrics = new ExecutionMetrics();
        this.profilingSession = Optional.empty();
        this.timeoutMs = DEFAULT_TIMEOUT_MS;
    }
    
    public ExecutionContext set(String key, Object value) {
        variables.put(key, value);
        return this;
    }
    
    public <T> T get(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value == null) return null;
        return type.cast(value);
    }
}
```

### 4.5 Execution Result

```java
public class ExecutionResult {
    private final boolean success;
    private final Object value;
    private final Exception error;
    private final long executionTimeNs;
    private final ExecutionMetrics metrics;
    
    public static ExecutionResult success(Object value, long executionTimeNs) {
        return new ExecutionResult(true, value, null, executionTimeNs);
    }
    
    public static ExecutionResult failure(Exception error, long executionTimeNs) {
        return new ExecutionResult(false, null, error, executionTimeNs);
    }
    
    public long getExecutionTimeMs() {
        return TimeUnit.NANOSECONDS.toMillis(executionTimeNs);
    }
}
```

### 4.6 Error Handling & Resilience

**Timeout Handling:**
```java
public class TimeoutExecutor implements RuleExecutor {
    private final ExecutorService executorService;
    private final long defaultTimeoutMs;
    
    @Override
    public ExecutionResult execute(CompiledRule rule, ExecutionContext context) {
        Future<ExecutionResult> future = executorService.submit(() -> 
            syncExecutor.execute(rule, context));
        
        try {
            long timeout = context.getTimeoutMs().orElse(defaultTimeoutMs);
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ExecutionResult.failure(
                new RuleExecutionException("Rule execution timeout"), 0);
        }
    }
}
```

**Circuit Breaker:**
```java
public class CircuitBreakerExecutor implements RuleExecutor {
    private final Map<String, CircuitBreaker> circuitBreakers = 
        new ConcurrentHashMap<>();
    
    @Override
    public ExecutionResult execute(CompiledRule rule, ExecutionContext context) {
        CircuitBreaker breaker = circuitBreakers.computeIfAbsent(
            rule.getName(), k -> new CircuitBreaker());
        
        if (breaker.isOpen()) {
            return ExecutionResult.failure(
                new CircuitOpenException("Circuit breaker open for " + rule.getName()), 
                0);
        }
        
        try {
            ExecutionResult result = syncExecutor.execute(rule, context);
            if (result.isSuccess()) {
                breaker.recordSuccess();
            } else {
                breaker.recordFailure();
            }
            return result;
        } catch (Exception e) {
            breaker.recordFailure();
            throw e;
        }
    }
}
```

### 4.7 Concurrency Scenarios for JIT/GC Analysis

**High Contention Scenario:**
```java
// Many threads executing the same rule
// Tests JIT optimization for hot methods
public void highContentionWorkload() {
    CompiledRule hotRule = compileRule("hotRule");
    ExecutorService executor = Executors.newFixedThreadPool(16);
    
    for (int i = 0; i < 16; i++) {
        executor.submit(() -> {
            for (int j = 0; j < 100_000; j++) {
                hotRule.execute(context);
            }
        });
    }
    
    // Observe: rapid JIT compilation, high CPU usage, low GC pressure
}
```

**Low Contention Scenario:**
```java
// Each thread executes different rules
// Tests class loading and cache behavior
public void lowContentionWorkload() {
    List<CompiledRule> rules = IntStream.range(0, 1000)
        .mapToObj(i -> compileRule("rule_" + i))
        .collect(Collectors.toList());
    
    ExecutorService executor = Executors.newFixedThreadPool(16);
    
    for (CompiledRule rule : rules) {
        executor.submit(() -> rule.execute(context));
    }
    
    // Observe: high class loading activity, cache misses, varied JIT behavior
}
```

**Mixed Workload Scenario:**
```java
// Combination of hot and cold rules
// Tests tiered compilation and cache effectiveness
public void mixedWorkload() {
    CompiledRule hotRule = compileRule("hotRule");
    List<CompiledRule> coldRules = generateColdRules(100);
    
    ExecutorService executor = Executors.newFixedThreadPool(16);
    
    for (int i = 0; i < 16; i++) {
        executor.submit(() -> {
            for (int j = 0; j < 10_000; j++) {
                // 80% hot, 20% cold
                if (j % 5 == 0) {
                    coldRules.get(j % coldRules.size()).execute(context);
                } else {
                    hotRule.execute(context);
                }
            }
        });
    }
    
    // Observe: JIT focuses on hot rule, cold rules stay interpreted or C1
}
```

---


## 5. Tiered Cache - Strong, Soft & Weak References

### 5.1 Cache Architecture

```
┌─────────────────────────────────────────────────────┐
│              TieredRuleCache                        │
├─────────────────────────────────────────────────────┤
│  L1: Strong References (Hot Rules)                  │
│  - Fixed size LRU cache (default: 100 entries)      │
│  - Never GC'd while in cache                        │
│  - Fastest access, highest memory cost              │
├─────────────────────────────────────────────────────┤
│  L2: Soft References (Warm Rules)                   │
│  - Larger capacity (default: 1000 entries)          │
│  - GC'd only under memory pressure                  │
│  - Good balance of performance and memory           │
├─────────────────────────────────────────────────────┤
│  L3: Weak References (Cold Rules)                   │
│  - Unlimited size                                   │
│  - GC'd at next collection cycle if no strong refs  │
│  - Last resort before recompilation                 │
└─────────────────────────────────────────────────────┘
```

### 5.2 Cache Implementation

```java
public class TieredRuleCache {
    // L1: Strong references (Caffeine LRU cache)
    private final Cache<CacheKey, CompiledRule> l1Cache;
    
    // L2: Soft references
    private final Map<CacheKey, SoftReference<CompiledRule>> l2Cache;
    
    // L3: Weak references
    private final Map<CacheKey, WeakReference<CompiledRule>> l3Cache;
    
    // Reference queue for tracking GC'd entries
    private final ReferenceQueue<CompiledRule> refQueue;
    
    // Statistics
    private final CacheStatistics statistics;
    
    public TieredRuleCache(int l1Size, int l2Size) {
        this.l1Cache = Caffeine.newBuilder()
            .maximumSize(l1Size)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .evictionListener(this::onL1Eviction)
            .build();
        
        this.l2Cache = new ConcurrentHashMap<>(l2Size);
        this.l3Cache = new ConcurrentHashMap<>();
        this.refQueue = new ReferenceQueue<>();
        
        startReferenceQueueMonitor();
    }
    
    public Optional<CompiledRule> get(CacheKey key) {
        // Try L1 (strong refs)
        CompiledRule rule = l1Cache.getIfPresent(key);
        if (rule != null) {
            statistics.recordHit("L1");
            return Optional.of(rule);
        }
        
        // Try L2 (soft refs)
        SoftReference<CompiledRule> softRef = l2Cache.get(key);
        if (softRef != null) {
            rule = softRef.get();
            if (rule != null) {
                statistics.recordHit("L2");
                promoteToL1(key, rule);
                return Optional.of(rule);
            } else {
                // Soft ref cleared by GC
                l2Cache.remove(key);
                statistics.recordEviction("L2", "GC");
            }
        }
        
        // Try L3 (weak refs)
        WeakReference<CompiledRule> weakRef = l3Cache.get(key);
        if (weakRef != null) {
            rule = weakRef.get();
            if (rule != null) {
                statistics.recordHit("L3");
                promoteToL2(key, rule);
                return Optional.of(rule);
            } else {
                // Weak ref cleared by GC
                l3Cache.remove(key);
                statistics.recordEviction("L3", "GC");
            }
        }
        
        statistics.recordMiss();
        return Optional.empty();
    }
    
    public void put(CacheKey key, CompiledRule rule) {
        // Initially store in L3
        l3Cache.put(key, new WeakReference<>(rule, refQueue));
        statistics.recordCachePut("L3");
    }
}
```

### 5.3 Cache Key Design

```java
public class CacheKey {
    private final String ruleName;
    private final String ruleVersion;
    private final int inputSchemaHash;
    private final int hash;
    
    public CacheKey(String ruleName, String ruleVersion, Schema inputSchema) {
        this.ruleName = ruleName;
        this.ruleVersion = ruleVersion;
        this.inputSchemaHash = inputSchema.hashCode();
        this.hash = Objects.hash(ruleName, ruleVersion, inputSchemaHash);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheKey that)) return false;
        return inputSchemaHash == that.inputSchemaHash &&
               ruleName.equals(that.ruleName) &&
               ruleVersion.equals(that.ruleVersion);
    }
    
    @Override
    public int hashCode() {
        return hash;
    }
}
```

### 5.4 Promotion & Demotion Strategy

**Promotion Triggers:**
```java
public class PromotionPolicy {
    private final Map<CacheKey, AccessStats> accessStats = new ConcurrentHashMap<>();
    
    public boolean shouldPromoteFromL3ToL2(CacheKey key) {
        AccessStats stats = accessStats.get(key);
        return stats.getAccessCount() > 5 || stats.getRecentAccessRate() > 0.5;
    }
    
    public boolean shouldPromoteFromL2ToL1(CacheKey key) {
        AccessStats stats = accessStats.get(key);
        return stats.getAccessCount() > 20 || stats.isLatencyCritical();
    }
}
```

**Demotion Triggers:**
```java
public class DemotionPolicy {
    
    private void onL1Eviction(CacheKey key, CompiledRule rule, RemovalCause cause) {
        if (cause == RemovalCause.SIZE) {
            // Evicted due to size limit, demote to L2
            demoteToL2(key, rule);
        } else if (cause == RemovalCause.EXPIRED) {
            // Expired due to TTL, demote to L2
            demoteToL2(key, rule);
        }
    }
    
    private void demoteToL2(CacheKey key, CompiledRule rule) {
        l2Cache.put(key, new SoftReference<>(rule, refQueue));
        statistics.recordDemotion("L1", "L2");
    }
    
    private void demoteToL3(CacheKey key, CompiledRule rule) {
        l3Cache.put(key, new WeakReference<>(rule, refQueue));
        statistics.recordDemotion("L2", "L3");
    }
}
```

### 5.5 Reference Queue Monitoring

```java
private void startReferenceQueueMonitor() {
    Thread monitorThread = new Thread(() -> {
        while (running) {
            try {
                Reference<? extends CompiledRule> ref = refQueue.remove();
                handleClearedReference(ref);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }, "cache-ref-monitor");
    
    monitorThread.setDaemon(true);
    monitorThread.start();
}

private void handleClearedReference(Reference<? extends CompiledRule> ref) {
    // Determine which tier this reference belonged to
    CacheKey key = findKeyForReference(ref);
    if (key != null) {
        String tier = ref instanceof SoftReference ? "L2" : "L3";
        logger.debug("Rule evicted by GC from {}: {}", tier, key);
        statistics.recordEviction(tier, "GC");
        
        // Emit JFR event
        CacheEvictionEvent event = new CacheEvictionEvent();
        event.ruleName = key.getRuleName();
        event.tier = tier;
        event.reason = "GC";
        event.commit();
    }
}
```

### 5.6 GC Stress Experiments

**Experiment 1: Soft Reference Under Memory Pressure**
```java
public void softReferenceTest() {
    TieredRuleCache cache = new TieredRuleCache(100, 1000);
    
    // Fill L2 cache with soft references
    for (int i = 0; i < 1000; i++) {
        CacheKey key = new CacheKey("rule_" + i, "1.0", schema);
        CompiledRule rule = compileRule("rule_" + i);
        cache.put(key, rule);
        // Promote to L2
        cache.get(key); // L3 → L2
    }
    
    System.out.println("L2 size before pressure: " + cache.sizeL2());
    
    // Apply memory pressure
    List<byte[]> pressure = new ArrayList<>();
    try {
        while (true) {
            pressure.add(new byte[10 * 1024 * 1024]); // 10MB chunks
        }
    } catch (OutOfMemoryError e) {
        // Expected - heap exhausted
    }
    
    pressure.clear();
    System.gc();
    Thread.sleep(100);
    
    System.out.println("L2 size after pressure: " + cache.sizeL2());
    // Observation: Soft refs cleared to prevent OOM
}
```

**Experiment 2: Weak Reference Immediate Collection**
```java
public void weakReferenceTest() {
    TieredRuleCache cache = new TieredRuleCache(100, 1000);
    
    // Fill L3 cache with weak references
    for (int i = 0; i < 1000; i++) {
        CacheKey key = new CacheKey("rule_" + i, "1.0", schema);
        CompiledRule rule = compileRule("rule_" + i);
        cache.put(key, rule); // Goes to L3
    }
    
    System.out.println("L3 size before GC: " + cache.sizeL3());
    
    // Trigger GC
    System.gc();
    Thread.sleep(100);
    
    System.out.println("L3 size after GC: " + cache.sizeL3());
    // Observation: Most weak refs cleared (no strong refs exist)
}
```

**Experiment 3: SoftRefLRUPolicyMSPerMB Tuning**
```bash
# Default behavior: clear soft refs aggressively
java -XX:SoftRefLRUPolicyMSPerMB=0 SoftRefTest

# Keep soft refs longer (1 second per MB of free heap)
java -XX:SoftRefLRUPolicyMSPerMB=1000 SoftRefTest

# Keep soft refs even longer (10 seconds per MB)
java -XX:SoftRefLRUPolicyMSPerMB=10000 SoftRefTest
```

### 5.7 Cache Statistics

```java
public class CacheStatistics {
    private final LongAdder l1Hits = new LongAdder();
    private final LongAdder l2Hits = new LongAdder();
    private final LongAdder l3Hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    
    private final Map<String, LongAdder> evictions = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> promotions = new ConcurrentHashMap<>();
    
    public double getHitRate() {
        long totalHits = l1Hits.sum() + l2Hits.sum() + l3Hits.sum();
        long totalRequests = totalHits + misses.sum();
        return totalRequests == 0 ? 0.0 : (double) totalHits / totalRequests;
    }
    
    public double getL1HitRate() {
        long totalRequests = l1Hits.sum() + l2Hits.sum() + l3Hits.sum() + misses.sum();
        return totalRequests == 0 ? 0.0 : (double) l1Hits.sum() / totalRequests;
    }
    
    public CacheStatsSnapshot snapshot() {
        return new CacheStatsSnapshot(
            l1Hits.sum(),
            l2Hits.sum(),
            l3Hits.sum(),
            misses.sum(),
            getHitRate(),
            evictions.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()))
        );
    }
}
```

---


## 6. Java Agent - Instrumentation & Memory Analysis

### 6.1 Agent Entry Points

```java
public class JvmEngineAgent {
    private static Instrumentation instrumentation;
    private static AgentConfiguration config;
    
    /**
     * Entry point for -javaagent startup attachment
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        config = AgentConfiguration.parse(agentArgs);
        initialize();
    }
    
    /**
     * Entry point for runtime attachment via Attach API
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        config = AgentConfiguration.parse(agentArgs);
        initialize();
    }
    
    private static void initialize() {
        logger.info("JVM Engine Agent initializing...");
        
        // Register class transformer
        if (config.isTransformationEnabled()) {
            instrumentation.addTransformer(
                new RuleClassTransformer(), 
                true // retransformable
            );
        }
        
        // Register allocation tracker
        if (config.isAllocationTrackingEnabled()) {
            instrumentation.addTransformer(new AllocationTracker());
        }
        
        // Register JMX MBeans
        registerMBeans();
        
        // Start memory analyzer
        if (config.isMemoryAnalysisEnabled()) {
            MemoryAnalyzer.start(instrumentation);
        }
        
        logger.info("JVM Engine Agent initialized successfully");
    }
}
```

### 6.2 Class Transformation

**Rule Class Transformer:**
```java
public class RuleClassTransformer implements ClassFileTransformer {
    
    @Override
    public byte[] transform(ClassLoader loader,
                          String className,
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain,
                          byte[] classfileBuffer) {
        
        // Only transform generated rule classes
        if (!isRuleClass(className)) {
            return null; // No transformation
        }
        
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new RuleInstrumentationVisitor(cw);
            
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            
            byte[] transformed = cw.toByteArray();
            logger.debug("Transformed rule class: {}", className);
            return transformed;
            
        } catch (Exception e) {
            logger.error("Failed to transform class: {}", className, e);
            return null;
        }
    }
}
```

**Method Instrumentation Visitor:**
```java
public class RuleInstrumentationVisitor extends ClassVisitor {
    
    public RuleInstrumentationVisitor(ClassVisitor cv) {
        super(ASM9, cv);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                    String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // Instrument execute methods
        if (name.equals("execute")) {
            return new ExecutionInstrumentationAdapter(mv, access, name, descriptor);
        }
        
        return mv;
    }
}

public class ExecutionInstrumentationAdapter extends AdviceAdapter {
    
    @Override
    protected void onMethodEnter() {
        // Inject: AgentCallback.onMethodEntry(ruleName, methodName);
        push(getRuleName());
        push(getName());
        invokeStatic(AGENT_CALLBACK_TYPE, 
                    Method.getMethod("void onMethodEntry(String, String)"));
    }
    
    @Override
    protected void onMethodExit(int opcode) {
        // Inject: AgentCallback.onMethodExit(ruleName, methodName);
        push(getRuleName());
        push(getName());
        invokeStatic(AGENT_CALLBACK_TYPE,
                    Method.getMethod("void onMethodExit(String, String)"));
    }
}
```

### 6.3 Allocation Tracking

**Allocation Tracker Transformer:**
```java
public class AllocationTracker implements ClassFileTransformer {
    
    @Override
    public byte[] transform(ClassLoader loader, String className,
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain,
                          byte[] classfileBuffer) {
        
        if (!shouldTrackAllocations(className)) {
            return null;
        }
        
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = new AllocationTrackingVisitor(cw, className);
        
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }
}

public class AllocationTrackingVisitor extends ClassVisitor {
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                    String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new AllocationInterceptor(mv);
    }
}

public class AllocationInterceptor extends MethodVisitor {
    
    @Override
    public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, type);
        
        if (opcode == NEW) {
            // After NEW instruction, inject tracking call
            super.visitLdcInsn(type);
            super.visitMethodInsn(INVOKESTATIC,
                "com/jvm/engine/agent/AgentCallback",
                "recordAllocation",
                "(Ljava/lang/String;)V",
                false);
        }
    }
}
```

**Agent Callback:**
```java
public class AgentCallback {
    private static final ThreadLocal<ExecutionStats> stats = 
        ThreadLocal.withInitial(ExecutionStats::new);
    
    public static void onMethodEntry(String ruleName, String methodName) {
        ExecutionStats s = stats.get();
        s.recordMethodEntry(ruleName, methodName, System.nanoTime());
    }
    
    public static void onMethodExit(String ruleName, String methodName) {
        ExecutionStats s = stats.get();
        s.recordMethodExit(ruleName, methodName, System.nanoTime());
    }
    
    public static void recordAllocation(String typeName) {
        ExecutionStats s = stats.get();
        s.recordAllocation(typeName);
    }
}
```

### 6.4 Object Layout Analysis (JOL Integration)

```java
public class MemoryAnalyzer {
    private final Instrumentation instrumentation;
    
    public void analyzeRuleObject(Object ruleInstance) {
        // Print object layout with JOL
        String layout = ClassLayout.parseInstance(ruleInstance).toPrintable();
        logger.info("Object layout:\n{}", layout);
        
        // Calculate sizes
        long shallowSize = ClassLayout.parseInstance(ruleInstance).instanceSize();
        long deepSize = GraphLayout.parseInstance(ruleInstance).totalSize();
        long footprint = GraphLayout.parseInstance(ruleInstance).totalSize();
        
        // Compressed oops analysis
        boolean compressedOops = isCompressedOopsEnabled();
        int headerSize = compressedOops ? 12 : 16;
        int referenceSize = compressedOops ? 4 : 8;
        
        MemoryAnalysisReport report = new MemoryAnalysisReport(
            ruleInstance.getClass().getName(),
            shallowSize,
            deepSize,
            footprint,
            headerSize,
            referenceSize,
            compressedOops
        );
        
        logger.info("Memory analysis: {}", report);
        
        // Emit JFR event
        MemoryAnalysisEvent event = new MemoryAnalysisEvent();
        event.className = ruleInstance.getClass().getName();
        event.shallowSize = shallowSize;
        event.deepSize = deepSize;
        event.compressedOops = compressedOops;
        event.commit();
    }
    
    public boolean isCompressedOopsEnabled() {
        // Check via HotSpot diagnostic MBean
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
            Object value = server.invoke(objectName, "getVMOption",
                new Object[]{"UseCompressedOops"},
                new String[]{"java.lang.String"});
            return value.toString().contains("true");
        } catch (Exception e) {
            return false;
        }
    }
}
```

**JOL Analysis Example Output:**
```
com.jvm.engine.core.CompiledRuleImpl object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                VALUE
      0    12        (object header)            N/A
     12     4 String CompiledRuleImpl.name      N/A
     16     4 byte[] CompiledRuleImpl.bytecode  N/A
     20     4    int CompiledRuleImpl.version   N/A
Instance size: 24 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
```

### 6.5 JMX MBean Exposure

**Engine Control MBean:**
```java
@MXBean
public interface EngineControlMXBean {
    // Status
    String getEngineStatus();
    long getUptimeMs();
    
    // Statistics
    long getTotalRulesLoaded();
    long getActiveClassLoaders();
    Map<String, Long> getMemoryByClassLoader();
    Map<String, Long> getRuleStatistics();
    Map<String, Double> getCacheStatistics();
    
    // Control operations
    void clearCache(String tier);
    void reloadRule(String ruleName);
    void triggerGC();
    void dumpThreads();
    void dumpHeap(String fileName);
    
    // Configuration
    void setCacheSize(String tier, int size);
    void setExecutorThreads(int threads);
    
    // Memory analysis
    boolean isCompressedOopsEnabled();
    int getObjectHeaderSize();
    Map<String, Long> analyzeRuleMemory(String ruleName);
}
```

**Profiler Control MBean:**
```java
@MXBean
public interface ProfilerControlMXBean {
    // Profiling control
    void startCpuProfiling();
    void startAllocationProfiling();
    void startLockProfiling();
    void stopProfiling();
    String getProfilingStatus();
    
    // JIT monitoring
    Map<String, Integer> getCompilationLevels();
    List<String> getRecentlyCompiledMethods();
    Map<String, Long> getCompilationStatistics();
    
    // GC monitoring
    Map<String, Object> getGcStatistics();
    long getLastGcPauseTimeMs();
    double getAllocationRate();
}
```

**MBean Registration:**
```java
private static void registerMBeans() {
    try {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        
        ObjectName engineControl = new ObjectName("com.jvm.engine:type=EngineControl");
        mbs.registerMBean(new EngineControl(), engineControl);
        
        ObjectName profilerControl = new ObjectName("com.jvm.engine:type=ProfilerControl");
        mbs.registerMBean(new ProfilerControl(), profilerControl);
        
        logger.info("JMX MBeans registered successfully");
    } catch (Exception e) {
        logger.error("Failed to register JMX MBeans", e);
    }
}
```

### 6.6 Agent Configuration (MANIFEST.MF)

```
Manifest-Version: 1.0
Premain-Class: com.jvm.engine.agent.JvmEngineAgent
Agent-Class: com.jvm.engine.agent.JvmEngineAgent
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Can-Set-Native-Method-Prefix: false
Boot-Class-Path: jol-core-0.17.jar
Implementation-Version: 1.0.0
```

### 6.7 Agent Attachment Methods

**Static Attachment (Startup):**
```bash
java -javaagent:engine-agent.jar=config=agent.properties \
     -jar engine-core.jar
```

**Dynamic Attachment (Runtime):**
```java
public class AgentAttacher {
    
    public static void attachToCurrentJVM() throws Exception {
        String pid = ProcessHandle.current().pid();
        VirtualMachine vm = VirtualMachine.attach(String.valueOf(pid));
        
        String agentPath = "/path/to/engine-agent.jar";
        String options = "config=agent.properties";
        
        vm.loadAgent(agentPath, options);
        vm.detach();
        
        logger.info("Agent attached successfully to PID {}", pid);
    }
}
```

**Agent Configuration File (agent.properties):**
```properties
# Transformation
transformation.enabled=true
transformation.rules.only=true

# Allocation tracking
allocation.tracking.enabled=true
allocation.sampling.rate=0.01

# Memory analysis
memory.analysis.enabled=true
memory.analysis.interval.seconds=60

# JMX
jmx.enabled=true
jmx.port=9999
```

---


## 7. Profiler Module - JIT, GC & Flame Graphs

### 7.1 JIT Compilation Monitor

**Compilation Event Tracking:**
```java
public class JitCompilationMonitor {
    private final List<CompilationEventListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean monitoring = false;
    
    public void startMonitoring() {
        if (monitoring) return;
        monitoring = true;
        
        // Start process with JIT logging enabled
        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-XX:+PrintCompilation",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintInlining",
            "-XX:+LogCompilation",
            "-XX:LogFile=hotspot_compilation.log"
        );
        
        // Parse output in real-time
        new Thread(() -> parseCompilationLog()).start();
    }
    
    private void parseCompilationLog() {
        // Parse -XX:+PrintCompilation output format:
        //  timestamp compile_id tier method_name bytecode_size time
        Pattern pattern = Pattern.compile(
            "\\s*(\\d+)\\s+(\\d+)\\s+([0-4])\\s+([^\\s]+)\\s+\\((\\d+)\\s+bytes\\)");
        
        try (BufferedReader reader = Files.newBufferedReader(
                Paths.get("hotspot_compilation.log"))) {
            String line;
            while ((line = reader.readLine()) != null && monitoring) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    CompilationEvent event = parseEvent(matcher);
                    notifyListeners(event);
                }
            }
        }
    }
}

public record CompilationEvent(
    long timestamp,
    int compileId,
    int tier,               // 0=interpreted, 1-4=C1/C2
    String methodName,
    int bytecodeSize,
    long compilationTimeMs,
    boolean inlined,
    String inlineReason
) {}
```

**Tiered Compilation Tracker:**
```java
public class TieredCompilationTracker {
    private final Map<String, CompilationHistory> methodHistory = 
        new ConcurrentHashMap<>();
    
    public void recordCompilationEvent(CompilationEvent event) {
        methodHistory.computeIfAbsent(event.methodName(), 
            k -> new CompilationHistory())
            .addEvent(event);
    }
    
    public CompilationStats getStats() {
        Map<Integer, Long> countByTier = new HashMap<>();
        for (CompilationHistory history : methodHistory.values()) {
            int tier = history.getCurrentTier();
            countByTier.merge(tier, 1L, Long::sum);
        }
        
        return new CompilationStats(
            countByTier.getOrDefault(0, 0L), // Interpreted
            countByTier.getOrDefault(1, 0L), // C1 level 1
            countByTier.getOrDefault(2, 0L), // C1 level 2
            countByTier.getOrDefault(3, 0L), // C1 level 3
            countByTier.getOrDefault(4, 0L)  // C2
        );
    }
}

public class CompilationHistory {
    private final List<CompilationEvent> events = new ArrayList<>();
    
    public void addEvent(CompilationEvent event) {
        events.add(event);
    }
    
    public int getCurrentTier() {
        return events.isEmpty() ? 0 : events.get(events.size() - 1).tier();
    }
    
    public List<TierTransition> getTierTransitions() {
        List<TierTransition> transitions = new ArrayList<>();
        for (int i = 1; i < events.size(); i++) {
            CompilationEvent prev = events.get(i - 1);
            CompilationEvent curr = events.get(i);
            if (prev.tier() != curr.tier()) {
                transitions.add(new TierTransition(
                    prev.tier(), curr.tier(), 
                    curr.timestamp() - prev.timestamp()));
            }
        }
        return transitions;
    }
}
```

### 7.2 GC Log Analyzer

**GC Event Parsing:**
```java
public class GcLogAnalyzer {
    
    public List<GcEvent> parseLog(Path logFile) throws IOException {
        List<GcEvent> events = new ArrayList<>();
        
        // Parse -Xlog:gc*:file=gc.log:time,level,tags format
        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Pause")) {
                    GcEvent event = parseGcEvent(line);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }
        }
        
        return events;
    }
    
    private GcEvent parseGcEvent(String line) {
        // Example: [2024-07-30T00:12:34.567+0000][info][gc] 
        //          GC(12) Pause Young (Normal) 123M->45M(512M) 4.567ms
        
        Pattern pattern = Pattern.compile(
            "\\[([^]]+)\\].*GC\\((\\d+)\\)\\s+Pause\\s+(\\w+).*" +
            "(\\d+\\.\\d+)ms");
        
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return new GcEvent(
                Instant.parse(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3),
                Double.parseDouble(matcher.group(4))
            );
        }
        return null;
    }
    
    public GcStatistics analyze(List<GcEvent> events) {
        if (events.isEmpty()) {
            return GcStatistics.empty();
        }
        
        DoubleSummaryStatistics pauseStats = events.stream()
            .mapToDouble(GcEvent::pauseTimeMs)
            .summaryStatistics();
        
        long youngGcs = events.stream()
            .filter(e -> e.type().contains("Young"))
            .count();
        
        long mixedGcs = events.stream()
            .filter(e -> e.type().contains("Mixed"))
            .count();
        
        long fullGcs = events.stream()
            .filter(e -> e.type().contains("Full"))
            .count();
        
        return new GcStatistics(
            events.size(),
            pauseStats.getAverage(),
            pauseStats.getMax(),
            youngGcs,
            mixedGcs,
            fullGcs,
            calculateThroughput(events)
        );
    }
}

public record GcEvent(
    Instant timestamp,
    int gcId,
    String type,
    double pauseTimeMs
) {}

public record GcStatistics(
    long totalCollections,
    double avgPauseTimeMs,
    double maxPauseTimeMs,
    long youngGcs,
    long mixedGcs,
    long fullGcs,
    double throughputPercent
) {}
```

### 7.3 async-profiler Integration

**Profiler Integration:**
```java
public class AsyncProfilerIntegration {
    private AsyncProfiler profiler;
    private volatile boolean profiling = false;
    
    public AsyncProfilerIntegration() {
        this.profiler = AsyncProfiler.getInstance();
    }
    
    public void startProfiling(ProfileMode mode, Duration duration) {
        if (profiling) {
            throw new IllegalStateException("Profiling already in progress");
        }
        
        String command = buildCommand(mode, duration);
        profiler.execute(command);
        profiling = true;
        
        logger.info("Started {} profiling for {}", mode, duration);
    }
    
    private String buildCommand(ProfileMode mode, Duration duration) {
        return String.format(
            "start,event=%s,file=flamegraph-%s.html,interval=%d",
            mode.getEvent(),
            mode.name().toLowerCase(),
            duration.toMillis()
        );
    }
    
    public void stopProfiling() {
        if (!profiling) {
            return;
        }
        
        profiler.execute("stop");
        profiling = false;
        
        logger.info("Stopped profiling, flamegraph generated");
    }
    
    public enum ProfileMode {
        CPU("cpu"),           // CPU profiling (sampled)
        ALLOC("alloc"),       // Allocation profiling
        LOCK("lock"),         // Lock contention
        WALL("wall"),         // Wall-clock time
        CACHE_MISSES("cache-misses"); // Hardware counters
        
        private final String event;
        
        ProfileMode(String event) {
            this.event = event;
        }
        
        public String getEvent() {
            return event;
        }
    }
}
```

**Flame Graph Generation:**
```java
public class FlameGraphGenerator {
    private final AsyncProfilerIntegration profiler;
    
    public Path generateFlameGraph(Runnable workload, ProfileMode mode) {
        Path outputPath = Paths.get("flamegraph-" + 
            System.currentTimeMillis() + ".html");
        
        // Start profiling
        profiler.startProfiling(mode, Duration.ofMinutes(1));
        
        // Run workload
        workload.run();
        
        // Stop profiling (generates flamegraph.html)
        profiler.stopProfiling();
        
        return outputPath;
    }
    
    public ComparisonReport compareFlameGraphs(
            Path baseline, Path modified) {
        // Compare two flame graphs to see performance differences
        // Useful for before/after optimization comparisons
        return FlameGraphComparator.compare(baseline, modified);
    }
}
```

### 7.4 JFR (Java Flight Recorder) Custom Events

**Custom Event Definitions:**
```java
// Rule execution event
@Name("com.jvm.engine.RuleExecution")
@Label("Rule Execution")
@Category("JVM Engine")
@Description("Records rule execution with timing and context")
public class RuleExecutionEvent extends Event {
    @Label("Rule Name")
    String ruleName;
    
    @Label("Execution Time")
    @Timespan(Timespan.MILLISECONDS)
    long duration;
    
    @Label("Bytecode Size")
    int bytecodeSize;
    
    @Label("Cache Tier")
    String cacheTier;
    
    @Label("Success")
    boolean success;
}

// Rule compilation event
@Name("com.jvm.engine.RuleCompilation")
@Label("Rule Compilation")
@Category("JVM Engine")
public class RuleCompilationEvent extends Event {
    @Label("Rule Name")
    String ruleName;
    
    @Label("Bytecode Size")
    int bytecodeSize;
    
    @Label("Compilation Time")
    @Timespan(Timespan.MILLISECONDS)
    long duration;
    
    @Label("Success")
    boolean success;
    
    @Label("Generator")
    String generator; // "ByteBuddy" or "ASM"
}

// ClassLoader creation event
@Name("com.jvm.engine.ClassLoaderCreated")
@Label("ClassLoader Created")
@Category("JVM Engine")
public class ClassLoaderCreatedEvent extends Event {
    @Label("Loader ID")
    String loaderId;
    
    @Label("Parent Loader")
    String parentLoaderId;
    
    @Label("Rule Name")
    String ruleName;
    
    @Label("Isolation Mode")
    String isolationMode;
}

// Cache eviction event
@Name("com.jvm.engine.CacheEviction")
@Label("Cache Eviction")
@Category("JVM Engine")
public class CacheEvictionEvent extends Event {
    @Label("Rule Name")
    String ruleName;
    
    @Label("Tier")
    String tier; // "L1", "L2", "L3"
    
    @Label("Reason")
    String reason; // "GC", "Size", "TTL"
}
```

**Recording Events:**
```java
public class JfrEventRecorder {
    
    public void recordRuleExecution(CompiledRule rule, 
                                   ExecutionResult result,
                                   String cacheTier) {
        RuleExecutionEvent event = new RuleExecutionEvent();
        event.begin();
        
        event.ruleName = rule.getName();
        event.duration = result.getExecutionTimeMs();
        event.bytecodeSize = rule.getBytecodeSize();
        event.cacheTier = cacheTier;
        event.success = result.isSuccess();
        
        event.end();
        event.commit();
    }
    
    public void recordRuleCompilation(String ruleName,
                                     byte[] bytecode,
                                     long compilationTimeNs,
                                     String generator) {
        RuleCompilationEvent event = new RuleCompilationEvent();
        event.begin();
        
        event.ruleName = ruleName;
        event.bytecodeSize = bytecode.length;
        event.duration = TimeUnit.NANOSECONDS.toMillis(compilationTimeNs);
        event.success = true;
        event.generator = generator;
        
        event.end();
        event.commit();
    }
}
```

**Starting JFR Recording:**
```bash
# Command line
java -XX:StartFlightRecording=filename=recording.jfr,duration=60s \
     -jar engine-core.jar

# Programmatic
FlightRecorderConnection connection = FlightRecorderConnection.create();
RecordingConfiguration config = RecordingConfiguration.create()
    .name("JVM Engine Recording")
    .duration(Duration.ofMinutes(5))
    .destination(Paths.get("recording.jfr"));

Recording recording = connection.newRecording(config);
recording.start();
// ... run workload ...
recording.stop();
recording.close();
```

### 7.5 Interactive Profiler UI

**Terminal-based Dashboard:**
```java
public class LiveDashboard {
    private final JitCompilationMonitor jitMonitor;
    private final GcLogAnalyzer gcAnalyzer;
    private final TieredRuleCache cache;
    
    public void start() {
        Terminal terminal = TerminalBuilder.builder()
            .system(true)
            .build();
        
        // Refresh every second
        while (true) {
            clearScreen(terminal);
            renderDashboard(terminal);
            Thread.sleep(1000);
        }
    }
    
    private void renderDashboard(Terminal terminal) {
        terminal.writer().println("╔═══════════════════════════════════════════════════════╗");
        terminal.writer().println("║  JVM Engine Profiler - Live Dashboard                ║");
        terminal.writer().println("╠═══════════════════════════════════════════════════════╣");
        
        // JIT Compilation Status
        CompilationStats stats = jitMonitor.getStats();
        terminal.writer().println("║  JIT Compilation Status:                              ║");
        terminal.writer().printf("║    Tier 0 (Interp): %-5d methods                    ║%n", 
            stats.tier0Count());
        terminal.writer().printf("║    Tier 3 (C1):     %-5d methods                    ║%n", 
            stats.tier3Count());
        terminal.writer().printf("║    Tier 4 (C2):     %-5d methods                    ║%n", 
            stats.tier4Count());
        terminal.writer().println("║                                                       ║");
        
        // GC Activity
        GcStatistics gcStats = gcAnalyzer.getCurrentStats();
        terminal.writer().println("║  GC Activity (last 60s):                              ║");
        terminal.writer().printf("║    Collections: %-5d                                ║%n", 
            gcStats.totalCollections());
        terminal.writer().printf("║    Avg Pause: %.2fms                                 ║%n", 
            gcStats.avgPauseTimeMs());
        terminal.writer().printf("║    Max Pause: %.2fms                                 ║%n", 
            gcStats.maxPauseTimeMs());
        terminal.writer().println("║                                                       ║");
        
        // Cache Stats
        CacheStatsSnapshot cacheStats = cache.getStatistics().snapshot();
        terminal.writer().println("║  Cache Stats:                                         ║");
        terminal.writer().printf("║    L1 Hit Rate: %.1f%%                                ║%n", 
            cacheStats.l1HitRate() * 100);
        terminal.writer().printf("║    L2 Hit Rate: %.1f%%                                ║%n", 
            cacheStats.l2HitRate() * 100);
        terminal.writer().printf("║    L3 Hit Rate: %.1f%%                                ║%n", 
            cacheStats.l3HitRate() * 100);
        terminal.writer().printf("║    GC Evictions: %-5d                               ║%n", 
            cacheStats.gcEvictions());
        
        terminal.writer().println("╚═══════════════════════════════════════════════════════╝");
    }
}
```

---


## 8. Observability - Metrics, Logging & Health

### 8.1 Metrics Collection

**Metric Registry:**
```java
public class EngineMetrics {
    private final MeterRegistry registry = new SimpleMeterRegistry();
    
    // Counters
    private final Counter rulesCompiled;
    private final Counter rulesExecuted;
    private final Counter compilationFailures;
    private final Counter executionFailures;
    
    // Timers
    private final Timer compilationTime;
    private final Timer executionTime;
    
    // Gauges
    private final AtomicLong activeClassLoaders = new AtomicLong(0);
    private final AtomicLong cachedRulesL1 = new AtomicLong(0);
    private final AtomicLong metaspaceUsed = new AtomicLong(0);
    
    public EngineMetrics() {
        this.rulesCompiled = Counter.builder("rules.compiled")
            .tag("component", "compiler")
            .register(registry);
        
        this.rulesExecuted = Counter.builder("rules.executed")
            .tag("component", "executor")
            .register(registry);
        
        this.compilationTime = Timer.builder("rules.compilation.time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        // Register gauges
        Gauge.builder("classloaders.active", activeClassLoaders, AtomicLong::get)
            .register(registry);
        
        Gauge.builder("metaspace.used.bytes", metaspaceUsed, AtomicLong::get)
            .register(registry);
    }
}
```

### 8.2 Logging Strategy

**Structured Logging:**
```java
public class StructuredLogger {
    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);
    
    public void logRuleCompilation(String ruleName, int bytecodeSize, long durationMs) {
        logger.info("Rule compiled",
            kv("ruleName", ruleName),
            kv("bytecodeSize", bytecodeSize),
            kv("compilationTimeMs", durationMs),
            kv("event", "rule_compiled")
        );
    }
    
    public void logRuleExecution(String ruleName, long durationMs, boolean cacheHit) {
        logger.debug("Rule executed",
            kv("ruleName", ruleName),
            kv("executionTimeMs", durationMs),
            kv("cacheHit", cacheHit),
            kv("event", "rule_executed")
        );
    }
    
    public void logClassLoaderCreated(String loaderId, String ruleName) {
        logger.info("ClassLoader created",
            kv("loaderId", loaderId),
            kv("ruleName", ruleName),
            kv("event", "classloader_created")
        );
    }
}
```

### 8.3 Health Checks

```java
public class EngineHealthCheck {
    private final ClassLoaderManager classLoaderManager;
    private final TieredRuleCache cache;
    private final MetaspaceMonitor metaspaceMonitor;
    
    public HealthStatus check() {
        List<HealthIssue> issues = new ArrayList<>();
        
        // Check Metaspace usage
        MetaspaceStats metaspace = metaspaceMonitor.getStats();
        if (metaspace.utilizationPercent() > 80) {
            issues.add(new HealthIssue(
                Severity.WARNING,
                "Metaspace usage above 80%",
                Map.of("usage", metaspace.used(), 
                      "max", metaspace.max())
            ));
        }
        
        // Check ClassLoader leaks
        int activeLoaders = classLoaderManager.getActiveLoaderCount();
        if (activeLoaders > EXPECTED_MAX_LOADERS) {
            issues.add(new HealthIssue(
                Severity.ERROR,
                "Potential ClassLoader leak detected",
                Map.of("activeLoaders", activeLoaders,
                      "expected", EXPECTED_MAX_LOADERS)
            ));
        }
        
        // Check cache effectiveness
        double hitRate = cache.getStatistics().getHitRate();
        if (hitRate < 0.5) {
            issues.add(new HealthIssue(
                Severity.WARNING,
                "Low cache hit rate",
                Map.of("hitRate", hitRate)
            ));
        }
        
        return new HealthStatus(
            issues.isEmpty() ? Status.HEALTHY : Status.DEGRADED,
            issues
        );
    }
}
```

---

## 9. Testing & Benchmarking

### 9.1 Unit Tests

**Parser Tests:**
```java
@Test
void shouldParseSimpleRule() {
    String json = """
        {
          "ruleName": "test",
          "input": {"x": "int"},
          "expressions": [{"expression": "return x > 10"}]
        }
        """;
    
    Rule rule = parser.parse(json);
    
    assertNotNull(rule);
    assertEquals("test", rule.getName());
    assertEquals(1, rule.getExpressions().size());
}

@Test
void shouldRejectInvalidSyntax() {
    String json = """
        {
          "ruleName": "test",
          "input": {"x": "int"},
          "expressions": [{"expression": "return x >"}]
        }
        """;
    
    assertThrows(RuleSyntaxException.class, () -> parser.parse(json));
}
```

**ClassLoader Tests:**
```java
@Test
void shouldIsolateRuleClasses() {
    RuleClassLoader loader1 = new RuleClassLoader("rule1", sharedLoader);
    RuleClassLoader loader2 = new RuleClassLoader("rule2", sharedLoader);
    
    Class<?> class1 = loader1.defineRule("TestRule", bytecode);
    Class<?> class2 = loader2.defineRule("TestRule", bytecode);
    
    assertNotSame(class1, class2, "Classes should be isolated");
    assertEquals("TestRule", class1.getName());
    assertEquals("TestRule", class2.getName());
}

@Test
void shouldUnloadClassesWhenLoaderClosed() throws Exception {
    WeakReference<ClassLoader> loaderRef;
    WeakReference<Class<?>> classRef;
    
    {
        RuleClassLoader loader = new RuleClassLoader("test", sharedLoader);
        Class<?> ruleClass = loader.defineRule("TestRule", bytecode);
        
        loaderRef = new WeakReference<>(loader);
        classRef = new WeakReference<>(ruleClass);
        
        loader.close();
    }
    
    System.gc();
    System.gc();
    
    assertNull(loaderRef.get(), "ClassLoader should be collected");
    assertNull(classRef.get(), "Class should be unloaded");
}
```

### 9.2 Integration Tests

**End-to-End Test:**
```java
@Test
void shouldExecuteRuleEndToEnd() {
    // Given: JSON rule
    String json = """
        {
          "ruleName": "fraud",
          "input": {"amount": "double", "country": "String"},
          "expressions": [
            {"expression": "return amount > 1000 && country.equals(\\"US\\")"}
          ]
        }
        """;
    
    // When: Compile and execute
    RuleEngine engine = new RuleEngine();
    ExecutionContext ctx = new ExecutionContext()
        .set("amount", 2000.0)
        .set("country", "US");
    ExecutionResult result = engine.executeRule(json, ctx);
    
    // Then: Verify result
    assertTrue(result.isSuccess());
    assertTrue((Boolean) result.getValue());
    assertTrue(result.getExecutionTimeMs() < 100);
}
```

### 9.3 JMH Benchmarks

**Compilation Benchmark:**
```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class CompilationBenchmark {
    
    private RuleCompiler compiler;
    private String simpleRule;
    private String complexRule;
    
    @Setup
    public void setup() {
        compiler = new RuleCompiler();
        simpleRule = loadRule("simple.json");
        complexRule = loadRule("complex.json");
    }
    
    @Benchmark
    public CompiledRule compileSimpleRule() {
        return compiler.compile(simpleRule);
    }
    
    @Benchmark
    public CompiledRule compileComplexRule() {
        return compiler.compile(complexRule);
    }
    
    @Benchmark
    @Param({"ByteBuddy", "ASM"})
    public CompiledRule compileBytecodeGenerator(String generator) {
        compiler.setGenerator(generator);
        return compiler.compile(simpleRule);
    }
}
```

**Execution Benchmark:**
```java
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ExecutionBenchmark {
    
    private RuleEngine engine;
    private CompiledRule hotRule;
    private ExecutionContext context;
    
    @Setup(Level.Trial)
    public void setup() {
        engine = new RuleEngine();
        hotRule = engine.compileRule(loadRule("hot.json"));
        context = new ExecutionContext().set("value", 100);
        
        // Warm up JIT
        for (int i = 0; i < 20000; i++) {
            hotRule.execute(context);
        }
    }
    
    @Benchmark
    public ExecutionResult executeHotRule() {
        return hotRule.execute(context);
    }
    
    @Benchmark
    @Threads(8)
    public ExecutionResult executeConcurrent() {
        return hotRule.execute(context);
    }
}
```

---

## 10. Experiment Scenarios

### 10.1 Metaspace Leak & Recovery

```java
public class MetaspaceLeakExperiment {
    
    @Test
    @Tag("experiment")
    void demonstrateLeak() {
        List<RuleClassLoader> leakedLoaders = new ArrayList<>();
        
        assertThrows(OutOfMemoryError.class, () -> {
            for (int i = 0; i < 5000; i++) {
                RuleClassLoader loader = new RuleClassLoader("rule_" + i, sharedLoader);
                Class<?> ruleClass = loader.defineRule("GeneratedRule_" + i, bytecode);
                
                // MISTAKE: Keep loader reference
                leakedLoaders.add(loader);
                
                if (i % 100 == 0) {
                    logger.info("Metaspace usage: {}", getMetaspaceUsage());
                }
            }
        });
    }
    
    @Test
    @Tag("experiment")
    void demonstrateCleanup() {
        for (int i = 0; i < 5000; i++) {
            try (RuleClassLoader loader = new RuleClassLoader("rule_" + i, sharedLoader)) {
                Class<?> ruleClass = loader.defineRule("GeneratedRule_" + i, bytecode);
                // Execute rule
            } // Loader closed, eligible for unloading
            
            if (i % 500 == 0) {
                System.gc();
                Thread.sleep(100);
                logger.info("Metaspace usage: {}", getMetaspaceUsage());
            }
        }
        // Observation: Stable Metaspace usage
    }
}
```

**JVM Flags:**
```bash
-XX:MetaspaceSize=64m 
-XX:MaxMetaspaceSize=128m 
-XX:+PrintGCDetails 
-XX:+TraceClassUnloading
```

### 10.2 JIT Compilation Tiers

```java
public class JitCompilationExperiment {
    
    @Test
    @Tag("experiment")
    void observeCompilationTiers() {
        CompiledRule rule = compileSimpleRule();
        
        // Phase 1: Interpreted (0-100 invocations)
        for (int i = 0; i < 100; i++) {
            rule.execute(context);
        }
        logger.info("=== After 100: Likely still interpreted ===");
        
        // Phase 2: C1 compilation (100-2000 invocations)
        for (int i = 0; i < 1900; i++) {
            rule.execute(context);
        }
        logger.info("=== After 2000: Likely C1 compiled (Tier 3) ===");
        
        // Phase 3: C2 compilation (2000-15000 invocations)
        for (int i = 0; i < 13000; i++) {
            rule.execute(context);
        }
        logger.info("=== After 15000: Likely C2 compiled (Tier 4) ===");
    }
    
    @Test
    @Tag("experiment")
    void testInliningLimits() {
        // Generate rule with bytecode size > 35 bytes
        CompiledRule largeRule = compileRuleWithSize(500);
        
        for (int i = 0; i < 20000; i++) {
            largeRule.execute(context);
        }
        // Check -XX:+PrintInlining: "too big to inline"
    }
}
```

**JVM Flags:**
```bash
-XX:+PrintCompilation 
-XX:+UnlockDiagnosticVMOptions 
-XX:+PrintInlining 
-XX:+LogCompilation 
-XX:LogFile=hotspot.log
```

### 10.3 GC Stress & Reference Types

```java
public class GcStressExperiment {
    
    @Test
    @Tag("experiment")
    void softReferenceUnderPressure() {
        TieredRuleCache cache = new TieredRuleCache(100, 1000);
        
        // Fill L2 cache (SoftReferences)
        for (int i = 0; i < 1000; i++) {
            cache.putInL2("rule_" + i, compileRule());
        }
        
        logger.info("L2 size before pressure: {}", cache.sizeL2());
        
        // Apply memory pressure
        List<byte[]> pressure = new ArrayList<>();
        try {
            while (true) {
                pressure.add(new byte[10 * 1024 * 1024]); // 10MB
            }
        } catch (OutOfMemoryError e) {
            // Expected
        }
        
        pressure.clear();
        System.gc();
        
        logger.info("L2 size after pressure: {}", cache.sizeL2());
        // Observation: SoftReferences cleared to avoid OOM
    }
}
```

### 10.4 Object Layout Analysis

```java
public class ObjectLayoutExperiment {
    
    @Test
    @Tag("experiment")
    void compareCompressedOops() {
        CompiledRule rule = compileRule();
        Object ruleInstance = instantiateRule(rule);
        
        // Analyze with JOL
        System.out.println(ClassLayout.parseInstance(ruleInstance).toPrintable());
        
        long shallowSize = ClassLayout.parseInstance(ruleInstance).instanceSize();
        long deepSize = GraphLayout.parseInstance(ruleInstance).totalSize();
        
        logger.info("Shallow size: {} bytes", shallowSize);
        logger.info("Deep size: {} bytes", deepSize);
        logger.info("Compressed oops enabled: {}", isCompressedOops());
    }
}
```

**Run Commands:**
```bash
# With compressed oops
java -XX:+UseCompressedOops ObjectLayoutExperiment

# Without compressed oops
java -XX:-UseCompressedOops ObjectLayoutExperiment
```

---

## 11. Build Configuration

### 11.1 Parent POM

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.jvm.engine</groupId>
    <artifactId>jvm-scripting-engine</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <modules>
        <module>engine-api</module>
        <module>engine-core</module>
        <module>engine-profiler</module>
        <module>engine-agent</module>
        <module>engine-experiments</module>
    </modules>
    
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <bytebuddy.version>1.14.9</bytebuddy.version>
        <asm.version>9.5</asm.version>
        <junit.version>5.10.0</junit.version>
        <jmh.version>1.37</jmh.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>net.bytebuddy</groupId>
                <artifactId>byte-buddy</artifactId>
                <version>${bytebuddy.version}</version>
            </dependency>
            <dependency>
                <groupId>org.ow2.asm</groupId>
                <artifactId>asm</artifactId>
                <version>${asm.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 11.2 Agent Module POM (Fat JAR)

```xml
<project>
    <parent>
        <groupId>com.jvm.engine</groupId>
        <artifactId>jvm-scripting-engine</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    
    <artifactId>engine-agent</artifactId>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <manifestEntries>
                                        <Premain-Class>com.jvm.engine.agent.JvmEngineAgent</Premain-Class>
                                        <Agent-Class>com.jvm.engine.agent.JvmEngineAgent</Agent-Class>
                                        <Can-Redefine-Classes>true</Can-Redefine-Classes>
                                        <Can-Retransform-Classes>true</Can-Retransform-Classes>
                                    </manifestEntries>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 12. Deployment & Usage

### 12.1 Running the Engine

**Basic Execution:**
```bash
java -jar engine-core.jar
```

**With Agent:**
```bash
java -javaagent:engine-agent.jar \
     -jar engine-core.jar
```

**With Full Profiling:**
```bash
java -javaagent:engine-agent.jar \
     -XX:+PrintCompilation \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+PrintInlining \
     -Xlog:gc*:file=gc.log \
     -XX:StartFlightRecording=filename=recording.jfr \
     -jar engine-core.jar
```

### 12.2 Running Experiments

```bash
# Metaspace leak experiment
java -XX:MetaspaceSize=64m \
     -XX:MaxMetaspaceSize=128m \
     -XX:+TraceClassUnloading \
     -cp engine-experiments.jar \
     com.jvm.engine.experiments.MetaspaceLeakExperiment

# JIT compilation experiment
java -XX:+PrintCompilation \
     -XX:+PrintInlining \
     -cp engine-experiments.jar \
     com.jvm.engine.experiments.JitCompilationExperiment
```

### 12.3 Running Benchmarks

```bash
mvn clean install -pl engine-experiments
java -jar engine-experiments/target/benchmarks.jar
```

---

## 13. Future Enhancements

1. **Additional Bytecode Generators:** Support for Javassist, CGLIB
2. **Distributed Caching:** Redis/Hazelcast integration for cache tiers
3. **Rule Versioning:** Support for A/B testing and gradual rollout
4. **Native Image Support:** GraalVM native compilation compatibility
5. **Web Dashboard:** Browser-based profiling dashboard with real-time metrics
6. **Rule DSL Extensions:** Support for more complex expressions and control flow
7. **Performance Regression Testing:** Automated detection of performance regressions

---

## 14. References

- "Optimizing Java" by Benjamin J. Evans, James Gough, Chris Newland
- JVM Specification (Java SE 17)
- ByteBuddy Documentation
- ASM Documentation
- async-profiler Documentation
- JDK Flight Recorder API Documentation

---

