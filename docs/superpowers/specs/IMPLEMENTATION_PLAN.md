# Implementation Plan: Helix - JVM Scripting Engine & Profiler

**Project Name:** Helix (from "double helix" - representing the intertwined nature of bytecode and JVM internals)

**Timeline:** 7 Days (7 Sprints, 1 day each)  
**Start Date:** 2026-08-01  
**End Date:** 2026-08-07  
**Team Size:** 1 Developer  

---

## Project Overview

Helix is a production-grade JVM internals exploration platform that combines a dynamic rules engine with deep profiling capabilities. Built with a layered architecture across 5 Maven modules, it enables hands-on learning of class loading, bytecode generation, JIT compilation, and garbage collection.

---

## Sprint Structure

Each sprint represents 1 day of focused development:
- **Duration:** 8-10 hours of development time
- **Deliverable:** Working, tested module or feature set
- **Review:** End-of-day verification and commit

---

## Milestones & Sprints

### Sprint 1 (Day 1): Foundation & API Layer
**Milestone:** M1 - Project Bootstrap & API Contracts  
**Goal:** Set up project structure, Maven configuration, and define all public interfaces

### Sprint 2 (Day 2): Core Engine - Parsing & Bytecode Generation
**Milestone:** M2 - Rule Compilation Pipeline  
**Goal:** Implement JSON parsing, AST building, and ByteBuddy bytecode generation

### Sprint 3 (Day 3): Core Engine - ClassLoaders & Execution
**Milestone:** M3 - Rule Execution & Class Loading  
**Goal:** Implement hierarchical ClassLoaders, execution engine (sync/async/batch), and tiered cache

### Sprint 4 (Day 4): Java Agent & Instrumentation
**Milestone:** M4 - Agent Instrumentation & Memory Analysis  
**Goal:** Build Java Agent with ASM transformation, JOL memory analysis, and JMX MBeans

### Sprint 5 (Day 5): Profiler Module
**Milestone:** M5 - JIT/GC Profiling & Observability  
**Goal:** Implement JIT monitor, GC analyzer, async-profiler integration, JFR events, and live dashboard

### Sprint 6 (Day 6): Experiments & Benchmarks
**Milestone:** M6 - JVM Experiments & Performance Benchmarks  
**Goal:** Create experiment scenarios (Metaspace, JIT, GC, object layout) and JMH benchmarks

### Sprint 7 (Day 7): Integration, Documentation & Polish
**Milestone:** M7 - Production Readiness  
**Goal:** End-to-end integration tests, documentation, README, CI/CD setup, and final polish

---


## Sprint 1: Foundation & API Layer (Day 1)

**Milestone:** M1 - Project Bootstrap & API Contracts  
**Duration:** 8-10 hours  
**Dependencies:** None  

### Tasks

#### Task 1.1: Project Structure Setup
**Priority:** Critical  
**Estimated Time:** 1.5 hours  
**Labels:** `setup`, `infrastructure`

**Description:**
Create the multi-module Maven project structure with all 5 modules.

**Acceptance Criteria:**
- [ ] Root `pom.xml` created with parent configuration
- [ ] 5 module directories created: `engine-api`, `engine-core`, `engine-profiler`, `engine-agent`, `engine-experiments`
- [ ] Each module has its own `pom.xml`
- [ ] Module dependencies properly configured
- [ ] Java 17+ source/target configured
- [ ] Project builds successfully with `mvn clean install`

**Technical Notes:**
- Use Maven 3.9+
- Set encoding to UTF-8
- Configure Maven Compiler Plugin 3.11+

---

#### Task 1.2: Define Core API Interfaces
**Priority:** Critical  
**Estimated Time:** 2 hours  
**Labels:** `api`, `interfaces`, `design`

**Description:**
Define all public interfaces in the `engine-api` module.

**Acceptance Criteria:**
- [ ] `Rule` interface created with name, version, input schema
- [ ] `CompiledRule` interface created with execute() method
- [ ] `RuleEngine` interface created with compile/execute methods
- [ ] `ExecutionContext` class created with variable storage
- [ ] `ExecutionResult` class created with success/failure states
- [ ] All interfaces have complete Javadoc documentation
- [ ] No implementation dependencies in `engine-api`

**Files to Create:**
```
engine-api/src/main/java/com/helix/api/
├── Rule.java
├── CompiledRule.java
├── RuleEngine.java
├── ExecutionContext.java
├── ExecutionResult.java
├── RuleCompilationException.java
└── RuleExecutionException.java
```

---

#### Task 1.3: Define Profiler API Interfaces
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `api`, `profiler`

**Description:**
Define profiler-related interfaces in `engine-api`.

**Acceptance Criteria:**
- [ ] `Profiler` interface created with start/stop methods
- [ ] `ProfileEvent` base class created
- [ ] `CompilationEvent` record created
- [ ] `ExecutionEvent` record created
- [ ] `CacheEvent` record created
- [ ] Event listener interfaces defined

**Files to Create:**
```
engine-api/src/main/java/com/helix/api/profiler/
├── Profiler.java
├── ProfileEvent.java
├── ProfileEventListener.java
├── CompilationEvent.java
├── ExecutionEvent.java
└── CacheEvent.java
```

---

#### Task 1.4: Define Agent API Interfaces
**Priority:** Medium  
**Estimated Time:** 1 hour  
**Labels:** `api`, `agent`

**Description:**
Define agent-related interfaces for instrumentation and memory analysis.

**Acceptance Criteria:**
- [ ] `AgentConfiguration` class created
- [ ] `MemoryAnalysisReport` record created
- [ ] `ClassLoaderInfo` record created
- [ ] Agent callback interfaces defined

**Files to Create:**
```
engine-api/src/main/java/com/helix/api/agent/
├── AgentConfiguration.java
├── MemoryAnalysisReport.java
├── ClassLoaderInfo.java
└── InstrumentationCallback.java
```

---

#### Task 1.5: Add Core Dependencies
**Priority:** Critical  
**Estimated Time:** 1 hour  
**Labels:** `dependencies`, `setup`

**Description:**
Add all required dependencies to Maven POMs with version management in parent POM.

**Acceptance Criteria:**
- [ ] Parent POM has `<dependencyManagement>` section
- [ ] ByteBuddy 1.14.9+ added
- [ ] ASM 9.5+ added
- [ ] Caffeine 3.1+ for caching
- [ ] SLF4J + Logback for logging
- [ ] JUnit 5.10+ for testing
- [ ] JMH 1.37+ for benchmarks
- [ ] JOL 0.17+ for object layout
- [ ] All versions externalized to properties

---

#### Task 1.6: Configure Build Plugins
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `build`, `maven`

**Description:**
Configure essential Maven plugins in parent POM.

**Acceptance Criteria:**
- [ ] Maven Compiler Plugin configured for Java 17
- [ ] Maven Surefire Plugin for unit tests
- [ ] Maven Failsafe Plugin for integration tests
- [ ] Maven Shade Plugin configured for agent module
- [ ] Maven JAR Plugin for manifest configuration
- [ ] SpotBugs plugin for static analysis
- [ ] All plugins use latest stable versions

---

#### Task 1.7: Set Up Logging Configuration
**Priority:** Medium  
**Estimated Time:** 0.5 hours  
**Labels:** `logging`, `configuration`

**Description:**
Create Logback configuration for structured logging.

**Acceptance Criteria:**
- [ ] `logback.xml` created in `engine-core/src/main/resources`
- [ ] Log patterns include timestamp, level, thread, class, message
- [ ] Separate loggers for different packages
- [ ] File appender for persistent logs
- [ ] Console appender for development

---

#### Task 1.8: Create Basic Unit Tests
**Priority:** Medium  
**Estimated Time:** 1 hour  
**Labels:** `testing`, `unit-tests`

**Description:**
Create basic test structure and smoke tests for API module.

**Acceptance Criteria:**
- [ ] Test directory structure created for all modules
- [ ] Basic test for `ExecutionContext` creation
- [ ] Basic test for `ExecutionResult` success/failure
- [ ] Test utilities class created
- [ ] All tests pass with `mvn test`

---

#### Task 1.9: Initialize Git Repository
**Priority:** High  
**Estimated Time:** 0.5 hours  
**Labels:** `git`, `setup`

**Description:**
Initialize Git repository with proper `.gitignore`.

**Acceptance Criteria:**
- [ ] Git repository initialized
- [ ] `.gitignore` created for Maven/Java projects
- [ ] Initial commit with project structure
- [ ] README.md created with project overview
- [ ] Branch strategy documented (main + feature branches)

**`.gitignore` should include:**
```
target/
*.class
*.jar
*.log
.idea/
.vscode/
*.iml
.DS_Store
```

---

#### Task 1.10: Sprint 1 Verification
**Priority:** Critical  
**Estimated Time:** 0.5 hours  
**Labels:** `verification`, `milestone`

**Description:**
Verify all Sprint 1 deliverables are complete.

**Acceptance Criteria:**
- [ ] `mvn clean install` succeeds on all modules
- [ ] All interfaces compile without errors
- [ ] All unit tests pass
- [ ] Code coverage > 50% for API module
- [ ] No compiler warnings
- [ ] Git repository has clean commit history
- [ ] README documents project setup

---

### Sprint 1 Summary

**Total Tasks:** 10  
**Total Estimated Time:** 10.5 hours  
**Critical Path:** Task 1.1 → Task 1.2 → Task 1.5 → Task 1.10  

**Deliverables:**
- ✅ Complete project structure with 5 Maven modules
- ✅ All public API interfaces defined and documented
- ✅ Build system fully configured
- ✅ Git repository initialized
- ✅ Basic test infrastructure in place

**End-of-Sprint Checklist:**
- [ ] All 10 tasks completed
- [ ] Build passes: `mvn clean install`
- [ ] Tests pass: `mvn test`
- [ ] Code committed to Git
- [ ] Sprint retrospective: What went well? What to improve?

---


## Sprint 2: Core Engine - Parsing & Bytecode Generation (Day 2)

**Milestone:** M2 - Rule Compilation Pipeline  
**Duration:** 8-10 hours  
**Dependencies:** Sprint 1 completed  

### Tasks

#### Task 2.1: JSON Rule Parser Implementation
**Priority:** Critical  
**Estimated Time:** 2 hours  
**Labels:** `core`, `parser`, `json`

**Description:**
Implement JSON rule parser that converts JSON into intermediate Rule objects.

**Acceptance Criteria:**
- [ ] `RuleParser` class created with parse() method
- [ ] JSON schema validation implemented
- [ ] Input type parsing (int, double, String, long, boolean)
- [ ] Expression parsing into strings
- [ ] Error handling for malformed JSON
- [ ] Unit tests with valid and invalid JSON samples
- [ ] Support for rule metadata (version, description, category)

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/parser/
├── RuleParser.java
├── JsonRuleLoader.java
├── RuleSchema.java
└── ParseException.java
```

**Test Cases:**
- Valid simple rule with single expression
- Complex rule with multiple expressions
- Rule with all supported input types
- Invalid JSON syntax
- Missing required fields

---

#### Task 2.2: AST Builder Implementation
**Priority:** Critical  
**Estimated Time:** 2.5 hours  
**Labels:** `core`, `ast`, `compiler`

**Description:**
Build Abstract Syntax Tree from expression strings using recursive descent parser.

**Acceptance Criteria:**
- [ ] `ExpressionNode` interface and implementations created
- [ ] `BinaryOpNode`, `LiteralNode`, `VariableNode`, `MethodCallNode` implemented
- [ ] Support for operators: `+`, `-`, `*`, `/`, `>`, `<`, `==`, `!=`, `&&`, `||`
- [ ] Operator precedence handled correctly
- [ ] Parentheses support for grouping
- [ ] AST visitor pattern implemented for traversal
- [ ] Unit tests for each node type
- [ ] Integration tests for complex expressions

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/parser/ast/
├── ExpressionNode.java
├── BinaryOpNode.java
├── UnaryOpNode.java
├── LiteralNode.java
├── VariableNode.java
├── MethodCallNode.java
├── AstBuilder.java
└── AstVisitor.java
```

---

#### Task 2.3: Type Checker Implementation
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `core`, `type-checking`

**Description:**
Implement type checking and validation for AST nodes.

**Acceptance Criteria:**
- [ ] `TypeChecker` class created
- [ ] Type inference for literals
- [ ] Type validation for binary operations
- [ ] Method signature resolution
- [ ] Type compatibility checks
- [ ] Clear error messages for type mismatches
- [ ] Unit tests for type checking scenarios

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/parser/
├── TypeChecker.java
├── TypeContext.java
└── TypeMismatchException.java
```

---

#### Task 2.4: ByteBuddy Bytecode Generator
**Priority:** Critical  
**Estimated Time:** 2.5 hours  
**Labels:** `core`, `bytecode`, `bytebuddy`

**Description:**
Implement ByteBuddy-based bytecode generator that converts AST to executable classes.

**Acceptance Criteria:**
- [ ] `ByteBuddyGenerator` class created
- [ ] Generates classes implementing `CompiledRule` interface
- [ ] AST-to-bytecode conversion for all node types
- [ ] Method delegation for rule execution
- [ ] Generated classes are properly named and versioned
- [ ] Unit tests verify generated bytecode executes correctly
- [ ] Integration tests with complex rules

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/bytecode/
├── ByteBuddyGenerator.java
├── BytecodeGenerator.java (interface)
├── ExecutionInterceptor.java
└── BytecodeGenerationException.java
```

**Test Cases:**
- Simple arithmetic expression: `return x + y`
- Comparison: `return amount > 1000`
- Logical operators: `return x > 10 && y < 20`
- Method calls: `return name.equals("test")`

---

#### Task 2.5: ASM Bytecode Generator (Experimental)
**Priority:** Medium  
**Estimated Time:** 1.5 hours  
**Labels:** `core`, `bytecode`, `asm`, `experimental`

**Description:**
Implement ASM-based bytecode generator for low-level experiments.

**Acceptance Criteria:**
- [ ] `AsmGenerator` class created
- [ ] Direct bytecode emission using ASM ClassWriter
- [ ] Support for simple expressions only (POC)
- [ ] Manual stack manipulation
- [ ] Unit tests for basic scenarios
- [ ] Documentation on when to use ASM vs ByteBuddy

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/bytecode/
├── AsmGenerator.java
├── AsmMethodVisitor.java
└── AsmClassBuilder.java
```

---

#### Task 2.6: Bytecode Optimizer
**Priority:** Low  
**Estimated Time:** 1 hour  
**Labels:** `core`, `optimization`

**Description:**
Implement basic bytecode optimizations.

**Acceptance Criteria:**
- [ ] Constant folding implemented (e.g., `2 + 3` → `5`)
- [ ] Dead code elimination for unreachable branches
- [ ] Inline constant values where possible
- [ ] Optimization is optional (can be disabled)
- [ ] Unit tests verify optimizations work correctly
- [ ] Performance benchmarks show improvement

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/bytecode/
├── BytecodeOptimizer.java
├── ConstantFolder.java
└── DeadCodeEliminator.java
```

---

#### Task 2.7: Rule Compiler Integration
**Priority:** Critical  
**Estimated Time:** 1 hour  
**Labels:** `core`, `integration`

**Description:**
Create `RuleCompiler` that orchestrates the entire compilation pipeline.

**Acceptance Criteria:**
- [ ] `RuleCompiler` class coordinates Parser → AST → TypeChecker → Generator
- [ ] Compilation timing tracked with metrics
- [ ] Detailed error reporting at each stage
- [ ] Support for both ByteBuddy and ASM generators
- [ ] Integration tests for end-to-end compilation
- [ ] Performance benchmarks for compilation speed

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/
├── RuleCompiler.java
└── CompilationMetrics.java
```

---

#### Task 2.8: Unit Tests for Compilation Pipeline
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `testing`, `unit-tests`

**Description:**
Comprehensive unit tests for all compilation components.

**Acceptance Criteria:**
- [ ] Parser tests: 10+ test cases
- [ ] AST builder tests: 15+ test cases
- [ ] Type checker tests: 10+ test cases
- [ ] ByteBuddy generator tests: 10+ test cases
- [ ] End-to-end compilation tests: 5+ test cases
- [ ] Code coverage > 80% for compilation pipeline
- [ ] All edge cases covered (empty rules, invalid syntax, etc.)

---

#### Task 2.9: Integration Tests
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `testing`, `integration-tests`

**Description:**
Create integration tests that verify the entire compilation pipeline.

**Acceptance Criteria:**
- [ ] Test simple rule: `{"expression": "return x > 10"}`
- [ ] Test complex rule with multiple expressions
- [ ] Test rule with all supported operators
- [ ] Test rule with method calls
- [ ] Verify generated bytecode executes correctly
- [ ] Integration tests run in separate Maven phase

**Files to Create:**
```
engine-core/src/test/java/com/helix/core/integration/
└── CompilationPipelineIntegrationTest.java
```

---

#### Task 2.10: Sprint 2 Verification
**Priority:** Critical  
**Estimated Time:** 0.5 hours  
**Labels:** `verification`, `milestone`

**Description:**
Verify all Sprint 2 deliverables are complete and working.

**Acceptance Criteria:**
- [ ] All 9 tasks completed
- [ ] Can compile a simple JSON rule to bytecode
- [ ] Can compile a complex rule with all operators
- [ ] All unit tests pass (80%+ coverage)
- [ ] All integration tests pass
- [ ] Performance benchmarks baseline established
- [ ] Documentation updated with examples
- [ ] Code committed and pushed to Git

---

### Sprint 2 Summary

**Total Tasks:** 10  
**Total Estimated Time:** 14.5 hours (Adjust: 10 hours by parallelizing tests)  
**Critical Path:** Task 2.1 → Task 2.2 → Task 2.3 → Task 2.4 → Task 2.7  

**Deliverables:**
- ✅ Complete rule compilation pipeline (JSON → AST → Bytecode)
- ✅ ByteBuddy primary generator
- ✅ ASM experimental generator
- ✅ Type checking and validation
- ✅ Basic bytecode optimizations
- ✅ 80%+ test coverage

**End-of-Sprint Checklist:**
- [ ] Can compile and execute: `{"expression": "return amount > 1000 && country.equals(\"US\")"}`
- [ ] All tests pass: `mvn test`
- [ ] No compiler warnings
- [ ] Code reviewed and committed

---


## Sprint 3: Core Engine - ClassLoaders & Execution (Day 3)

**Milestone:** M3 - Rule Execution & Class Loading  
**Duration:** 8-10 hours  
**Dependencies:** Sprint 2 completed  

### Tasks

#### Task 3.1: RuleClassLoader Implementation
**Priority:** Critical  
**Estimated Time:** 2 hours  
**Labels:** `core`, `classloader`, `jvm-internals`

**Description:**
Implement custom ClassLoader for loading dynamically generated rule classes.

**Acceptance Criteria:**
- [ ] `RuleClassLoader` extends `URLClassLoader`
- [ ] Implements `AutoCloseable` for proper cleanup
- [ ] `defineRule()` method loads bytecode into JVM
- [ ] Tracks all loaded classes for monitoring
- [ ] Proper parent delegation to shared loader
- [ ] Unit tests for class loading
- [ ] Unit tests for class unloading (with WeakReference)
- [ ] Metrics integration for ClassLoader lifecycle

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/classloader/
├── RuleClassLoader.java
├── ClassLoaderMetrics.java
└── ClassLoadingException.java
```

---

#### Task 3.2: SharedUtilityClassLoader Implementation
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `core`, `classloader`

**Description:**
Implement shared ClassLoader for common utilities to reduce memory footprint.

**Acceptance Criteria:**
- [ ] `SharedUtilityClassLoader` singleton implementation
- [ ] Loads common dependencies once
- [ ] Serves as parent for all RuleClassLoaders
- [ ] Unit tests verify singleton behavior
- [ ] Documentation on what gets shared

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/classloader/
└── SharedUtilityClassLoader.java
```

---

#### Task 3.3: ClassLoaderManager Implementation
**Priority:** Critical  
**Estimated Time:** 1.5 hours  
**Labels:** `core`, `classloader`, `management`

**Description:**
Implement ClassLoader manager supporting isolated, shared, and hierarchical modes.

**Acceptance Criteria:**
- [ ] `ClassLoaderManager` manages all RuleClassLoader instances
- [ ] Supports 3 isolation modes: `ISOLATED`, `SHARED`, `HIERARCHICAL`
- [ ] Creates ClassLoaders based on isolation strategy
- [ ] Tracks active loaders in ConcurrentHashMap
- [ ] `closeLoader()` properly cleans up and enables GC
- [ ] Leak detection for long-lived loaders
- [ ] Unit tests for all isolation modes
- [ ] Integration tests with actual rule loading

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/classloader/
├── ClassLoaderManager.java
├── IsolationMode.java
└── ClassLoaderLeakDetector.java
```

---

#### Task 3.4: Sync Executor Implementation
**Priority:** Critical  
**Estimated Time:** 1 hour  
**Labels:** `core`, `execution`, `sync`

**Description:**
Implement synchronous rule executor.

**Acceptance Criteria:**
- [ ] `SyncExecutor` implements `RuleExecutor` interface
- [ ] Executes rules in calling thread
- [ ] Measures execution time with nanosecond precision
- [ ] Proper error handling and wrapping
- [ ] Metrics collection (counters, timers)
- [ ] Unit tests with mock rules
- [ ] Integration tests with real compiled rules

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/executor/
├── RuleExecutor.java (interface)
├── SyncExecutor.java
└── ExecutorMetrics.java
```

---

#### Task 3.5: Async Executor Implementation
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `core`, `execution`, `async`

**Description:**
Implement asynchronous rule executor using CompletableFuture.

**Acceptance Criteria:**
- [ ] `AsyncExecutor` wraps SyncExecutor
- [ ] Uses configurable ExecutorService
- [ ] Returns CompletableFuture<ExecutionResult>
- [ ] Proper thread pool configuration
- [ ] Timeout support
- [ ] Exception handling in async context
- [ ] Unit tests with async verification
- [ ] Performance tests comparing sync vs async

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/executor/
├── AsyncExecutor.java
└── ExecutorConfiguration.java
```

---

#### Task 3.6: Batch Executor Implementation
**Priority:** Medium  
**Estimated Time:** 1 hour  
**Labels:** `core`, `execution`, `batch`

**Description:**
Implement batch executor for parallel execution of multiple contexts.

**Acceptance Criteria:**
- [ ] `BatchExecutor` processes List<ExecutionContext>
- [ ] Uses parallel streams with configurable parallelism
- [ ] Collects all results
- [ ] Stream-based API for large batches
- [ ] Error handling doesn't stop entire batch
- [ ] Unit tests with various batch sizes
- [ ] Performance benchmarks

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/executor/
└── BatchExecutor.java
```

---

#### Task 3.7: Tiered Cache Implementation
**Priority:** Critical  
**Estimated Time:** 2.5 hours  
**Labels:** `core`, `cache`, `gc`

**Description:**
Implement three-tier cache with Strong, Soft, and Weak references.

**Acceptance Criteria:**
- [ ] `TieredRuleCache` with L1 (Caffeine), L2 (Soft), L3 (Weak)
- [ ] L1: Strong refs, LRU eviction, configurable size
- [ ] L2: SoftReference with ConcurrentHashMap
- [ ] L3: WeakReference with ConcurrentHashMap
- [ ] ReferenceQueue monitoring for GC'd entries
- [ ] Promotion logic: L3 → L2 (5+ accesses), L2 → L1 (20+ accesses)
- [ ] Demotion logic: L1 → L2 (TTL/size), L2 → L3 (inactivity)
- [ ] CacheKey with rule name, version, input schema hash
- [ ] Statistics tracking (hit rates, evictions, promotions)
- [ ] Unit tests for each tier
- [ ] Integration tests for promotion/demotion
- [ ] GC stress tests

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/cache/
├── TieredRuleCache.java
├── CacheKey.java
├── CacheTier.java (enum)
├── PromotionPolicy.java
├── DemotionPolicy.java
├── ReferenceManager.java
├── CacheStatistics.java
└── CacheStatsSnapshot.java
```

---

#### Task 3.8: Event Bus Implementation
**Priority:** Medium  
**Estimated Time:** 1 hour  
**Labels:** `core`, `events`

**Description:**
Implement internal event bus for component communication.

**Acceptance Criteria:**
- [ ] `EventBus` with publish/subscribe pattern
- [ ] Thread-safe event distribution
- [ ] Support for async event handlers
- [ ] Event types: Compilation, Execution, Cache, ClassLoader
- [ ] Unit tests for event distribution
- [ ] Performance tests (event throughput)

**Files to Create:**
```
engine-core/src/main/java/com/helix/core/events/
├── EventBus.java
├── EngineEvent.java (base class)
├── EventListener.java (interface)
└── EventType.java (enum)
```

---

#### Task 3.9: Integration Tests for Execution Pipeline
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `testing`, `integration-tests`

**Description:**
End-to-end tests for compile → load → execute → cache pipeline.

**Acceptance Criteria:**
- [ ] Test: Compile rule → Execute → Verify result
- [ ] Test: Execute same rule twice → Verify cache hit
- [ ] Test: Async execution → CompletableFuture handling
- [ ] Test: Batch execution → All results collected
- [ ] Test: ClassLoader isolation → Different loaders
- [ ] Test: Cache eviction → GC clears L3
- [ ] All integration tests pass

**Files to Create:**
```
engine-core/src/test/java/com/helix/core/integration/
├── ExecutionPipelineIntegrationTest.java
├── CacheIntegrationTest.java
└── ClassLoaderIntegrationTest.java
```

---

#### Task 3.10: Sprint 3 Verification
**Priority:** Critical  
**Estimated Time:** 0.5 hours  
**Labels:** `verification`, `milestone`

**Description:**
Verify Sprint 3 deliverables.

**Acceptance Criteria:**
- [ ] Can execute a compiled rule synchronously
- [ ] Can execute a compiled rule asynchronously
- [ ] Can execute a batch of rules in parallel
- [ ] Cache stores and retrieves rules correctly
- [ ] ClassLoaders isolate rules properly
- [ ] All unit tests pass (80%+ coverage)
- [ ] All integration tests pass
- [ ] Memory leak tests pass (ClassLoader unloading)
- [ ] Code committed and pushed

**End-to-End Test:**
```java
// This should work:
RuleEngine engine = new RuleEngine();
String json = "{\"expression\": \"return x > 10\"}";
CompiledRule rule = engine.compileRule(json);
ExecutionContext ctx = new ExecutionContext().set("x", 15);
ExecutionResult result = engine.execute(rule, ctx);
assertTrue(result.isSuccess());
assertTrue((Boolean) result.getValue());

// Execute again - should hit cache
ExecutionResult cached = engine.execute(rule, ctx);
assertTrue(cached.isSuccess());
```

---

### Sprint 3 Summary

**Total Tasks:** 10  
**Total Estimated Time:** 13.5 hours (Adjust: 10 hours with focused effort)  
**Critical Path:** Task 3.1 → Task 3.3 → Task 3.4 → Task 3.7  

**Deliverables:**
- ✅ Hierarchical ClassLoader system
- ✅ Three execution modes (sync/async/batch)
- ✅ Three-tier cache with GC-aware reference management
- ✅ Event bus for component communication
- ✅ Full execution pipeline working end-to-end

**End-of-Sprint Checklist:**
- [ ] Full workflow works: JSON → Compile → Load → Execute → Cache
- [ ] All 10 tasks completed
- [ ] All tests pass
- [ ] Memory leak tests verify ClassLoader cleanup
- [ ] Performance benchmarks established

---


## Sprint 4: Java Agent & Instrumentation (Day 4)

**Milestone:** M4 - Agent Instrumentation & Memory Analysis  
**Duration:** 8-10 hours  
**Dependencies:** Sprint 3 completed  

### Tasks

#### Task 4.1: Agent Entry Points
**Priority:** Critical  
**Estimated Time:** 1.5 hours  
**Labels:** `agent`, `instrumentation`

**Description:**
Implement agent entry points (premain/agentmain) and initialization.

**Acceptance Criteria:**
- [ ] `AgentMain` class with premain() and agentmain() methods
- [ ] Agent configuration loading from properties file
- [ ] Instrumentation API registration
- [ ] Graceful initialization with error handling
- [ ] Logging setup for agent operations
- [ ] Unit tests for configuration parsing

**Files to Create:**
```
engine-agent/src/main/java/com/helix/agent/
├── AgentMain.java
├── AgentConfiguration.java
└── AgentInitializationException.java
```

---

#### Task 4.2: Rule Class Transformer
**Priority:** Critical  
**Estimated Time:** 2 hours  
**Labels:** `agent`, `asm`, `transformation`

**Description:**
Implement ASM-based class transformer for rule classes.

**Acceptance Criteria:**
- [ ] `RuleClassTransformer` implements ClassFileTransformer
- [ ] Detects rule classes (by naming convention or annotation)
- [ ] Uses ASM to inject method entry/exit hooks
- [ ] Preserves original class functionality
- [ ] Unit tests with sample classes
- [ ] Integration tests verify instrumentation works

**Files to Create:**
```
engine-agent/src/main/java/com/helix/agent/transformer/
├── RuleClassTransformer.java
├── RuleInstrumentationVisitor.java
├── ExecutionInstrumentationAdapter.java
└── TransformationException.java
```

---

#### Task 4.3: Allocation Tracker
**Priority:** Medium  
**Estimated Time:** 1.5 hours  
**Labels:** `agent`, `allocation`, `asm`

**Description:**
Implement allocation tracking via bytecode transformation.

**Acceptance Criteria:**
- [ ] `AllocationTracker` intercepts NEW instructions
- [ ] Tracks allocation count per rule
- [ ] Records allocation timestamps
- [ ] Lightweight (minimal overhead)
- [ ] Unit tests verify tracking accuracy
- [ ] Integration tests with real allocations

**Files to Create:**
```
engine-agent/src/main/java/com/helix/agent/transformer/
├── AllocationTracker.java
├── AllocationTrackingVisitor.java
└── AllocationInterceptor.java
```

---

#### Task 4.4: Agent Callback System
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `agent`, `instrumentation`

**Description:**
Implement callback system for instrumented code.

**Acceptance Criteria:**
- [ ] `AgentCallback` with static methods for instrumentation hooks
- [ ] Thread-local `ExecutionStats` for per-thread tracking
- [ ] Methods: onMethodEntry(), onMethodExit(), recordAllocation()
- [ ] Efficient data structures (no locks where possible)
- [ ] Unit tests for callback behavior

**Files to Create:**
```
engine-agent/src/main/java/com/helix/agent/
├── AgentCallback.java
└── ExecutionStats.java
```

---

#### Task 4.5: Memory Analyzer with JOL
**Priority:** High  
**Estimated Time:** 2 hours  
**Labels:** `agent`, `memory`, `jol`

**Description:**
Implement memory analysis using Java Object Layout (JOL).

**Acceptance Criteria:**
- [ ] `MemoryAnalyzer` analyzes object layout
- [ ] `ObjectLayoutInspector` uses JOL ClassLayout/GraphLayout
- [ ] Detects compressed oops status
- [ ] Calculates shallow and deep object sizes
- [ ] Identifies padding and alignment waste
- [ ] Generates MemoryAnalysisReport
- [ ] Unit tests with sample objects
- [ ] Integration tests with real rule instances

**Files to Create:**
```
engine-agent/src/main/java/com/helix/agent/jol/
├── MemoryAnalyzer.java
├── ObjectLayoutInspector.java
└── CompressedOopsDetector.java
```

---

#### Task 4.6: JMX MBean Implementation
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `agent`, `jmx`, `monitoring`

**Description:**
Implement JMX MBeans for runtime control and monitoring.

**Acceptance Criteria:**
- [ ] `EngineControlMBean` interface and implementation
- [ ] `ProfilerControlMBean` interface and implementation
- [ ] Operations: clearCache, triggerGC, dumpHeap, etc.
- [ ] Attributes: uptime, active loaders, memory stats
- [ ] MBean registration with platform MBeanServer
- [ ] Unit tests for MBean operations
- [ ] Manual testing with JConsole

**Files to Create:**
```
engine-agent/src/main/java/com/helix/agent/jmx/
├── EngineControlMBean.java
├── EngineControl.java
├── ProfilerControlMBean.java
├── ProfilerControl.java
└── MBeanRegistry.java
```

---

#### Task 4.7: Agent MANIFEST.MF Configuration
**Priority:** Critical  
**Estimated Time:** 0.5 hours  
**Labels:** `agent`, `build`, `manifest`

**Description:**
Configure agent JAR manifest for proper agent loading.

**Acceptance Criteria:**
- [ ] MANIFEST.MF with Premain-Class and Agent-Class
- [ ] Can-Redefine-Classes: true
- [ ] Can-Retransform-Classes: true
- [ ] Boot-Class-Path with JOL
- [ ] Maven Shade Plugin configured for fat JAR
- [ ] Build produces engine-agent.jar with all dependencies shaded

**Files to Create:**
```
engine-agent/src/main/resources/META-INF/
└── MANIFEST.MF
```

---

#### Task 4.8: Agent Integration Tests
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `testing`, `integration-tests`, `agent`

**Description:**
Integration tests for agent attachment and instrumentation.

**Acceptance Criteria:**
- [ ] Test static attachment (-javaagent)
- [ ] Test dynamic attachment (Attach API)
- [ ] Test class transformation works
- [ ] Test JMX MBeans are accessible
- [ ] Test memory analysis produces reports
- [ ] Test allocation tracking records allocations
- [ ] All integration tests pass

**Files to Create:**
```
engine-agent/src/test/java/com/helix/agent/integration/
├── AgentAttachmentTest.java
├── TransformationIntegrationTest.java
└── JmxIntegrationTest.java
```

---

#### Task 4.9: Agent Configuration Properties
**Priority:** Medium  
**Estimated Time:** 0.5 hours  
**Labels:** `agent`, `configuration`

**Description:**
Create agent configuration file template.

**Acceptance Criteria:**
- [ ] `agent.properties` template with all options
- [ ] Documentation for each property
- [ ] Sensible defaults
- [ ] Example configurations for different scenarios

**Files to Create:**
```
engine-agent/src/main/resources/
├── agent.properties.template
└── agent-config-README.md
```

---

#### Task 4.10: Sprint 4 Verification
**Priority:** Critical  
**Estimated Time:** 0.5 hours  
**Labels:** `verification`, `milestone`

**Description:**
Verify Sprint 4 deliverables.

**Acceptance Criteria:**
- [ ] Agent JAR builds successfully
- [ ] Can attach agent at startup: `java -javaagent:engine-agent.jar`
- [ ] Can attach agent dynamically via Attach API
- [ ] Instrumentation transforms classes correctly
- [ ] JMX MBeans visible in JConsole
- [ ] Memory analysis produces valid reports
- [ ] All tests pass
- [ ] Code committed and pushed

---

### Sprint 4 Summary

**Total Tasks:** 10  
**Total Estimated Time:** 12 hours (Adjust: 10 hours)  
**Critical Path:** Task 4.1 → Task 4.2 → Task 4.7  

**Deliverables:**
- ✅ Functional Java Agent with instrumentation
- ✅ ASM-based class transformation
- ✅ JOL memory analysis
- ✅ JMX MBeans for runtime control
- ✅ Allocation tracking

---

## Sprint 5: Profiler Module (Day 5)

**Milestone:** M5 - JIT/GC Profiling & Observability  
**Duration:** 8-10 hours  
**Dependencies:** Sprint 4 completed  

### Tasks

#### Task 5.1: JIT Compilation Monitor
**Priority:** Critical  
**Estimated Time:** 2 hours  
**Labels:** `profiler`, `jit`, `monitoring`

**Description:**
Implement JIT compilation monitoring by parsing PrintCompilation output.

**Acceptance Criteria:**
- [ ] `JitCompilationMonitor` parses -XX:+PrintCompilation logs
- [ ] Extracts: timestamp, compileId, tier, method, bytecode size
- [ ] `CompilationEvent` record created
- [ ] `TieredCompilationTracker` tracks tier transitions
- [ ] Event listeners for compilation events
- [ ] Unit tests with sample log entries
- [ ] Integration tests with real JVM output

**Files to Create:**
```
engine-profiler/src/main/java/com/helix/profiler/jit/
├── JitCompilationMonitor.java
├── CompilationEvent.java
├── TieredCompilationTracker.java
├── CompilationHistory.java
├── CompilationStats.java
└── InliningAnalyzer.java
```

---

#### Task 5.2: GC Log Analyzer
**Priority:** High  
**Estimated Time:** 2 hours  
**Labels:** `profiler`, `gc`, `monitoring`

**Description:**
Implement GC log analyzer for -Xlog:gc* output.

**Acceptance Criteria:**
- [ ] `GcLogAnalyzer` parses GC logs
- [ ] Extracts: timestamp, collection type, pause time, heap sizes
- [ ] `GcEvent` record created
- [ ] `GcStatistics` with avg/max pause, throughput
- [ ] Safepoint analysis integration
- [ ] Unit tests with sample GC logs
- [ ] Integration tests with real GC output

**Files to Create:**
```
engine-profiler/src/main/java/com/helix/profiler/gc/
├── GcLogAnalyzer.java
├── GcEvent.java
├── GcEventParser.java
├── GcStatistics.java
└── SafepointAnalyzer.java
```

---

#### Task 5.3: async-profiler Integration
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `profiler`, `async-profiler`, `flamegraphs`

**Description:**
Integrate async-profiler for CPU and allocation profiling.

**Acceptance Criteria:**
- [ ] `AsyncProfilerIntegration` controls async-profiler
- [ ] Support for CPU, allocation, lock profiling modes
- [ ] `FlameGraphGenerator` produces HTML flame graphs
- [ ] `ProfileComparator` compares before/after profiles
- [ ] Unit tests for profiler control
- [ ] Integration tests generate actual flame graphs

**Files to Create:**
```
engine-profiler/src/main/java/com/helix/profiler/async/
├── AsyncProfilerIntegration.java
├── ProfileMode.java (enum)
├── FlameGraphGenerator.java
└── ProfileComparator.java
```

---

#### Task 5.4: JFR Custom Events
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `profiler`, `jfr`, `observability`

**Description:**
Define and implement custom JFR events for Helix.

**Acceptance Criteria:**
- [ ] `RuleExecutionEvent` with JFR annotations
- [ ] `RuleCompilationEvent` for compilation tracking
- [ ] `ClassLoaderCreatedEvent` for ClassLoader lifecycle
- [ ] `CacheEvictionEvent` for cache GC events
- [ ] `JfrEventRecorder` records events
- [ ] `JfrRecordingManager` manages recording lifecycle
- [ ] Unit tests verify events are recorded
- [ ] Integration tests produce viewable JFR files

**Files to Create:**
```
engine-profiler/src/main/java/com/helix/profiler/jfr/
├── RuleExecutionEvent.java
├── RuleCompilationEvent.java
├── ClassLoaderCreatedEvent.java
├── CacheEvictionEvent.java
├── MemoryAnalysisEvent.java
├── JfrEventRecorder.java
└── JfrRecordingManager.java
```

---

#### Task 5.5: Live Dashboard Implementation
**Priority:** Medium  
**Estimated Time:** 2 hours  
**Labels:** `profiler`, `ui`, `dashboard`

**Description:**
Implement terminal-based live dashboard for real-time monitoring.

**Acceptance Criteria:**
- [ ] `LiveDashboard` renders to terminal
- [ ] Displays: JIT stats, GC stats, cache stats, memory usage
- [ ] Refreshes every 1 second
- [ ] Clean terminal UI with borders
- [ ] Uses JLine3 or similar for terminal control
- [ ] Manual testing verifies dashboard looks good

**Files to Create:**
```
engine-profiler/src/main/java/com/helix/profiler/interactive/
├── LiveDashboard.java
└── DashboardRenderer.java
```

---

#### Task 5.6: Metrics Collector Implementation
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `profiler`, `metrics`, `observability`

**Description:**
Implement metrics collection using Micrometer-style API.

**Acceptance Criteria:**
- [ ] `EngineMetrics` with counters, timers, gauges
- [ ] Metrics for: rules compiled, rules executed, cache hits/misses
- [ ] Metrics for: ClassLoaders active, Metaspace used
- [ ] Timer percentiles (p50, p95, p99)
- [ ] Unit tests verify metrics collection

**Files to Create:**
```
engine-profiler/src/main/java/com/helix/profiler/metrics/
├── EngineMetrics.java
├── MetricRegistry.java
└── MetricsSnapshot.java
```

---

#### Task 5.7: Health Check System
**Priority:** Medium  
**Estimated Time:** 1 hour  
**Labels:** `profiler`, `health`, `monitoring`

**Description:**
Implement health check system for engine components.

**Acceptance Criteria:**
- [ ] `EngineHealthCheck` checks system health
- [ ] Checks: Metaspace usage, ClassLoader leaks, cache effectiveness, GC pressure
- [ ] Returns `HealthStatus` with severity and issues
- [ ] Unit tests for health check logic
- [ ] Integration tests with unhealthy scenarios

**Files to Create:**
```
engine-profiler/src/main/java/com/helix/profiler/health/
├── EngineHealthCheck.java
├── HealthStatus.java
├── HealthIssue.java
└── Severity.java (enum)
```

---

#### Task 5.8: Profiler Integration Tests
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `testing`, `integration-tests`

**Description:**
Integration tests for profiler components.

**Acceptance Criteria:**
- [ ] Test JIT monitor captures compilation events
- [ ] Test GC analyzer parses GC logs correctly
- [ ] Test async-profiler generates flame graphs
- [ ] Test JFR events are recorded and readable
- [ ] All integration tests pass

**Files to Create:**
```
engine-profiler/src/test/java/com/helix/profiler/integration/
├── JitMonitorIntegrationTest.java
├── GcAnalyzerIntegrationTest.java
└── JfrIntegrationTest.java
```

---

#### Task 5.9: Profiler Module Documentation
**Priority:** Medium  
**Estimated Time:** 0.5 hours  
**Labels:** `documentation`

**Description:**
Document profiler usage and configuration.

**Acceptance Criteria:**
- [ ] README with profiler overview
- [ ] Examples for each profiling mode
- [ ] JVM flags reference
- [ ] Interpretation guide for flame graphs

**Files to Create:**
```
engine-profiler/
└── README.md
```

---

#### Task 5.10: Sprint 5 Verification
**Priority:** Critical  
**Estimated Time:** 0.5 hours  
**Labels:** `verification`, `milestone`

**Description:**
Verify Sprint 5 deliverables.

**Acceptance Criteria:**
- [ ] JIT monitor captures compilation events
- [ ] GC analyzer produces statistics
- [ ] async-profiler generates flame graphs
- [ ] JFR recordings contain custom events
- [ ] Live dashboard displays real-time metrics
- [ ] Health checks detect issues
- [ ] All tests pass
- [ ] Code committed and pushed

---

### Sprint 5 Summary

**Total Tasks:** 10  
**Total Estimated Time:** 12.5 hours (Adjust: 10 hours)  
**Critical Path:** Task 5.1 → Task 5.2 → Task 5.4  

**Deliverables:**
- ✅ JIT compilation monitoring
- ✅ GC log analysis
- ✅ async-profiler integration with flame graphs
- ✅ Custom JFR events
- ✅ Live dashboard
- ✅ Metrics and health checks

---


## Sprint 6: Experiments & Benchmarks (Day 6)

**Milestone:** M6 - JVM Experiments & Performance Benchmarks  
**Duration:** 8-10 hours  
**Dependencies:** Sprint 5 completed  

### Tasks

#### Task 6.1: Metaspace Leak Experiment
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `experiments`, `metaspace`, `classloading`

**Description:**
Create experiment demonstrating Metaspace leak and proper cleanup.

**Acceptance Criteria:**
- [ ] `MetaspaceLeakExperiment` with demonstrateLeak() method
- [ ] Intentionally leaks ClassLoaders to trigger OOM: Metaspace
- [ ] `demonstrateCleanup()` shows proper ClassLoader cleanup
- [ ] Monitors Metaspace usage throughout
- [ ] JVM flags documented (-XX:MaxMetaspaceSize, etc.)
- [ ] README explains what to observe
- [ ] Runnable via `mvn exec:java`

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/scenarios/
├── MetaspaceLeakExperiment.java
└── MetaspaceMonitor.java
```

---

#### Task 6.2: JIT Compilation Tiers Experiment
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `experiments`, `jit`, `compilation`

**Description:**
Create experiment observing tiered compilation (C1 → C2).

**Acceptance Criteria:**
- [ ] `JitCompilationExperiment` with observeCompilationTiers()
- [ ] Executes rule 100, 2000, 15000 times to trigger tier transitions
- [ ] `testInliningLimits()` tests bytecode size vs inlining
- [ ] Logs compilation events at each milestone
- [ ] JVM flags documented (-XX:+PrintCompilation, etc.)
- [ ] README explains expected JIT behavior
- [ ] Runnable and observable

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/scenarios/
├── JitCompilationExperiment.java
└── CompilationObserver.java
```

---

#### Task 6.3: GC Stress Experiment
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `experiments`, `gc`, `references`

**Description:**
Create experiments for GC behavior with Soft/Weak references.

**Acceptance Criteria:**
- [ ] `GcStressExperiment` with softReferenceUnderPressure()
- [ ] `weakReferenceImmediate()` shows weak ref GC behavior
- [ ] Memory pressure applied to trigger SoftReference clearing
- [ ] Monitors cache sizes before/after GC
- [ ] Tests SoftRefLRUPolicyMSPerMB tuning
- [ ] JVM flags documented
- [ ] README explains GC reference behavior

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/scenarios/
└── GcStressExperiment.java
```

---

#### Task 6.4: Object Layout Experiment
**Priority:** Medium  
**Estimated Time:** 1 hour  
**Labels:** `experiments`, `memory`, `jol`

**Description:**
Create experiment comparing object sizes with/without compressed oops.

**Acceptance Criteria:**
- [ ] `ObjectLayoutExperiment` with compareCompressedOops()
- [ ] Uses JOL to print object layouts
- [ ] Compares shallow vs deep sizes
- [ ] Identifies padding and alignment
- [ ] Run instructions for both -XX:+UseCompressedOops and -XX:-UseCompressedOops
- [ ] README explains compressed oops impact

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/scenarios/
└── ObjectLayoutExperiment.java
```

---

#### Task 6.5: Safepoint Experiment
**Priority:** Low  
**Estimated Time:** 1 hour  
**Labels:** `experiments`, `safepoint`, `gc`

**Description:**
Create experiment observing safepoint behavior.

**Acceptance Criteria:**
- [ ] `SafepointExperiment` with monitorSafepoints()
- [ ] Creates workload with varying safepoint opportunities
- [ ] Triggers GC from another thread
- [ ] JVM flags for safepoint logging documented
- [ ] README explains safepoint mechanics

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/scenarios/
└── SafepointExperiment.java
```

---

#### Task 6.6: Experiment Runner
**Priority:** Medium  
**Estimated Time:** 0.5 hours  
**Labels:** `experiments`, `runner`

**Description:**
Create unified experiment runner with CLI interface.

**Acceptance Criteria:**
- [ ] `ExperimentRunner` main class with switch statement
- [ ] Runs experiments by name: `java -jar experiments.jar metaspace-leak`
- [ ] Lists available experiments with descriptions
- [ ] Error handling for unknown experiments
- [ ] Help text with usage examples

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/
└── ExperimentRunner.java
```

---

#### Task 6.7: Compilation Benchmark (JMH)
**Priority:** Critical  
**Estimated Time:** 1.5 hours  
**Labels:** `benchmarks`, `jmh`, `compilation`

**Description:**
JMH benchmarks for rule compilation performance.

**Acceptance Criteria:**
- [ ] `CompilationBenchmark` with JMH annotations
- [ ] Benchmarks: simple rule, complex rule, ByteBuddy vs ASM
- [ ] @Warmup and @Measurement configured
- [ ] Multiple @Param values for testing
- [ ] Baseline results documented
- [ ] Runnable via `mvn exec:exec@jmh`

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/benchmarks/
└── CompilationBenchmark.java
```

---

#### Task 6.8: Execution Benchmark (JMH)
**Priority:** Critical  
**Estimated Time:** 1.5 hours  
**Labels:** `benchmarks`, `jmh`, `execution`

**Description:**
JMH benchmarks for rule execution performance.

**Acceptance Criteria:**
- [ ] `ExecutionBenchmark` with JMH annotations
- [ ] Benchmarks: cold rule, hot rule, concurrent execution
- [ ] JIT warmup (20000 iterations) before measurement
- [ ] @Threads annotation for concurrency tests
- [ ] Baseline results documented
- [ ] Throughput mode for ops/sec

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/benchmarks/
└── ExecutionBenchmark.java
```

---

#### Task 6.9: Cache Benchmark (JMH)
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `benchmarks`, `jmh`, `cache`

**Description:**
JMH benchmarks for cache performance.

**Acceptance Criteria:**
- [ ] `CacheBenchmark` with JMH annotations
- [ ] Benchmarks: cache hit, cache miss, eviction
- [ ] @Param for different cache sizes
- [ ] Measures L1/L2/L3 lookup times
- [ ] Baseline results documented

**Files to Create:**
```
engine-experiments/src/main/java/com/helix/experiments/benchmarks/
└── CacheBenchmark.java
```

---

#### Task 6.10: Sprint 6 Verification
**Priority:** Critical  
**Estimated Time:** 0.5 hours  
**Labels:** `verification`, `milestone`

**Description:**
Verify Sprint 6 deliverables.

**Acceptance Criteria:**
- [ ] All 5 experiments run successfully
- [ ] Metaspace leak experiment triggers OOM as expected
- [ ] JIT experiment shows tier transitions
- [ ] GC experiment shows reference clearing
- [ ] All 3 JMH benchmarks run and produce results
- [ ] Baseline performance documented
- [ ] Experiments README created
- [ ] Code committed and pushed

---

### Sprint 6 Summary

**Total Tasks:** 10  
**Total Estimated Time:** 11 hours (Adjust: 10 hours)  
**Critical Path:** Task 6.1 → Task 6.2 → Task 6.7 → Task 6.8  

**Deliverables:**
- ✅ 5 JVM behavior experiments (Metaspace, JIT, GC, Object Layout, Safepoint)
- ✅ 3 JMH benchmark suites (Compilation, Execution, Cache)
- ✅ Experiment runner CLI
- ✅ Performance baselines established

---

## Sprint 7: Integration, Documentation & Polish (Day 7)

**Milestone:** M7 - Production Readiness  
**Duration:** 8-10 hours  
**Dependencies:** Sprint 6 completed  

### Tasks

#### Task 7.1: End-to-End Integration Tests
**Priority:** Critical  
**Estimated Time:** 2 hours  
**Labels:** `testing`, `integration`, `e2e`

**Description:**
Comprehensive end-to-end integration tests across all modules.

**Acceptance Criteria:**
- [ ] Test: JSON → Compile → Load → Execute → Cache → Profile
- [ ] Test: Agent instrumentation in real workflow
- [ ] Test: JFR events recorded during execution
- [ ] Test: Metrics collected correctly
- [ ] Test: Health checks detect issues
- [ ] Test: All 3 execution modes work together
- [ ] Test: Cache eviction under memory pressure
- [ ] All E2E tests pass

**Files to Create:**
```
engine-core/src/test/java/com/helix/integration/
└── EndToEndIntegrationTest.java
```

---

#### Task 7.2: Main Application , Modern TUI & Automation CLI
**Priority:** High  
**Estimated Time:** 1.5 hours  
**Labels:** `application`, `cli`, `user-interface`

**Priority:** Critical  
**Estimated Time:** 3 hours  
**Labels:** `sprint-7`, `application`, `cli`, `tui`, `automation`

**Description:**
Create a production-ready, modern CLI that rivals the UX of Go/Rust tools while remaining 100% native to the JVM. The application must serve two distinct user experiences:
1. **Interactive Mode (Human UX):** Utilize a modern Java TUI framework (e.g., Lanterna) to build a rich, curses-style dashboard with reactive data updates, mimicking the Elm-architecture feel of Bubble Tea/Ratatui.
2. **Batch/Pipeline Mode (Machine UX):** Utilize strict, schema-driven machine-readable outputs (JSON, CSV) via Picocli to enable seamless piping into APM telemetry parsers or `jq`.

Additionally, the application startup must be heavily optimized using AppCDS and HotSpot flags to minimize the dreaded "JVM cold start" delay, making the CLI feel snappy and instant.

**Acceptance Criteria:**
- [ ] `HelixApplication` implemented using **Picocli** for robust argument parsing, auto-complete generation, and ANSI-colored help menus.
- [ ] Subcommands implemented: `compile`, `execute`, `profile`, `experiment`.
- [ ] **[Advanced UX]** Interactive `--dashboard` mode implemented using Lanterna, providing a bordered, auto-refreshing terminal UI for live JIT/GC monitoring.
- [ ] **[Advanced Automation]** `--output` flag implemented supporting `text` (default), `json`, and `csv` formats for all execution and profiling results. Ensure `stdout` contains *only* the data payload for clean piping.
- [ ] **[Advanced UX]** `--quiet` or `--headless` mode implemented to suppress non-essential logging in CI pipelines.
- [ ] Standardized exit codes (e.g., `0` for success, `1` for compilation failure, `2` for execution timeout) implemented to instantly fail CI builds on errors.
- [ ] **[Performance]** Generate an AppCDS (Application Class-Data Sharing) archive (`helix.jsa`) during the package phase to drastically reduce CLI startup time.

**Files to Create/Modify:**
```text
engine-core/src/main/java/com/helix/
├── HelixApplication.java
├── cli/
│   ├── CliCommand.java
│   ├── CompileCommand.java
│   ├── ExecuteCommand.java
│   ├── ProfileCommand.java
│   ├── ExperimentCommand.java
│   ├── ui/
│   │   ├── TuiDashboard.java
│   │   └── TerminalRenderer.java
│   └── output/
│       ├── OutputFormatter.java
│       └── JsonFormatter.java
└── scripts/
    ├── start-helix.sh
    └── generate-appcds.sh
```

**Technical Notes:**

- Startup Tuning: In the start-helix.sh script, include JVM flags optimized for short-lived CLI commands when not in a long-running dashboard mode (e.g., -XX:TieredStopAtLevel=1 for faster C1-only startup, and -XX:SharedArchiveFile=helix.jsa for AppCDS).

- AOT Limitations: We cannot use GraalVM Native Image for this tool because Helix relies on dynamic ClassLoader.defineClass() and runtime ByteBuddy generation. Fast HotSpot startup techniques are required instead.

---

#### Task 7.3: Project README
**Priority:** Critical  
**Estimated Time:** 1.5 hours  
**Labels:** `documentation`, `readme`

**Description:**
Create comprehensive README for the project.

**Acceptance Criteria:**
- [ ] Project overview and goals
- [ ] Architecture diagram (reference all-in-all diagram for the whole system) 
- [ ] Quick start guide
- [ ] Installation instructions
- [ ] Usage examples for all features
- [ ] JVM experiments guide
- [ ] Performance benchmarks results
- [ ] Contributing guidelines
- [ ] License information

**Files to Create:**
```
README.md
CONTRIBUTING.md
LICENSE

```

---

#### Task 7.4: Module READMEs
**Priority:** Medium  
**Estimated Time:** 1 hour  
**Labels:** `documentation`

**Description:**
Create README for each module explaining its purpose and API.

**Acceptance Criteria:**
- [ ] engine-api/README.md - API overview
- [ ] engine-core/README.md - Core engine usage
- [ ] engine-profiler/README.md - Profiling guide
- [ ] engine-agent/README.md - Agent usage
- [ ] engine-experiments/README.md - Experiments guide
- [ ] Each README has code examples

---

#### Task 7.5: Javadoc Generation
**Priority:** Medium  
**Estimated Time:** 1 hour  
**Labels:** `documentation`, `javadoc`

**Description:**
Generate comprehensive Javadoc for all public APIs.

**Acceptance Criteria:**
- [ ] All public classes have Javadoc
- [ ] All public methods have @param and @return
- [ ] Code examples in Javadoc where appropriate
- [ ] Maven Javadoc Plugin configured
- [ ] `mvn javadoc:aggregate` generates site
- [ ] Javadoc warnings fixed

---

#### Task 7.6: GitHub Actions CI/CD
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `ci-cd`, `github-actions`

**Description:**
Set up a rigorous GitHub Actions workflow that acts as an automated gatekeeper. Beyond standard unit and integration testing, this pipeline must implement Continuous Benchmarking. Because Helix manipulates low-level JVM internals, the CI must automatically run JMH throughput benchmarks on every pull request and compare them against the `main` branch baseline. If a PR introduces a statistically significant performance regression (e.g., >5% drop in ops/sec), the build must fail.

**Acceptance Criteria:**
- [ ] Primary CI workflow triggers on `push` to `main` and all `pull_request` events.
- [ ] Pipeline builds all 5 Maven modules and executes unit/integration tests with a >80% JaCoCo coverage gate.
- [ ] **[Advanced]** JMH Benchmark Step: Pipeline executes `CompilationBenchmark` and `ExecutionBenchmark`.
- [ ] **[Advanced]** Performance Regression Gate: Pipeline uses a tool (like `jmh-github-action` or a custom comparison script) to compare PR benchmark results against the `main` branch, failing the PR if performance drops below the defined threshold.
- [ ] **[Advanced]** Automated reporting: Benchmark delta (improvements or regressions) is automatically commented on the Pull Request by a GitHub bot.
- [ ] Build artifacts (compiled JARs, JaCoCo reports, JFR recordings from E2E tests) are uploaded via `actions/upload-artifact`.

**Files to Create/Modify:**
```text
.github/workflows/
├── ci.yml
└── benchmark-pr.yml
```
**Technical Notes:**

- JMH benchmarks in CI can be noisy due to shared GitHub runner resources. Consider pinning the benchmark action to run with a fixed number of forks/warmups tailored for CI environments to reduce variance, or run them on a self-hosted runner if available.

---

#### Task 7.7: Example Rules Collection
**Priority:** Medium  
**Estimated Time:** 0.5 hours  
**Labels:** `examples`, `documentation`

**Description:**
Create collection of example rules for common scenarios.

**Acceptance Criteria:**
- [ ] 10+ example JSON rules
- [ ] Categories: fraud detection, business logic, data validation
- [ ] Comments explaining each rule
- [ ] README with rule catalog

**Files to Create:**
```
examples/rules/
├── fraud-detection.json
├── credit-approval.json
├── age-verification.json
├── discount-calculator.json
└── README.md
```

---

#### Task 7.8: Performance Tuning Guide
**Priority:** Medium  
**Estimated Time:** 0.5 hours  
**Labels:** `documentation`, `performance`

**Description:**
Document comprehensive performance tuning recommendations and explicitly detail the mechanical sympathy of the Helix engine. Performance engineers require transparency regarding overhead. This guide must move beyond basic JVM flags and explicitly benchmark and explain the memory footprint and allocation costs of our core abstractions (Executors, Cache Tiers, Bytecode generators) so users can make informed architectural trade-offs.

**Acceptance Criteria:**
- [ ] JVM tuning matrix documented (GC tuning, Metaspace limits, JIT thresholds).
- [ ] Cache sizing guidelines documented based on heap availability and SoftReference retention (`SoftRefLRUPolicyMSPerMB`).
- [ ] **[Advanced]** Abstraction Cost Analysis: Explicitly document the allocation overhead (in bytes/ops) of using the `AsyncExecutor` (CompletableFuture allocations, thread pool overhead) versus the `SyncExecutor`.
- [ ] **[Advanced]** Memory Footprint Analysis: Detail the exact shallow and deep byte footprint of rules sitting in L1 (Strong) vs L2 (Soft) vs L3 (Weak) caches based on JOL experiment findings.
- [ ] **[Advanced]** JIT Warm-up guide: Document the exact invocation thresholds required to push generated bytecode from Interpreter → C1 → C2, and how users can script warm-up runs.
- [ ] When to use ASM vs ByteBuddy guide based on JMH compilation throughput benchmarks.

**Files to Create/Modify:**
```text
docs/
├── PERFORMANCE_TUNING.md
└── ABSTRACTION_COSTS.md
```
---

#### Task 7.9: Release Packaging
**Priority:** High  
**Estimated Time:** 1 hour  
**Labels:** `release`, `packaging`

**Description:**
Package the application for two distinct consumption models: End-Users (CLI) and Developers (SDK/Library). We must provide a standalone, highly-optimized CLI distribution (with AppCDS archives and attachable Java Agents), while also configuring the Maven modules (`engine-api`, `engine-core`, `engine-profiler`) to generate proper `-sources.jar` and `-javadoc.jar` artifacts for publishing to a Maven repository (like GitHub Packages or Maven Central).

**Acceptance Criteria:**
- [ ] **[CLI Distribution]** Maven Assembly Plugin configured to create a distribution ZIP containing `engine-core.jar`, the isolated `engine-agent.jar`, and all executable `.sh` scripts.
- [ ] **[Library Distribution]** Maven Source Plugin and Maven Javadoc Plugin configured in the parent `pom.xml` to automatically attach source code and javadoc artifacts during the `package` phase.
- [ ] `start-helix.sh` script automatically detects the host OS and applies the correct HotSpot tuning flags (e.g., `-XX:SharedArchiveFile`).
- [ ] `pom.xml` configured with proper `<licenses>`, `<developers>`, and `<scm>` tags to meet Maven Central sync requirements.
- [ ] Automated release creation via GitHub Actions (creating a GitHub Release with attached ZIP artifacts when a version tag is pushed).

**Files to Create/Modify:**
```text
pom.xml (Parent)
engine-core/src/assembly/
└── distribution.xml
scripts/
├── start-helix.sh
└── run-experiment.sh
```

**Technical Notes:**

- Ensure the agent JAR is strictly excluded from the standard classpath in the startup scripts, as it must only be loaded via the -javaagent flag to avoid ASM/JOL dependency conflicts at runtime.

---

#### Task 7.10: Sprint 7 Final Verification
**Priority:** Critical  
**Estimated Time:** 1 hour  
**Labels:** `verification`, `milestone`, `release`

**Description:**
Final verification and production readiness check.

**Acceptance Criteria:**
- [ ] All 6 previous sprints' tasks completed
- [ ] All tests pass (unit + integration + E2E)
- [ ] All benchmarks run successfully
- [ ] All experiments run successfully
- [ ] Documentation complete and accurate
- [ ] CI/CD pipeline green
- [ ] No critical bugs or warnings
- [ ] Code coverage > 80%
- [ ] Performance meets baseline
- [ ] Distribution package works on clean machine
- [ ] Project ready for production use
- [ ] Git tags: v1.0.0-SNAPSHOT

**Final Checklist:**
```
[ ] Project builds: mvn clean install
[ ] All tests pass: mvn verify
[ ] Benchmarks run: mvn exec:exec@jmh
[ ] Experiments run: java -jar experiments.jar --list
[ ] Agent attaches: java -javaagent:engine-agent.jar -jar engine-core.jar
[ ] JMX MBeans visible in JConsole
[ ] JFR recordings playable in Mission Control
[ ] Flame graphs generated
[ ] Live dashboard works
[ ] CLI commands work
[ ] Documentation complete
[ ] CI/CD pipeline passes
[ ] Distribution package contains all files
[ ] Clean Git history with meaningful commits
```

---

### Sprint 7 Summary

**Total Tasks:** 10  
**Total Estimated Time:** 11 hours (Adjust: 10 hours)  
**Critical Path:** Task 7.1 → Task 7.2 → Task 7.10  

**Deliverables:**
- ✅ End-to-end integration tests
- ✅ Main application with CLI
- ✅ Complete documentation (README, Javadoc, guides)
- ✅ CI/CD pipeline
- ✅ Example rules
- ✅ Distribution package
- ✅ Production-ready v1.0.0

---


---

## Project Summary

### Overall Statistics

**Total Sprints:** 7 (7 days)  
**Total Tasks:** 70 tasks  
**Total Estimated Time:** ~80 hours (avg 11.4 hours/day, adjusted to 10 hours/day with focus)  

**Modules:**
- engine-api: 7 tasks (Sprint 1)
- engine-core: 28 tasks (Sprints 2-3)
- engine-agent: 10 tasks (Sprint 4)
- engine-profiler: 10 tasks (Sprint 5)
- engine-experiments: 10 tasks (Sprint 6)
- Integration & Docs: 10 tasks (Sprint 7)

**Test Coverage Target:** 80%+ across all modules  
**Performance Benchmarks:** Baseline established in Sprint 6  

---

## Critical Path

```
Day 1: API Layer (foundation)
  ↓
Day 2: Compilation Pipeline (JSON → Bytecode)
  ↓
Day 3: Execution & Caching (ClassLoaders, Executors, Cache)
  ↓
Day 4: Agent & Instrumentation (ASM transformation, JOL, JMX)
  ↓
Day 5: Profiler (JIT, GC, async-profiler, JFR, Dashboard)
  ↓
Day 6: Experiments & Benchmarks (5 experiments, 3 JMH benchmarks)
  ↓
Day 7: Integration, Docs, Polish (E2E tests, CLI, README, CI/CD)
```

---

## Risk Management

### High-Risk Items

| Risk | Impact | Mitigation | Sprint |
|------|--------|------------|--------|
| Bytecode generation bugs | High | Extensive unit tests, verify with javap | Sprint 2 |
| ClassLoader memory leaks | High | Unit tests with WeakReference, monitoring | Sprint 3 |
| Agent transformation failures | High | Gradual rollout, fallback to no-op | Sprint 4 |
| JIT/GC log parsing brittleness | Medium | Version-specific parsers, error handling | Sprint 5 |
| Performance regression | Medium | Continuous benchmarking, baseline tracking | Sprint 6 |
| Integration issues | Medium | Daily integration tests, early detection | Sprint 7 |

### Time Management Strategies

1. **Focus on Critical Path:** Prioritize tasks marked "Critical"
2. **Parallel Work:** Tests can be written while implementation is fresh
3. **Timeboxing:** Strict time limits per task; defer non-critical if needed
4. **Daily Commits:** Commit working code at end of each sprint
5. **MVP First:** Core functionality before polish

---

## GitHub Projects Setup

### Project Board Structure

**Board Name:** Helix - JVM Scripting Engine Development  

**Columns:**
1. **Backlog** - All tasks not yet started
2. **Sprint Planning** - Tasks selected for current sprint
3. **In Progress** - Actively being worked on
4. **Testing** - Implementation done, testing in progress
5. **Review** - Ready for review
6. **Done** - Completed and merged

**Milestones:**
- M1: Foundation & API Layer (Day 1)
- M2: Rule Compilation Pipeline (Day 2)
- M3: Rule Execution & Class Loading (Day 3)
- M4: Agent Instrumentation & Memory Analysis (Day 4)
- M5: JIT/GC Profiling & Observability (Day 5)
- M6: JVM Experiments & Performance Benchmarks (Day 6)
- M7: Production Readiness (Day 7)

---

## GitHub Issue Template

### Issue Format

Each task from the implementation plan should be converted to a GitHub issue using this template:

```markdown
## Task: [Task Name]

**Sprint:** [Sprint Number]  
**Milestone:** [Milestone Name]  
**Priority:** [Critical/High/Medium/Low]  
**Estimated Time:** [Hours]  
**Labels:** `sprint-N`, `module-name`, `task-type`

### Description
[Brief description from task]

### Acceptance Criteria
- [ ] [Criterion 1]
- [ ] [Criterion 2]
- [ ] [Criterion 3]

### Files to Create/Modify
```
path/to/file1.java
path/to/file2.java
```

### Technical Notes
[Any technical considerations, dependencies, or implementation hints]

### Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing
- [ ] Code reviewed (self-review for solo project)
- [ ] Documentation updated
- [ ] Committed to Git

### Related Issues
- Depends on: #[issue number]
- Blocks: #[issue number]
```

---

## Example GitHub Issues

### Sprint 1 - Task 1.1

```markdown
## Task: Project Structure Setup

**Sprint:** Sprint 1  
**Milestone:** M1 - Foundation & API Layer  
**Priority:** Critical  
**Estimated Time:** 1.5 hours  
**Labels:** `sprint-1`, `setup`, `infrastructure`

### Description
Create the multi-module Maven project structure with all 5 modules.

### Acceptance Criteria
- [ ] Root `pom.xml` created with parent configuration
- [ ] 5 module directories created: `engine-api`, `engine-core`, `engine-profiler`, `engine-agent`, `engine-experiments`
- [ ] Each module has its own `pom.xml`
- [ ] Module dependencies properly configured
- [ ] Java 17+ source/target configured
- [ ] Project builds successfully with `mvn clean install`

### Technical Notes
- Use Maven 3.9+
- Set encoding to UTF-8
- Configure Maven Compiler Plugin 3.11+

### Definition of Done
- [ ] Implementation complete
- [ ] Build passes
- [ ] Committed to Git branch `feature/project-structure`
```

---

### Sprint 2 - Task 2.4

```markdown
## Task: ByteBuddy Bytecode Generator

**Sprint:** Sprint 2  
**Milestone:** M2 - Rule Compilation Pipeline  
**Priority:** Critical  
**Estimated Time:** 2.5 hours  
**Labels:** `sprint-2`, `core`, `bytecode`, `bytebuddy`

### Description
Implement ByteBuddy-based bytecode generator that converts AST to executable classes.

### Acceptance Criteria
- [ ] `ByteBuddyGenerator` class created
- [ ] Generates classes implementing `CompiledRule` interface
- [ ] AST-to-bytecode conversion for all node types
- [ ] Method delegation for rule execution
- [ ] Generated classes are properly named and versioned
- [ ] Unit tests verify generated bytecode executes correctly
- [ ] Integration tests with complex rules

### Files to Create
```
engine-core/src/main/java/com/helix/core/bytecode/
├── ByteBuddyGenerator.java
├── BytecodeGenerator.java (interface)
├── ExecutionInterceptor.java
└── BytecodeGenerationException.java
```

### Test Cases
- Simple arithmetic expression: `return x + y`
- Comparison: `return amount > 1000`
- Logical operators: `return x > 10 && y < 20`
- Method calls: `return name.equals("test")`

### Technical Notes
- Use ByteBuddy's `subclass()` API
- Implement `MethodDelegation` for dynamic execution
- Keep generated method size < 35 bytes for JIT inlining hints

### Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing (80%+ coverage)
- [ ] Integration tests passing
- [ ] Javadoc added
- [ ] Code reviewed
- [ ] Committed to Git

### Related Issues
- Depends on: #2.2 (AST Builder)
- Depends on: #2.3 (Type Checker)
- Blocks: #2.7 (Rule Compiler Integration)
```

---

## Quick Reference: Task Conversion Script

To convert all tasks to GitHub issues, use this shell script:

```bash
#!/bin/bash
# create-issues.sh - Creates GitHub issues from implementation plan

REPO="username/helix"  # Replace with your repo
TOKEN="your_github_token"  # Replace with your token

# Sprint 1 tasks
gh issue create --repo $REPO \
  --title "Task 1.1: Project Structure Setup" \
  --milestone "M1 - Foundation & API Layer" \
  --label "sprint-1,setup,infrastructure" \
  --body "$(cat issues/task-1-1.md)"

# Repeat for all 70 tasks...
```

---

## Production Readiness Criteria

### Before Declaring "Production Ready"

- [ ] **Functional Completeness**
  - [ ] All 5 modules implemented and integrated
  - [ ] All critical features working
  - [ ] CLI functional
  - [ ] Agent attachable

- [ ] **Quality Assurance**
  - [ ] 80%+ code coverage
  - [ ] All tests passing
  - [ ] No critical bugs
  - [ ] Performance benchmarks meet targets

- [ ] **Documentation**
  - [ ] README complete
  - [ ] API documentation (Javadoc)
  - [ ] Architecture diagrams
  - [ ] Usage examples
  - [ ] Performance tuning guide

- [ ] **Operations**
  - [ ] CI/CD pipeline working
  - [ ] Distribution package builds
  - [ ] Logging properly configured
  - [ ] Health checks working
  - [ ] Metrics exposed

- [ ] **Security**
  - [ ] No hardcoded secrets
  - [ ] Input validation
  - [ ] Safe bytecode generation
  - [ ] Proper error handling

---

## Post-Launch Enhancements (Beyond 7 Days)

### Future Roadmap (Not in Scope)

1. **Additional Features**
   - Rule versioning and migration
   - Distributed caching (Redis/Hazelcast)
   - Web-based dashboard
   - RESTful API
   - GraalVM native image support

2. **Advanced Profiling**
   - CPU flame graphs with thread filtering
   - Memory leak detection automation
   - Anomaly detection in performance metrics
   - Integration with APM tools (Datadog, New Relic)

3. **Rule DSL Extensions**
   - Control flow (if/else, loops)
   - Custom function registry
   - External data source integration
   - Rule chaining and composition

4. **Enterprise Features**
   - Multi-tenancy support
   - Rule governance (approval workflows)
   - A/B testing framework
   - Blue/green rule deployments

---

## Success Metrics

### Sprint Completion Criteria

Each sprint is considered successful when:
1. All critical tasks completed
2. All tests passing
3. Code committed to Git
4. Sprint demo works (can show working feature)
5. No blocking issues for next sprint

### Project Completion Criteria

The project is complete when:
1. All 7 sprints finished
2. End-to-end workflow works: JSON → Compile → Execute → Profile
3. All 5 experiments run successfully
4. All 3 JMH benchmarks produce results
5. Documentation is complete
6. Distribution package works on clean machine
7. CI/CD pipeline is green
8. Code is production-ready

---

## Final Notes

### Project Name: Helix

**Why "Helix"?**
- Represents the double helix structure - like DNA, bytecode and JVM internals are intertwined
- Spiraling upward symbolizes learning and growth
- Scientific and technical, fitting for a JVM internals project
- Short, memorable, and easy to type

### Project Motto

*"Unravel the JVM, one bytecode at a time."*

### License

Apache License 2.0 (recommended for maximum reusability)

---

**END OF IMPLEMENTATION PLAN**

---

## Appendix: Daily Sprint Checklist

### Daily Routine (Copy for Each Day)

**Morning (Start of Sprint)**
- [ ] Review sprint goals
- [ ] Check task dependencies
- [ ] Set up development environment
- [ ] Create feature branch: `feature/sprint-N-description`

**During Sprint**
- [ ] Follow task order (critical path first)
- [ ] Write tests alongside implementation
- [ ] Commit frequently with meaningful messages
- [ ] Keep notes on blockers or deviations

**Evening (End of Sprint)**
- [ ] Run full build: `mvn clean install`
- [ ] Run all tests: `mvn verify`
- [ ] Review day's work (self code review)
- [ ] Update task status in GitHub Projects
- [ ] Commit final work
- [ ] Sprint retrospective: What worked? What didn't?
- [ ] Plan adjustments for tomorrow

---

