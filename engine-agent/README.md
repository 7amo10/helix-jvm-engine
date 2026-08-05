# Helix - Engine Agent Module (`engine-agent`)

The `engine-agent` module provides a bytecode-transforming Java Agent (`-javaagent`) and JVM object layout inspector built on OpenJDK JOL (Java Object Layout).

---

## Core Components

### 1. Java Agent (`AgentMain` & `RuleClassTransformer`)
Instruments loaded target classes at JVM startup or runtime attach to inject execution timing, JMX telemetry MBeans, and call-site monitoring hooks.

**JVM Startup Argument:**
```bash
java -javaagent:engine-agent/target/engine-agent-1.0.0-SNAPSHOT.jar -jar your-app.jar
```

### 2. `ObjectLayoutInspector` (JOL Memory Layout)
Inspects low-level JVM object layouts, header size, mark words, class pointers, field padding, and compressed OOPs state:

```java
ObjectLayoutInspector inspector = new ObjectLayoutInspector();
LayoutReport report = inspector.inspect(String.class);

System.out.println("Instance Size: " + report.getInstanceSizeBytes() + " B");
System.out.println("Header Size:   " + report.getHeaderSizeBytes() + " B");
System.out.println("Padding:       " + report.getPaddingSizeBytes() + " B");
```

### 3. JMX Management Beans (`HelixEngineMXBean`)
Exposes live runtime metrics to standard JMX clients (e.g. `jconsole` or `VisualVM`):
- Active ClassLoaders Count
- Total Compiled Rules
- Cache Hit Ratio
