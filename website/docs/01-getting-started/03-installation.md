---
id: installation
title: Installation & Setup
sidebar_position: 3
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Installation & Setup

Helix can be embedded directly into your Java/Kotlin services as a high-performance library, executed as a standalone CLI application, or attached dynamically as a Java Agent.

---

## 1. Embedded Library (Maven / Gradle)

Add `engine-api` and `engine-core` to your build configuration:

<Tabs>
  <TabItem value="maven" label="Maven (pom.xml)" default>

```xml
<dependency>
    <groupId>com.helix</groupId>
    <artifactId>engine-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.helix</groupId>
    <artifactId>engine-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

  </TabItem>
  <TabItem value="gradle" label="Gradle (build.gradle.kts)">

```kotlin
dependencies {
    implementation("com.helix:engine-api:1.0.0-SNAPSHOT")
    implementation("com.helix:engine-core:1.0.0-SNAPSHOT")
}
```

  </TabItem>
</Tabs>

---

## 2. Standalone Binary Distribution ZIP

Download the pre-packaged binary archive from the [GitHub Releases](https://github.com/7amo10/helix-jvm-engine/releases):

```bash
# 1. Download distribution package
wget https://github.com/7amo10/helix-jvm-engine/releases/download/v1.0.0-SNAPSHOT/helix-jvm-engine-1.0.0-SNAPSHOT-bin.zip

# 2. Extract archive
unzip helix-jvm-engine-1.0.0-SNAPSHOT-bin.zip
cd helix-jvm-engine-1.0.0-SNAPSHOT

# 3. Test launcher
./scripts/start-helix.sh --help
```

The distribution directory contains:
- `lib/engine-core-1.0.0-SNAPSHOT.jar`: The executable shaded application JAR.
- `lib/engine-agent-1.0.0-SNAPSHOT.jar`: The isolated JVM profiling agent.
- `scripts/*.sh`: Startup, AppCDS generation, and experiment runners.
- `examples/rules/`: Complete enterprise rule catalog.

---

## 3. Attaching the Java Profiling Agent

To monitor bytecode transformations, inspect memory layout overhead, and record JFR events, attach `engine-agent` at JVM startup:

```bash
java -javaagent:lib/engine-agent-1.0.0-SNAPSHOT.jar \
     -jar lib/engine-core-1.0.0-SNAPSHOT.jar \
     execute --rule examples/rules/fraud-detection.json \
             --context examples/rules/sample-context.json
```
