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
| **Rules** | `GET` | `/api/v1/rules/sessions` | `ADMIN`, `OPERATOR`, `ANALYST` | Lists all execution sessions (optimized query, single SQL statement). |
| **Rules** | `GET` | `/api/v1/rules/sessions/{id}` | `ADMIN`, `OPERATOR`, `ANALYST` | Retrieves full execution details and metrics for a single session. |
| **Rules** | `DELETE` | `/api/v1/rules/sessions/{id}` | `ADMIN` | Deletes a recorded execution session. |
| **Analysis** | `POST` | `/api/v1/analysis/jar` | `ADMIN`, `OPERATOR` | Uploads a `.jar` archive and inspects bytecode using ASM ClassReader. |
| **Analysis** | `GET` | `/api/v1/analysis/reports` | `ADMIN`, `OPERATOR`, `ANALYST` | Lists summary reports of all historical JAR bytecode inspections. |
| **Analysis** | `GET` | `/api/v1/analysis/reports/{id}` | `ADMIN`, `OPERATOR`, `ANALYST` | Retrieves detailed class and method inspection breakdown for a report. |
| **Telemetry**| `GET` | `/api/v1/telemetry/stream` | `ADMIN`, `OPERATOR`, `ANALYST` | Server-Sent Events (SSE) continuous live telemetry stream (1s interval). |
| **Telemetry**| `GET` | `/api/v1/telemetry/metrics` | `ADMIN`, `OPERATOR`, `ANALYST` | Instantaneous snapshot of engine memory, CPU, and execution metrics. |

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

### 3. Stream Real-Time Telemetry (SSE)

Clients can subscribe to live performance events using any SSE-compatible client or `curl`:

```bash
curl -N -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/helix-cortex/api/v1/telemetry/stream
```

**Event Stream Output (Broadcast every 1,000 ms):**
```text
event: telemetry
data: {"timestamp":1726338420000,"cpuUsage":12.4,"heapUsedMb":184.2,"heapMaxMb":1024.0,"activeSessions":3,"cacheHits":1420,"cacheMisses":12}

event: telemetry
data: {"timestamp":1726338421000,"cpuUsage":11.8,"heapUsedMb":185.1,"heapMaxMb":1024.0,"activeSessions":2,"cacheHits":1445,"cacheMisses":12}
```
