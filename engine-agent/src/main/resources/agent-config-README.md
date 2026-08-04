# Helix Java Agent Configuration Guide

The Helix Java Agent (`engine-agent.jar`) provides bytecode transformation, execution profiling, allocation tracking, and JMX MBean control.

## Static Loading (-javaagent)
Add the agent JAR to your JVM launch command:
```bash
java -javaagent:engine-agent-1.0.0-SNAPSHOT.jar=retransform=true,packages=com.helix -jar your-app.jar
```

## Dynamic Loading (Attach API)
You can dynamically attach the agent programmatically:
```java
Instrumentation inst = ByteBuddyAgent.install();
AgentMain.agentmain("retransform=true,packages=com.helix", inst);
```

## Property File Loading
Specify a custom configuration file via `configFile`:
```bash
java -javaagent:engine-agent-1.0.0-SNAPSHOT.jar=configFile=/path/to/agent.properties -jar your-app.jar
```

## Available Properties
| Property | Default | Description |
| --- | --- | --- |
| `retransform` | `true` | Enables retransformation of existing loaded classes |
| `packages` | `com.helix` | Target package prefix for class instrumentation |
| `logLevel` | `INFO` | Logging level (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`) |
| `jmxEnabled` | `true` | Registers `EngineControl` and `ProfilerControl` MBeans |
| `allocationTracking` | `true` | Enables object allocation interceptors |
