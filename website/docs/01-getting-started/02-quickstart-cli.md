---
id: quickstart-cli
title: Quickstart Guide
sidebar_position: 2
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 5-Minute Quickstart Guide

This guide walks you through compiling your first JSON business rule, executing it against contextual input data, and launching the live terminal dashboard.

---

## 1. Prerequisites

- **Java Development Kit (JDK):** Version 17 or higher (`temurin-17`, `openjdk-17`, or `graalvm-17`).
- **Operating System:** Linux, macOS, or Windows (WSL2).

Verify your environment:
```bash
java -version
```

---

## 2. Compile a Business Rule

Helix rules are authored as standard JSON objects containing a name, version, boolean expression, and typed input schema:

```json title="examples/rules/fraud-detection.json"
{
    "name": "FraudDetectionRule",
    "version": "1.0.0",
    "expression": "amount > 10000 && country != \"US\"",
    "inputSchema": {
        "amount": "int",
        "country": "String"
    }
}
```

Compile the rule into raw JVM bytecode:

<Tabs>
  <TabItem value="json-output" label="JSON Output" default>

```bash
./scripts/start-helix.sh compile --rule examples/rules/fraud-detection.json --output json
```

**Result:**
```json
{
  "status" : "SUCCESS",
  "ruleName" : "FraudDetectionRule",
  "ruleVersion" : "1.0.0",
  "compilationTimeMs" : "177.396"
}
```

  </TabItem>
  <TabItem value="text-output" label="Standard Text Output">

```bash
./scripts/start-helix.sh compile --rule examples/rules/fraud-detection.json
```

  </TabItem>
</Tabs>

---

## 3. Execute Rule with Context Data

Now provide contextual runtime parameters to evaluate the rule:

```json title="examples/rules/sample-context.json"
{
  "amount": 15000,
  "country": "UK"
}
```

Execute asynchronously:

```bash
./scripts/start-helix.sh execute \
  --rule examples/rules/fraud-detection.json \
  --context examples/rules/sample-context.json \
  --mode async \
  --output json
```

**Output:**
```json
{
  "status" : "SUCCESS",
  "ruleName" : "FraudDetectionRule",
  "executionMode" : "ASYNC",
  "resultValue" : true,
  "durationMs" : "1.543"
}
```

---

## 4. Launch Interactive Terminal UI (TUI) Dashboard

To inspect real-time CPU utilization, Metaspace growth, GC pause rates, and L1/L2/L3 cache metrics in your terminal:

```bash
./scripts/start-helix.sh profile --tui
```

:::tip Interactive Controls
Use the arrow keys and button navigation in the TUI to trigger forced GC sweeps, clear cache tiers, or profile active threads in real time.
:::
