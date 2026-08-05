# Architecture Diagrams - JVM Scripting Engine & Profiler

This directory contains PlantUML (`.puml`) architecture diagrams following the C4 model and additional deployment views.

## Diagrams

### C4 Model Diagrams

1. **c4-context.puml** - System Context Diagram
   - Shows the system in its environment
   - External actors: Java Developer, System Operator
   - External systems: JConsole/VisualVM, JDK Mission Control, async-profiler

2. **c4-container.puml** - Container Diagram
   - Shows the high-level technology choices
   - Containers: Engine API, Engine Core, Profiler Module, Java Agent, Experiments Module
   - Data stores: L1/L2/L3 tiered cache

3. **c4-component-core.puml** - Component Diagram (Engine Core)
   - Detailed view of Engine Core internal components
   - Components: Rule Parser, AST Builder, ByteBuddy/ASM Generators, ClassLoader Manager, Executors, Cache Manager

4. **c4-component-profiler.puml** - Component Diagram (Profiler Module)
   - Detailed view of Profiler Module components
   - Components: JIT Monitor, GC Analyzer, async-profiler Integration, JFR Recorder, Live Dashboard

5. **c4-component-agent.puml** - Component Diagram (Java Agent)
   - Detailed view of Java Agent components
   - Components: Agent Main, Transformers, Memory Analyzer, JMX MBeans

### Additional Diagrams

6. **deployment.puml** - Deployment Diagram
   - Shows runtime deployment architecture
   - JVM process structure (Heap, Metaspace, Native Memory)
   - File system artifacts
   - Monitoring tools
   - CI/CD integration

7. **data-flow.puml** - Data Flow / Sequence Diagram
   - Rule compilation and execution flow
   - Cache interaction patterns
   - GC scenarios and cache eviction
   - Agent instrumentation callbacks

## Viewing the Diagrams

### Online Viewers

1. **PlantUML Online Server**
   ```
   http://www.plantuml.com/plantuml/uml/
   ```
   Copy/paste the `.puml` file contents

2. **PlantText**
   ```
   https://www.planttext.com/
   ```

### Local Rendering

**Option 1: PlantUML CLI**
```bash
# Install PlantUML
brew install plantuml  # macOS
apt-get install plantuml  # Ubuntu/Debian

# Generate PNG images
plantuml c4-context.puml
plantuml c4-container.puml
plantuml c4-component-core.puml
plantuml c4-component-profiler.puml
plantuml c4-component-agent.puml
plantuml deployment.puml
plantuml data-flow.puml

# Generate all diagrams
plantuml *.puml
```

**Option 2: VS Code Extension**
```
Install: "PlantUML" by jebbs
Preview: Alt+D (Windows/Linux) or Option+D (macOS)
```

**Option 3: IntelliJ IDEA Plugin**
```
Install: "PlantUML integration" plugin
Right-click .puml file → Diagram → Show Diagram
```

## Diagram Relationships

```
c4-context.puml (Level 1: System Context)
    ↓
c4-container.puml (Level 2: Container)
    ↓
    ├─→ c4-component-core.puml (Level 3: Components of Engine Core)
    ├─→ c4-component-profiler.puml (Level 3: Components of Profiler)
    └─→ c4-component-agent.puml (Level 3: Components of Agent)

deployment.puml (Deployment View)
    ↓
data-flow.puml (Runtime Behavior)
```

## C4 Model Notation

- **Person** (Blue): External users/actors
- **System** (Blue): The system being designed
- **System_Ext** (Gray): External systems
- **Container** (Blue): Deployable units (JARs, services)
- **Component** (Blue): Groupings of code (packages, classes)
- **ContainerDb** (Blue): Data storage containers

## Notes

- All diagrams use the official C4-PlantUML library from GitHub
- Diagrams are version-controlled and should be updated as the architecture evolves
- Generated images (.png, .svg) are .gitignored; regenerate from source
- For presentation purposes, export as SVG for best quality

## References

- [C4 Model](https://c4model.com/)
- [PlantUML](https://plantuml.com/)
- [C4-PlantUML](https://github.com/plantuml-stdlib/C4-PlantUML)

