---
id: architecture-integration
title: System Architecture & Integration Flow
sidebar_position: 2
---

# System Architecture & Integration Flow

Helix Cortex connects external systems with the Helix JVM Scripting Engine through a clean, decoupled Jakarta EE 10 architecture.

---

## High-Level Architecture Diagram

The diagram below illustrates the end-to-end integration between external clients, Cortex service layers, the embedded Helix Engine runtime, and persistence/telemetry sinks:

<p align="center">
  <img src="/helix-jvm-engine/img/diagrams/cortex-architecture.svg" alt="Helix Cortex System Architecture" width="850" />
</p>

---

## Step-by-Step Processing Flow

When a client initiates a request against Helix Cortex, the system executes through the following coordinated sequence:

### 1. Authentication & Security Boundary
- The client sends an HTTP request containing an `Authorization: Bearer <jwt-token>` header.
- `JwtSecurityFilter` validates token signature, issuer, expiration, and role claims against MicroProfile JWT standards.
- Unauthorized requests receive an immediate `401 Unauthorized` or `403 Forbidden` response prior to business logic execution.

### 2. Rule Compilation & Dynamic Verification
- When hitting `POST /api/v1/rules/compile`, `RuleResource` delegates the raw JSON payload to `RuleCompilerService`.
- `RuleCompilerService` translates the JSON schema into Helix Abstract Syntax Tree (AST) representations.
- Helix Engine evaluates the AST, checks the tiered cache (L1 fast lookup -> L2 Caffeine -> L3 persistent), and invokes ByteBuddy/ASM generators if compilation is required.
- The compiled bytecode class is loaded into an isolated child classloader to ensure strict tenant and memory isolation.

### 3. Execution & Context Evaluation (Single & Batch Fan-Out)
- **Single Evaluation:** When executing via `POST /api/v1/rules/execute`, `RuleExecutionService` evaluates the compiled rule against a provided JSON context (e.g., transaction amount, user credit history, location) in sub-microsecond time on the JVM stack.
- **Batch Fan-Out:** When executing via `POST /api/v1/rules/execute/batch`, `RuleExecutionService` leverages the configured executor (`helix.cortex.executor.type=VIRTUAL_THREADS` or `PLATFORM_POOL`) to fan out rule evaluation across lightweight virtual threads or an optimized platform thread pool, assembling array results concurrently with structured error handling.
- The result, execution duration in nanoseconds, and pass/fail criteria are assembled into a response DTO.

### 4. Optimized Persistence & Auditing
- `RuleSessionRepository` persists the session metadata and execution metrics into PostgreSQL 16.
- To prevent N+1 query overhead in high-throughput listing endpoints (`GET /api/v1/rules/sessions`), queries utilize JPQL `LEFT JOIN FETCH s.metrics` and JPA EntityGraph hints to fetch sessions and their associated metrics in a single database round-trip.
- HikariCP manages connection pooling with tuned timeouts (16 max connections, 3-second connection timeout), guaranteeing zero connection exhaustion under concurrent spikes.

### 5. Continuous Telemetry & Flame Graph Streaming
- Throughout engine execution, the background `TelemetryControl` service samples JVM and engine metrics (CPU utilization, heap memory usage, active rule sessions, cache hit ratios).
- Every 1 second, the `SseBroadcaster` streams an SSE event to all connected dashboard consumers via `GET /api/v1/telemetry/stream`.
- **Flame Graph Telemetry:** Real-time call stack samples are aggregated in-memory via `FlameGraphAggregator` and exposed via REST (`GET /api/v1/telemetry/flamegraph?format=folded|html|svg`) and live SSE streaming (`GET /api/v1/telemetry/flamegraph/stream`), allowing enterprise observability dashboards to visualize CPU hotspots and allocation spikes live.

### 6. ONNX Model Registry & Dynamic Cluster Activation
- Data scientists upload pre-trained ONNX models via `POST /api/v1/models`, storing metadata in PostgreSQL and raw `.onnx` binaries in persistent volume storage (`/opt/helix/models`).
- When a model version is activated via `PUT /api/v1/models/{name}/activate`, `ModelRegistryService` updates database flags and triggers `ModelActivationBroadcaster` to publish an invalidation payload to Redis topic `helix:models:activate`.
- All clustered WildFly nodes receive the Redis broadcast, synchronize their in-memory `LocalModelRegistry`, and hot-swap their `OnnxSessionPool` instances with zero downtime.
- When compiling rules that invoke `ML(model_name)`, `RuleCompilerService` dynamically resolves the active model version and enriches the compilation context schema with expected feature vectors.

---

## Related Documentation & Cross-Links

- [REST API Reference & Real-Time Telemetry](api-and-telemetry) - Comprehensive API reference including the [Model Registry Endpoints](api-and-telemetry#machine-learning-model-registry-rest-api-apiv1models).
- [Deployment, Docker Stack & WildFly Setup](deployment-and-setup) - Clustered deployment topology with PostgreSQL, Redis, and persistent volume storage.
- [ONNX Model Inference Guide](../core-guides/onnx-model-inference) - Rule expression `ML()` grammar reference and in-process ONNX Runtime integration.
- [ML Inference & Adaptive Optimization Architecture](../architecture-and-internals/ml-inference-and-adaptive-optimization) - Deep-dive into model execution and runtime AST clause reordering.
