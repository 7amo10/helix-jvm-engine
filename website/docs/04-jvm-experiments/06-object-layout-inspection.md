---
id: object-layout-inspection
title: Object Layout & JOL Analysis
sidebar_position: 6
---

import ObjectLayoutSvg from '@site/static/img/diagrams/object-layout.svg';

# Object Layout & JOL Memory Inspection

Using the **Java Object Layout (JOL)** tool, Helix inspects the exact physical memory structure and 8-byte word alignment of runtime objects under 64-bit JVM Compressed OOPs (`-XX:+UseCompressedOops`).

---

## 64-Bit Object Header Structure

Every Java object in memory has an object header followed by its instance payload fields and alignment padding:

<div style={{textAlign: 'center', margin: '2rem 0'}}>
  <ObjectLayoutSvg style={{maxWidth: '100%', height: 'auto', borderRadius: '8px'}} />
</div>

---

## Helix Object Footprint Analysis

| Class Symbol | Shallow Size | Retained Deep Size | Field & Padding Layout Breakdown |
|---|---|---|---|
| `ExecutionResult` | **32 Bytes** | **64 Bytes** | 12B Header + 1B `success` + 4B `result` + 4B `error` + 8B `nanos` + 3B padding |
| `ExecutionContext` | **24 Bytes** | **256 Bytes** | 12B Header + 4B `variables` Map reference + 8B padding |
| `CacheKey` | **32 Bytes** | **184 Bytes** | 12B Header + 4B `name` + 4B `version` + 4B `schema` + 8B hashcode |
| `RuleClassLoader` | **112 Bytes** | **12.4 KB** | 12B Header + ClassLoader native vector structures + loaded class references |
| `CompiledRuleClass` | **640 Bytes** (ASM) | **2.4 KB** (Metaspace) | Bytecode instructions array + constant pool + class metadata |

---

## Running Layout Inspection

```bash
./scripts/run-experiment.sh layout
```

```text
[INFO] Analyzed object layout for 'com.helix.api.ExecutionResult':
       - Object Header:  12 Bytes (8B Mark Word + 4B Klass Pointer)
       - Instance Data:  17 Bytes
       - Word Alignment: 3 Bytes Padding
       - Shallow Size:   32 Bytes
```
