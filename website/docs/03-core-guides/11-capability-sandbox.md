---
id: capability-sandbox
title: AST Capability Sandbox
sidebar_position: 11
---

# AST Capability Sandbox

Modern multi-tenant rule engines evaluate dynamic logic authored not only by internal engineering teams, but increasingly by external enterprise tenants, partner developers, and automated policy pipelines. Executing arbitrary client-supplied code on a shared Java Virtual Machine introduces critical security risks: denial of service, resource exhaustion, sensitive file extraction, unauthorized network connections, reflective inspection of JVM internals, and escape to native host processes.

Historically, JVM environments relied on `java.lang.SecurityManager`. However, SecurityManager was formally deprecated for removal in [JEP 411 (Java 17)](https://openjdk.org/jeps/411) due to substantial performance overhead on hot execution paths, brittle permission models, and continuous maintenance burden. Furthermore, standard class loader isolation only isolates static namespace visibility—it does not prevent untrusted bytecode from invoking dangerous standard library methods (such as `System.exit()` or `Runtime.getRuntime().exec()`).

Helix addresses this challenge by introducing an **AST-Level Capability Sandbox**. Rather than inspecting permissions at runtime with every bytecode instruction, Helix enforces security statically over the Abstract Syntax Tree (AST) before bytecode compilation. This provides strict zero-trust isolation with **0 ns execution-time latency overhead**.

---

## 1. Security Architecture & Threat Model

```mermaid
flowchart TD
    subgraph Submission["Rule Ingestion Boundary"]
        Request["POST /api/v1/rules/compile?sandboxed=true"] --> Controller["RuleSessionControl"]
        Controller --> TrustCheck{"TenantTrustLevel\nEvaluation"}
    end

    subgraph Pipeline["AST Validation & Compilation"]
        TrustCheck -- "TRUSTED" --> BytecodeGen["ByteBuddy / ASM Generator\n(Bypass Sandbox)"]
        TrustCheck -- "UNTRUSTED" --> ASTBuilder["Rule Parser & AST Construction"]
        ASTBuilder --> Enforcer["CapabilityPolicyEnforcer\n(Depth-First AST Walker)"]
        Enforcer --> Policy{"DefaultCapabilityPolicy\nWhitelist Inspection"}
        
        Policy -- "Violation Detected" --> Reject["Throw CapabilityViolationException\n(HTTP 422 Unprocessable Entity)"]
        Policy -- "All Operations Permitted" --> BytecodeGen
        BytecodeGen --> SandboxLoader["SandboxClassLoader\n(Restricted Namespace Boundary)"]
    end

    subgraph Caching["Cache & Runtime Fleet"]
        SandboxLoader --> L1Cache["L1 In-Memory Cache\n(key: rule:version:sandboxed)"]
        SandboxLoader --> L4Cache["L4 Distributed Redis Cache\n(hash: SHA-256 + trust-suffix)"]
        L1Cache --> ExecEngine["Microsecond Execution Path\n(0 ns Security Check Overhead)"]
    end

    style Reject fill:#7f1d1d,stroke:#f87171,color:#fef2f2
    style Policy fill:#1e293b,stroke:#38bdf8,color:#f8fafc
    style ExecEngine fill:#14532d,stroke:#4ade80,color:#f0fdf4
```

### The Multi-Tenant Threat Model

When multi-tenant rules execute in a pooled JVM instance, untrusted scripts must never be capable of:

1. **Host Compromise**: Spawning native OS processes, executing shell commands, or invoking arbitrary binaries via `ProcessBuilder` or JNI.
2. **Data Exfiltration**: Opening raw TCP/UDP sockets, establishing HTTP connections, or reading host files (`/etc/passwd`, cloud instance metadata tokens).
3. **Engine Subversion**: Terminating the JVM via `System.exit()`, deregistering shutdown hooks, or installing malicious security providers.
4. **Introspection & Escape**: Reflectively accessing private fields, bypassing access controls via `sun.misc.Unsafe` or `MethodHandles.Lookup`, or tampering with sibling tenant session data.
5. **Denial of Service (DoS)**: Manipulating thread groups, spawning uncontrolled virtual threads, or altering system clock and timezone settings.

Helix categorizes all rule compilation requests under two trust domains governed by `TenantTrustLevel`:

| Trust Level | Target Author | Security Checks | Cache Key Namespace |
| :--- | :--- | :--- | :--- |
| `TRUSTED` | Internal systems, platform engineers | Standard type checks, syntax validation | `ruleName:version` |
| `UNTRUSTED` | External tenants, third-party partners | Full AST Capability Policy whitelist verification | `ruleName:version:sandboxed` |

Separating cache keys by trust level ensures that a sandboxed rule and a trusted internal rule with identical identifiers can never suffer from cache poisoning across tenant boundaries.

---

## 2. Restricted Operations Taxonomy

The `DefaultCapabilityPolicy` implements a strict default-deny whitelist. Any AST node attempting to access classes, packages, or methods outside the permissible set immediately causes compilation to fail.

```mermaid
classDiagram
    class CapabilityPolicy {
        <<interface>>
        +isPackageAllowed(String packageName) boolean
        +isClassAllowed(String className) boolean
        +isMethodAllowed(String className, String methodName) boolean
    }
    class DefaultCapabilityPolicy {
        -Set~String~ ALLOWED_PACKAGES
        -Set~String~ ALLOWED_CLASSES
        -Set~String~ RESTRICTED_METHODS
        +isPackageAllowed(String) boolean
        +isClassAllowed(String) boolean
        +isMethodAllowed(String, String) boolean
    }
    class CapabilityPolicyEnforcer {
        -CapabilityPolicy policy
        +enforce(Rule rule) void
        -visitNode(AstNode node) void
    }
    class CapabilityViolationException {
        -String violatingClass
        -String violatingMethod
        +getViolatingClass() String
        +getViolatingMethod() String
    }

    CapabilityPolicy <|.. DefaultCapabilityPolicy : implements
    CapabilityPolicyEnforcer --> CapabilityPolicy : validates against
    CapabilityPolicyEnforcer ..> CapabilityViolationException : throws on breach
```

### Classification of Forbidden Operations

The sandbox enforces comprehensive restrictions across six primary vectors:

```
+-------------------------------------------------------------------------+
|                  FORBIDDEN OPERATION TAXONOMY                           |
+-------------------+-----------------------------------------------------+
| Subsystem         | Prohibited Types & Operations                       |
+-------------------+-----------------------------------------------------+
| System Control    | java.lang.System (exit, gc, loadLibrary, getenv)    |
|                   | java.lang.Runtime (exec, halt, addShutdownHook)     |
|                   | java.lang.ProcessBuilder                            |
+-------------------+-----------------------------------------------------+
| Reflection & JNI  | java.lang.reflect.* (Method, Field, Constructor)    |
|                   | java.lang.invoke.* (MethodHandles, VarHandle)       |
|                   | sun.misc.Unsafe, jdk.internal.*                     |
+-------------------+-----------------------------------------------------+
| Filesystem & I/O  | java.io.* (File, FileInputStream, FileOutputStream) |
|                   | java.nio.file.* (Path, Files, FileSystems)          |
+-------------------+-----------------------------------------------------+
| Networking        | java.net.* (Socket, URL, HttpURLConnection)         |
|                   | java.net.http.* (HttpClient)                        |
+-------------------+-----------------------------------------------------+
| Concurrency       | java.lang.Thread (start, stop, interrupt, sleep)    |
|                   | java.lang.ThreadGroup                               |
|                   | java.util.concurrent.* (Executors, ForkJoinPool)    |
+-------------------+-----------------------------------------------------+
| Class Loading     | java.lang.ClassLoader, java.lang.Class.forName      |
+-------------------+-----------------------------------------------------+
```

### The Safe Whitelisted Surface

Untrusted rules are strictly confined to deterministic computational logic:

- **Mathematical Primitives**: Standard arithmetic, comparison, and boolean logic operators.
- **Java Standard Math**: Safe `java.lang.Math` operations (`abs`, `min`, `max`, `sqrt`, `round`, `pow`, `log`, `sin`, `cos`).
- **Context Variables**: Reading named attributes from the incoming `ExecutionContext` (e.g., `transaction.amount`, `user.accountAge`).
- **Safe String Manipulation**: Primitive string operations (`length`, `contains`, `startsWith`, `endsWith`, `toLowerCase`, `trim`).
- **Machine Learning Inference**: Invoking registered machine learning models via the native `ML('model_name')` AST construct (which delegates directly to the sandboxed in-process ONNX Runtime session pool).

---

## 3. Capability Policy Enforcer Mechanics

When an untrusted rule expression is submitted, the compilation pipeline executes the following sequence:

```mermaid
sequenceDiagram
    autonumber
    participant Client as REST Client / Tenant
    participant Resource as RuleResource
    participant Control as RuleSessionControl
    participant Compiler as RuleCompilerService
    participant Enforcer as CapabilityPolicyEnforcer
    participant Policy as DefaultCapabilityPolicy
    participant CodeGen as Bytecode Generator

    Client->>Resource: POST /rules/compile?sandboxed=true
    Resource->>Control: compileAndSave(req, "tenant_user", UNTRUSTED)
    Control->>Compiler: compile(rule, TenantTrustLevel.UNTRUSTED)
    
    Compiler->>Enforcer: enforceCapabilityPolicy(rule)
    Enforcer->>Enforcer: AstBuilder.buildAst(rule.getExpression())
    
    loop For Each Node in AST
        Enforcer->>Policy: isClassAllowed(node.targetClass)
        alt Class Forbidden
            Policy-->>Enforcer: false
            Enforcer-->>Compiler: throw CapabilityViolationException(class, method)
            Compiler-->>Resource: Propagate Exception
            Resource-->>Client: HTTP 422 Unprocessable Entity (CapabilityViolationResponse)
        else Class Permitted
            Enforcer->>Policy: isMethodAllowed(node.targetClass, node.methodName)
            alt Method Forbidden
                Policy-->>Enforcer: false
                Enforcer-->>Compiler: throw CapabilityViolationException(class, method)
                Compiler-->>Resource: Propagate Exception
                Resource-->>Client: HTTP 422 Unprocessable Entity (CapabilityViolationResponse)
            else Method Permitted
                Policy-->>Enforcer: true
            end
        end
    end

    Enforcer-->>Compiler: Validation Success
    Compiler->>CodeGen: Emit JVM Bytecode
    CodeGen-->>Compiler: CompiledRule instance
    Compiler-->>Control: Cache under rule:version:sandboxed
    Control-->>Resource: RuleSession (COMPILED)
    Resource-->>Client: HTTP 201 Created
```

### Isolated SandboxClassLoader

In addition to static AST verification, the compiled class bytes of an untrusted rule are injected into a dedicated `SandboxClassLoader`. This classloader overrides standard parent delegation for restricted packages:

```java
public class SandboxClassLoader extends ClassLoader {
    private final CapabilityPolicy policy;

    public SandboxClassLoader(ClassLoader parent, CapabilityPolicy policy) {
        super(parent);
        this.policy = Objects.requireNonNull(policy, "CapabilityPolicy cannot be null");
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Enforce boundary: prevent dynamic lookup of restricted classes at runtime
        if (!policy.isClassAllowed(name)) {
            throw new SecurityException("Sandbox restricted class resolution: " + name);
        }
        return super.loadClass(name, resolve);
    }
}
```

---

## 4. Enabling Sandboxed Mode via REST API

The Helix Cortex compile endpoint exposes an optional `sandboxed` boolean query parameter:

```http
POST /api/v1/rules/compile?sandboxed=true HTTP/1.1
Host: localhost:8080
Authorization: Bearer <jwt-token>
Content-Type: application/json
```

### Valid Sandboxed Submission (Success)

#### Request Payload
```json
{
  "ruleName": "tenant_volume_discount",
  "ruleVersion": "1.0.0",
  "expression": "orderTotal > 500 && customerType == 'VIP' ? orderTotal * 0.15 : 0.0",
  "inputSchema": {
    "orderTotal": "double",
    "customerType": "string"
  }
}
```

#### Response: `201 Created`
```json
{
  "id": 1042,
  "ruleName": "tenant_volume_discount",
  "ruleVersion": "1.0.0",
  "compiledRuleId": "tenant_volume_discount:1.0.0:sandboxed",
  "status": "COMPILED",
  "engineerId": "external_partner_corp",
  "createdAt": "2026-10-29T10:15:30.125Z"
}
```

### Malicious Sandboxed Submission (Capability Violation)

Consider an untrusted tenant attempting to execute an operating system process:

#### Request Payload
```json
{
  "ruleName": "malicious_escape_attempt",
  "ruleVersion": "1.0.0",
  "expression": "java.lang.System.exit(0) == null",
  "inputSchema": {}
}
```

#### Response: `422 Unprocessable Entity`
The endpoint returns an RFC 7807 problem details response encapsulating structured diagnostic metadata:

```json
{
  "status": 422,
  "title": "Unprocessable Entity",
  "violatingClass": "java.lang.System",
  "violatingMethod": "exit",
  "errorMessage": "Capability violation: Operation 'java.lang.System.exit' is restricted in sandboxed rule execution mode.",
  "detail": "Capability violation: Operation 'java.lang.System.exit' is restricted in sandboxed rule execution mode."
}
```

### cURL Verification Commands

```bash
# 1. Compile a legitimate sandboxed tenant rule
curl -s -X POST "http://localhost:8080/helix-cortex/api/v1/rules/compile?sandboxed=true" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleName": "safe_credit_check",
    "ruleVersion": "1.0.0",
    "expression": "creditScore >= 700 && debtToIncomeRatio < 0.40",
    "inputSchema": {"creditScore": "int", "debtToIncomeRatio": "double"}
  }' | jq .

# 2. Verify rejection of a forbidden runtime command execution
curl -s -X POST "http://localhost:8080/helix-cortex/api/v1/rules/compile?sandboxed=true" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleName": "forbidden_exec",
    "ruleVersion": "1.0.0",
    "expression": "java.lang.Runtime.getRuntime().exec(\"cat /etc/passwd\") != null",
    "inputSchema": {}
  }' | jq .
```

---

## 5. Architectural Roadmap: WebAssembly (WASM) Sandboxing

While static AST inspection delivers optimal performance for JVM-hosted expressions, certain regulated environments require execution of arbitrary multi-language code (e.g., Rust, C++, Go, or Python compiled to WebAssembly bytecode).

```mermaid
flowchart LR
    subgraph Current["Static AST Sandbox (Current Architecture)"]
        ASTNode["AST Expression\n(JSON / DSL)"] --> PolicyCheck["Pure Java Whitelist Enforcer\n(Compile-Time Verification)"]
        PolicyCheck --> JVMBytecode["Native JVM Bytecode\n(ASM / ByteBuddy)"]
        JVMBytecode --> ZeroOverhead["0 ns Execution Overhead\n(Single-Digit Microsecond Latency)"]
    end

    subgraph Future["GraalWasm Sandbox (Future Roadmap)"]
        PolySource["Multi-Language Source\n(Rust, Go, C)"] --> WasmCompiler["LLVM / WASM Compiler"]
        WasmCompiler --> WasmBinary[".wasm Binary Module"]
        WasmBinary --> GraalVM["GraalWasm Engine / Wasmtime"]
        GraalVM --> LinearMemory["Isolated Linear Memory Block\n(Hardware Fault Boundary)"]
    end
```

### Architectural Trade-Off Analysis

| Architectural Dimension | Helix AST Sandbox (Current) | WebAssembly / GraalWasm (Future Roadmap) |
| :--- | :--- | :--- |
| **Execution Overhead** | **0 ns** (runs as native JVM bytecode) | 15% – 35% interpreter / JIT boundary overhead |
| **Cold Start Latency** | **Microseconds** (< 2 ms parsing & compile) | Tens of milliseconds (WASM module compilation) |
| **Language Support** | Helix Rule DSL, Boolean / Arithmetic expressions | Rust, C, C++, Go, AssemblyScript, Zig |
| **Memory Isolation** | Shared JVM heap (constrained by variable context) | Discrete 32-bit linear address space |
| **Use Case Alignment** | High-throughput fintech, fraud detection, SLA < 50 µs | Complex third-party algorithmic plugins, legacy C libraries |

Helix provides static AST capability sandboxing as the standard default for enterprise rule evaluation, with an extensible architecture designed to seamlessly accommodate GraalWasm modules where multi-language binary isolation is mandated by compliance policies.
