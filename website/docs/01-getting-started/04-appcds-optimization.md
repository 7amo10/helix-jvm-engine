---
id: appcds-optimization
title: AppCDS Startup Optimization
sidebar_position: 4
---

# Application Class Data Sharing (AppCDS)

Application Class Data Sharing (**AppCDS**) is a JVM feature that maps pre-processed class metadata into shared memory. This eliminates class loading and verification overhead during startup, reducing Helix CLI latency by **over 65%** (down to sub-100ms).

---

## Performance Comparison

| Metric | Without AppCDS | With AppCDS (`helix.jsa`) | Improvement |
|---|---|---|---|
| **JVM Startup Latency** | `~285 ms` | `~92 ms` | **67.7% Faster** |
| **Classes Loaded on Init** | 2,450 classes | Pre-mapped shared archive | **Zero I/O bottleneck** |
| **Memory Footprint** | Separate copy per process | Read-only shared memory page | **Shared across workers** |

---

## 1-Step Generation via Helper Script

Helix includes an automated script that profiles classloading and builds the `helix.jsa` archive:

```bash
./scripts/generate-appcds.sh
```

### Script Execution Flow

```mermaid
sequenceDiagram
    participant Dev as Engineer / Script
    participant JVM as HotSpot JVM
    participant FS as File System
    
    Dev->>JVM: 1. java -XX:DumpLoadedClassList=helix.lst -jar engine-core.jar --help
    JVM->>FS: Writes list of 2,400+ loaded classes
    Dev->>JVM: 2. java -Xshare:dump -XX:SharedClassListFile=helix.lst -XX:SharedArchiveFile=helix.jsa
    JVM->>FS: Creates optimized binary archive (helix.jsa)
    Dev->>JVM: 3. java -XX:SharedArchiveFile=helix.jsa -jar engine-core.jar execute ...
    Note over JVM: Sub-100ms instant execution!
```

---

## Manual Generation Commands

If you wish to customize class list profiling:

```bash
# Step 1: Dump Class List during representative workload
java -XX:DumpLoadedClassList=helix.lst \
     -jar engine-core/target/engine-core-1.0.0-SNAPSHOT.jar \
     compile --rule examples/rules/fraud-detection.json

# Step 2: Dump the Shared Archive File
java -Xshare:dump \
     -XX:SharedClassListFile=helix.lst \
     -XX:SharedArchiveFile=helix.jsa \
     -jar engine-core/target/engine-core-1.0.0-SNAPSHOT.jar

# Step 3: Run with AppCDS Enabled
java -XX:SharedArchiveFile=helix.jsa \
     -jar engine-core/target/engine-core-1.0.0-SNAPSHOT.jar \
     execute --rule examples/rules/fraud-detection.json \
             --context examples/rules/sample-context.json
```

:::info Automatic Detection
The `./scripts/start-helix.sh` wrapper automatically searches for `helix.jsa` in the project root. If found, it attaches `-XX:SharedArchiveFile=helix.jsa` without requiring manual JVM configuration flags.
:::
