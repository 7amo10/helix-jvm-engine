---
id: java-agent-and-jmx
title: Java Agent & JMX Telemetry
sidebar_position: 4
---

# Java Agent & JMX Telemetry

The `engine-agent` module provides low-overhead JVM-level instrumentation, JMX MBean interfaces, and JDK Flight Recorder (JFR) telemetry.

---

## Attaching the Java Agent

Add `-javaagent` before your application JAR:

```bash
java -javaagent:engine-agent/target/engine-agent-1.0.0-SNAPSHOT.jar \
     -jar your-application.jar
```

### Agent Responsibilities
1. **ASM Bytecode Transformation:** Intercepts `CompiledRule` class loading to inject execution timers and exception counters.
2. **JOL Object Inspector:** Measures deep object graph sizes directly from native memory.
3. **JMX MBean Registration:** Publishes engine health metrics to `com.helix:type=HelixEngine`.

---

## JMX Monitoring via JConsole / VisualVM

Connect to the JVM and navigate to MBean `com.helix:type=HelixEngine`:

| Attribute Name | Type | Description |
|---|---|---|
| `TotalCompiledRules` | `long` | Cumulative count of rules compiled since JVM startup |
| `L1CacheHitRatio` | `double` | Percentage of evaluations satisfied by Tier 1 Caffeine cache |
| `ActiveClassLoaders` | `int` | Current count of dynamic `RuleClassLoader` instances |
| `MetaspaceUsedBytes` | `long` | Current native memory consumed by dynamic rule classes |

---

## JDK Flight Recorder (JFR) Manager

`JfrRecordingManager` enables programmatic flight recording triggers:

```java
import com.helix.profiler.jfr.JfrRecordingManager;

JfrRecordingManager jfr = new JfrRecordingManager();
Path recordingFile = jfr.startRecording("HelixProfiling", 60); // 60 seconds
```
The output `.jfr` file can be opened directly in **JDK Mission Control (JMC)** for flamegraph and thread lock analysis.
