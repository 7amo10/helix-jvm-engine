---
id: deployment-and-setup
title: Deployment, Docker Stack & WildFly Setup
sidebar_position: 3
---

# Deployment, Docker Stack & WildFly Setup

Helix Cortex can be deployed using containerized Docker orchestration or deployed directly to an existing WildFly 31 Application Server.

---

## 1. Quickstart with Docker Compose

The simplest way to run Helix Cortex and its dependencies locally is via Docker Compose:

```bash
# Clone the repository
git clone https://github.com/7amo10/helix-cortex.git
cd helix-cortex

# Copy and configure environment variables
cp .env.example .env

# Build and start services in background
docker compose up --build -d
```

### Services Started:
- **`postgres`**: PostgreSQL 16-alpine database on port `5432` with automated healthcheck (`pg_isready`).
- **`helix-cortex`**: WildFly 31.0.0.Final container on port `8080` (HTTP) and `9990` (Management console), waiting for PostgreSQL to be healthy before launch.

Verify container status:
```bash
docker compose ps
docker compose logs -f helix-cortex
```

---

## 2. Multi-Stage Docker Architecture

Helix Cortex utilizes a multi-stage Docker build to ensure lean, secure production images:

```dockerfile
# Stage 1: Build Application WAR
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Production WildFly Runtime
FROM quay.io/wildfly/wildfly:31.0.0.Final-jdk17
USER root
# Install PostgreSQL JDBC Module
COPY docker/module.xml /opt/jboss/wildfly/modules/system/layers/base/org/postgresql/main/module.xml
# Configure Datasource & MicroProfile Subsystems
COPY docker/standalone.xml /opt/jboss/wildfly/standalone/configuration/standalone.xml
# Deploy Built WAR
COPY --from=builder /workspace/target/helix-cortex.war /opt/jboss/wildfly/standalone/deployments/
USER jboss
EXPOSE 8080 9990
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
```

---

## 3. High-Concurrency HikariCP Pool Configuration

To prevent database bottlenecking during high-throughput rule execution workloads, Helix Cortex configures the `CortexPool` HikariCP connection pool with tuned thresholds:

| Property | Value | Rationale |
| :--- | :--- | :--- |
| `maximumPoolSize` | `16` | Sized to match typical database CPU core saturation without thread thrashing. |
| `minimumIdle` | `4` | Ensures ready-to-use connections for sudden bursts of incoming requests. |
| `connectionTimeout` | `3000 ms` | Fails fast (3s) instead of stalling client threads during pool exhaustion. |
| `idleTimeout` | `600000 ms` | Reclaims inactive connections after 10 minutes. |
| `maxLifetime` | `1800000 ms` | Refreshes connections every 30 minutes to avoid stale TCP sockets. |
| `poolName` | `CortexPool` | Explicit naming for JMX and Prometheus thread pool monitoring. |

*This configuration was verified to yield 0 `SQLTimeoutException`s under 25-concurrent-thread synthetic load.*

---

## 4. Environment Variables Reference

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `CORTEX_DB_HOST` | `postgres` | Hostname or IP of the PostgreSQL server. |
| `CORTEX_DB_PORT` | `5432` | Database port. |
| `CORTEX_DB_NAME` | `cortex_db` | PostgreSQL database name. |
| `CORTEX_DB_USER` | `cortex_user` | Database user account. |
| `CORTEX_DB_PASSWORD` | `cortex_pass` | Database user password. |
| `CORTEX_JWT_ISSUER` | `https://helix.pulse.com` | MicroProfile JWT expected issuer URI. |
