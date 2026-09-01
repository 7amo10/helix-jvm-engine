#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="$PROJECT_ROOT/website/static/img/diagrams"
mkdir -p "$TARGET_DIR"

echo "[INFO] Compiling D2 diagrams..."

# 1. System Architecture Diagram
cat << 'EOF' > /tmp/system-architecture.d2
direction: down

client: Client Application / CLI {
  shape: person
}

core_engine: Helix Core Engine {
  style.fill: "#1E293B"
  style.stroke: "#00F0FF"
  style.font-color: "#F8FAFC"
  
  compiler: RuleCompiler {
    parser: JSON AST Parser
    optimizer: AST Optimizer
    generator: Bytecode Generator (ASM / ByteBuddy)
  }
  executor: Rule Executors (Sync / Async / Batch)
  classloader: ClassLoader Manager
}

cache_system: Tiered Rule Cache {
  style.fill: "#0F172A"
  style.stroke: "#6366F1"
  style.font-color: "#F8FAFC"

  l1: L1 Cache (Strong Ref / Caffeine)
  l2: L2 Cache (SoftReference)
  l3: L3 Cache (WeakReference)
}

profiler_system: Profiler & Agent {
  style.fill: "#1E1B4B"
  style.stroke: "#EC4899"
  style.font-color: "#F8FAFC"

  jit_mon: JIT Compilation Monitor
  gc_mon: GC & Safepoint Analyzer
  agent: Bytecode Transformer & JOL
}

client -> core_engine.compiler: 1. Submit JSON Rule
core_engine.compiler -> cache_system: 2. Store / Lookup CompiledRule
client -> core_engine.executor: 3. Execute Context
core_engine.executor -> cache_system: 4. Fetch Bytecode
core_engine -> profiler_system: 5. Telemetry & JFR Events
EOF
d2 --theme=200 /tmp/system-architecture.d2 "$TARGET_DIR/system-architecture.svg"

# 2. Tiered Cache Diagram
cat << 'EOF' > /tmp/tiered-cache.d2
direction: down

req: Incoming Evaluation Request {
  shape: oval
}

l1: Tier 1 - Strong Reference Cache (Caffeine) {
  style.fill: "#1E293B"
  style.stroke: "#10B981"
  style.font-color: "#F8FAFC"
  desc: Fast access (< 12 ns), Old Gen, Zero GC eviction
}

l2: Tier 2 - Soft Reference Cache {
  style.fill: "#1E293B"
  style.stroke: "#F59E0B"
  style.font-color: "#F8FAFC"
  desc: Survives minor GC, cleared before OutOfMemory (SoftRefLRUPolicy)
}

l3: Tier 3 - Weak Reference Cache {
  style.fill: "#1E293B"
  style.stroke: "#EF4444"
  style.font-color: "#F8FAFC"
  desc: Reclaimed on next minor GC sweep, transient safety net
}

exec: CompiledRule Instance {
  shape: rectangle
  style.fill: "#6366F1"
  style.font-color: "#FFFFFF"
}

comp: Bytecode Compiler (On Miss) {
  shape: hexagon
}

req -> l1: 1. Lookup Key
l1 -> exec: Hit (< 12 ns)
l1 -> l2: Miss
l2 -> exec: Hit (< 45 ns)
l2 -> l3: Miss
l3 -> exec: Hit (< 80 ns)
l3 -> comp: Miss
comp -> l1: Populate Cache & Return
EOF
d2 --theme=200 /tmp/tiered-cache.d2 "$TARGET_DIR/tiered-cache.svg"

# 3. ClassLoader Hierarchy Diagram
cat << 'EOF' > /tmp/classloader-hierarchy.d2
direction: down

bootstrap: Bootstrap ClassLoader (JVM Runtime) {
  style.fill: "#0F172A"
  style.stroke: "#64748B"
  style.font-color: "#F8FAFC"
}

app_cl: AppClassLoader (Helix Application Classpath) {
  style.fill: "#0F172A"
  style.stroke: "#64748B"
  style.font-color: "#F8FAFC"
}

shared_cl: SharedUtilityClassLoader (Common Helpers & Interfaces) {
  style.fill: "#1E293B"
  style.stroke: "#00F0FF"
  style.font-color: "#F8FAFC"
}

rule_cl_1: RuleClassLoader: Finance / FraudRule_v1 {
  style.fill: "#1E293B"
  style.stroke: "#00F0FF"
  style.font-color: "#F8FAFC"
}

rule_cl_2: RuleClassLoader: Finance / FraudRule_v2 {
  style.fill: "#1E293B"
  style.stroke: "#00F0FF"
  style.font-color: "#F8FAFC"
}

rule_cl_3: RuleClassLoader: Retail / DiscountRule_v1 {
  style.fill: "#1E293B"
  style.stroke: "#00F0FF"
  style.font-color: "#F8FAFC"
}

bootstrap -> app_cl
app_cl -> shared_cl
shared_cl -> rule_cl_1: Dynamic Child
shared_cl -> rule_cl_2: Dynamic Child (Hot Reload)
shared_cl -> rule_cl_3: Dynamic Child
EOF
d2 --theme=200 /tmp/classloader-hierarchy.d2 "$TARGET_DIR/classloader-hierarchy.svg"

# 4. Object Layout (JOL Header & Padding)
cat << 'EOF' > /tmp/object-layout.d2
direction: right

mark: Mark Word (8 Bytes) {
  style.fill: "#334155"
  style.font-color: "#F8FAFC"
  desc: Lock state, GC age, hashcode
}

klass: Klass Pointer (4 Bytes) {
  style.fill: "#0284C7"
  style.font-color: "#F8FAFC"
  desc: -XX:+UseCompressedClassPointers
}

fields: Object Payload Fields (Var Bytes) {
  style.fill: "#4F46E5"
  style.font-color: "#F8FAFC"
  desc: Primitives, 4B compressed OOP refs
}

pad: Alignment Padding (0-7 Bytes) {
  style.fill: "#475569"
  style.font-color: "#F8FAFC"
  desc: 8-byte word boundary alignment
}

mark -> klass -> fields -> pad
EOF
d2 --theme=200 /tmp/object-layout.d2 "$TARGET_DIR/object-layout.svg"

echo "[SUCCESS] D2 diagrams generated in $TARGET_DIR"
