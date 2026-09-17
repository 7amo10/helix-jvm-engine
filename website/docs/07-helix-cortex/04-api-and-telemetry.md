---
id: api-and-telemetry
title: REST API Reference & Real-Time Telemetry
sidebar_position: 4
---

# REST API Reference & Real-Time Telemetry

Helix Cortex exposes a comprehensive Jakarta RESTful Web Services (JAX-RS) API documented via MicroProfile OpenAPI 3.0.

---

## Interactive OpenAPI Documentation

When Helix Cortex is running, the live OpenAPI specification is accessible at:
- **OpenAPI Schema (JSON / YAML)**: `http://localhost:8080/openapi`
- **Application Context**: `http://localhost:8080/helix-cortex/api/v1`

All secured endpoints require the HTTP header:
```http
Authorization: Bearer <jwt-token>
```

---

## Complete API Endpoints Table

| Category | Method | Path | Required Role | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Auth** | `POST` | `/api/v1/auth/login` | Public | Authenticates credentials and returns a signed MicroProfile JWT token. |
| **Auth** | `POST` | `/api/v1/auth/register` | Public | Registers a new user with BCrypt password hashing. |
| **Auth** | `POST` | `/api/v1/auth/refresh` | Authenticated | Exchanges a valid token for a refreshed token. |
| **Rules** | `POST` | `/api/v1/rules/compile` | `ADMIN`, `OPERATOR` | Compiles raw JSON rule syntax into Helix AST and executable bytecode. |
| **Rules** | `POST` | `/api/v1/rules/execute` | `ADMIN`, `OPERATOR` | Evaluates a compiled rule against input context with sub-ms execution. |
| **Rules** | `POST` | `/api/v1/rules/execute/batch` | `ADMIN`, `OPERATOR` | Evaluates a rule against an array of contexts concurrently (Virtual Threads / Pool). |
| **Rules** | `GET` | `/api/v1/rules/sessions` | `ADMIN`, `OPERATOR`, `ANALYST` | Lists all execution sessions (optimized query, single SQL statement). |
| **Rules** | `GET` | `/api/v1/rules/sessions/{id}` | `ADMIN`, `OPERATOR`, `ANALYST` | Retrieves full execution details and metrics for a single session. |
| **Rules** | `DELETE` | `/api/v1/rules/sessions/{id}` | `ADMIN` | Deletes a recorded execution session. |
| **Analysis** | `POST` | `/api/v1/analysis/jar` | `ADMIN`, `OPERATOR` | Uploads a `.jar` archive and inspects bytecode using ASM ClassReader. |
| **Analysis** | `GET` | `/api/v1/analysis/reports` | `ADMIN`, `OPERATOR`, `ANALYST` | Lists summary reports of all historical JAR bytecode inspections. |
| **Analysis** | `GET` | `/api/v1/analysis/reports/{id}` | `ADMIN`, `OPERATOR`, `ANALYST` | Retrieves detailed class and method inspection breakdown for a report. |
| **Telemetry**| `GET` | `/api/v1/telemetry/stream` | `ADMIN`, `OPERATOR`, `ANALYST` | Server-Sent Events (SSE) continuous live telemetry stream (1s interval). |
| **Telemetry**| `GET` | `/api/v1/telemetry/metrics` | `ADMIN`, `OPERATOR`, `ANALYST` | Instantaneous snapshot of engine memory, CPU, and execution metrics. |
| **Telemetry**| `GET` | `/api/v1/telemetry/flamegraph` | `ADMIN`, `OPERATOR`, `ANALYST` | Folded stack traces or SVG/HTML flame graph (`?format=folded\|html\|svg`). |
| **Telemetry**| `GET` | `/api/v1/telemetry/flamegraph/stream` | `ADMIN`, `OPERATOR`, `ANALYST` | Continuous live SSE stream broadcasting updated folded stack traces. |

---

## API Usage Examples

### 1. Authenticate & Obtain JWT

```bash
curl -s -X POST http://localhost:8080/helix-cortex/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "AdminPassword123!"}'
```

**Response (`200 OK`):**
```json
{
  "token": "eyJhbGciOiJSUzI1NiIs...",
  "expiresIn": 3600,
  "tokenType": "Bearer",
  "username": "admin",
  "roles": ["ADMIN"]
}
```

### 2. Compile a Business Rule

```bash
curl -s -X POST http://localhost:8080/helix-cortex/api/v1/rules/compile \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleName": "fraud-velocity-check",
    "description": "Flags transactions with excessive velocity",
    "expression": "context.transactionCount > 10 && context.amount > 5000"
  }'
```

**Response (`200 OK`):**
```json
{
  "ruleId": "fraud-velocity-check",
  "status": "COMPILED",
  "compilationTimeNs": 124500,
  "cacheTier": "L1",
  "message": "Bytecode successfully compiled and linked"
}
```

### 3. Concurrent Batch Evaluation (Virtual Threads)

Execute thousands of contextual items concurrently across lightweight virtual threads:

```bash
curl -s -X POST http://localhost:8080/helix-cortex/api/v1/rules/execute/batch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleId": "fraud-velocity-check",
    "contexts": [
      {"transactionCount": 12, "amount": 6000},
      {"transactionCount": 2, "amount": 100},
      {"transactionCount": 15, "amount": 12000}
    ]
  }'
```

**Response (`200 OK`):**
```json
{
  "ruleId": "fraud-velocity-check",
  "totalEvaluated": 3,
  "durationMs": "0.342",
  "executorType": "VIRTUAL_THREADS",
  "results": [
    {"index": 0, "result": true},
    {"index": 1, "result": false},
    {"index": 2, "result": true}
  ]
}
```

### 4. Fetch Folded Stack Flame Graphs

Retrieve folded stack traces or formatted SVG/HTML for enterprise profiling:

```bash
# Get raw Brendan Gregg folded stack traces
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/helix-cortex/api/v1/telemetry/flamegraph?format=folded&dimension=cpu"

# Get downloadable vector SVG
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/helix-cortex/api/v1/telemetry/flamegraph?format=svg" -o flamegraph.svg
```

### 5. Stream Real-Time Telemetry & Flame Graphs (SSE)

Clients can subscribe to live performance events or live flame graph updates:

```bash
# Telemetry Stream
curl -N -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/helix-cortex/api/v1/telemetry/stream

# Live Flame Graph Stream
curl -N -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/helix-cortex/api/v1/telemetry/flamegraph/stream
```

**Flame Graph Event Stream Output:**
```text
event: flamegraph
data: {"timestamp":1726338420000,"dimension":"CPU","folded":"com.helix.cli.Main;com.helix.core.VirtualThreadRuleExecutor.executeBatch 4200\ncom.helix.cli.Main;com.helix.core.RuleCompiler.compile 890"}
```

---

## Concurrency & Telemetry Configuration Reference

Configure these settings in `microprofile-config.properties` or container environment variables:

| Property | Environment Variable | Default | Description |
|---|---|---|---|
| `helix.cortex.executor.type` | `CORTEX_EXECUTOR_TYPE` | `VIRTUAL_THREADS` | Executor strategy for batch rule evaluation (`VIRTUAL_THREADS` or `PLATFORM_POOL`). |
| `helix.cortex.flamegraph.dimension` | `CORTEX_FLAMEGRAPH_DIMENSION` | `cpu` | Telemetry stack trace profiling dimension (`cpu` or `alloc`). |
