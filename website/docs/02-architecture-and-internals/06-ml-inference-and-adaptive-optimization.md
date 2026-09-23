---
id: ml-inference-and-adaptive-optimization
title: ML Inference & Adaptive Optimization Architecture
sidebar_position: 6
---

# ML Inference & Adaptive Optimization Architecture

This document describes the complete architecture of the two embedded ONNX models that power
Phase 3 of the Helix engine: inline production fraud detection via `fraud_model_v1.onnx` and
neural-guided AST reordering via `ast_reorder_policy.onnx`. Both models operate entirely within
the JVM process heap with no external network calls, remote endpoints, or serialization overhead.

---

## 1. Two-Model System Overview

Helix embeds two ONNX models serving fundamentally different roles in the execution pipeline.
One model produces business-level fraud decisions. The other optimizes the engine's own execution
strategy at runtime.

```mermaid
flowchart TD
    subgraph BusinessLayer["Business Decision Layer"]
        Rule["Rule Expression\namount > 1000 && ML('fraud_model_v1') > 0.85"] --> FraudModel["fraud_model_v1.onnx\nGradient Boosted Classifier\n17 transaction features -> fraud probability"]
        FraudModel --> Score["float fraud_score in [0.0, 1.0]"]
        Score --> Predicate["Guard predicate: score > 0.85"]
        Predicate --> Decision["ALLOW / BLOCK transaction"]
    end

    subgraph EngineLayer["Engine Self-Optimization Layer"]
        Profiler["AstNodeProfiler\n(live cost + failure stats per clause)"] --> PolicyModel["ast_reorder_policy.onnx\nRL Policy Network\n82-dim observation -> action logits"]
        PolicyModel --> Permutation["Optimal clause evaluation order"]
        Permutation --> HotSwap["Zero-downtime bytecode hot-swap\ninto TieredRuleCache"]
        HotSwap --> FasterEval["Faster rule evaluation\n(93% latency reduction in production workloads)"]
    end

    FraudModel -.->|"Same OnnxSessionPool\nand LocalModelRegistry"| PolicyModel
```

---

## 2. Model 1: `fraud_model_v1.onnx` - Production Fraud Classifier

### 2.1 Purpose and Use Case

`fraud_model_v1` is a production-grade binary transaction classifier trained on 17 behavioral and
contextual features extracted from payment transaction metadata. Its output — a scalar fraud
probability in `[0.0, 1.0]` — participates as a first-class operand in Helix boolean rule
expressions, enabling data scientists to deploy ML-powered fraud guards without writing any Java.

**Representative use cases:**
- High-value payment transaction screening (`amount > 1000 && ML('fraud_model_v1') > 0.85`)
- Multi-signal risk scoring combining device, velocity, and geographic features
- Chargeback prevention by combining historical signals with real-time inference

### 2.2 Input Feature Schema

The model consumes exactly 17 float features extracted in this fixed order from the `ExecutionContext`:

| Index | Feature Name | Type | Description |
|---|---|---|---|
| 0 | `amount` | `float` | Transaction amount in base currency units |
| 1 | `hour_of_day` | `float` | Hour of transaction (0-23, UTC) |
| 2 | `day_of_week` | `float` | Day of week (0=Mon, 6=Sun) |
| 3 | `merchant_category` | `float` | Merchant category code (encoded integer) |
| 4 | `transaction_currency` | `float` | ISO currency code (encoded integer) |
| 5 | `velocity_1h` | `float` | Transaction count in last 1 hour |
| 6 | `velocity_24h` | `float` | Transaction count in last 24 hours |
| 7 | `velocity_7d` | `float` | Transaction count in last 7 days |
| 8 | `amount_deviation_30d` | `float` | Z-score deviation from 30-day mean amount |
| 9 | `unique_merchants_24h` | `float` | Distinct merchant count in last 24 hours |
| 10 | `is_new_device` | `float` | 1.0 if device seen for first time, else 0.0 |
| 11 | `device_risk_score` | `float` | Device fingerprint risk score (0.0-1.0) |
| 12 | `is_vpn_or_proxy` | `float` | 1.0 if VPN or proxy IP detected, else 0.0 |
| 13 | `country_mismatch` | `float` | 1.0 if IP country differs from billing country |
| 14 | `account_age_days` | `float` | Days since account creation |
| 15 | `is_account_suspended` | `float` | 1.0 if account has active suspension flag |
| 16 | `previous_chargeback` | `float` | 1.0 if account has prior chargeback history |

### 2.3 Model Architecture and Inference Path

```mermaid
flowchart LR
    subgraph InputPrep["Feature Preparation (OnnxFeatureExtractor)"]
        Context["ExecutionContext\nMap<String, Object>"] --> Align["Feature order alignment\n(OnnxModelDescriptor.inputFeatures)"]
        Align --> Coerce["Type coercion\ndouble/int/bool -> float"]
        Coerce --> FVec["float[17] feature vector"]
        FVec --> Tensor["OnnxTensor shape [1, 17]\n(single-row batch)"]
    end

    subgraph ModelInference["ONNX Runtime Inference (native JNI)"]
        Tensor --> Input["input: float_input [1, 17]"]
        Input --> GBM["Gradient Boosting\nor MLP layers"]
        GBM --> Softmax["Softmax activation"]
        Softmax --> Output["output: probabilities [1, 2]\n[p_benign, p_fraud]"]
    end

    subgraph OutputExtraction["Result Extraction"]
        Output --> Extract["outputIndex=1\n(positive class: fraud)"]
        Extract --> Score["float fraud_probability\ne.g. 0.9237"]
        Score --> Compare["Rule predicate\nscore > 0.85"]
        Compare --> Boolean["boolean: true (BLOCK)"]
    end
```

### 2.4 ONNX Graph Topology

```mermaid
graph TD
    FloatInput["float_input\nshape: [batch, 17]"]

    subgraph GBMLayers["Gradient Boosting Ensemble"]
        Tree1["Decision Tree 1\n(estimator 0)"]
        Tree2["Decision Tree 2\n(estimator 1)"]
        TreeN["... Decision Tree N\n(estimator N-1)"]
    end

    subgraph Aggregation["Score Aggregation"]
        Sum["Weighted score sum\n(additive boosting)"]
        Sigmoid["Sigmoid / Softmax\nnormalization"]
    end

    Output["probabilities\nshape: [batch, 2]\n[p_benign, p_fraud]"]

    FloatInput --> Tree1
    FloatInput --> Tree2
    FloatInput --> TreeN
    Tree1 --> Sum
    Tree2 --> Sum
    TreeN --> Sum
    Sum --> Sigmoid
    Sigmoid --> Output
```

### 2.5 Session Pool and Concurrency Model

```mermaid
sequenceDiagram
    participant VT1 as "Virtual Thread 1"
    participant VT2 as "Virtual Thread 2"
    participant Pool as "OnnxSessionPool"
    participant Deque as "LinkedBlockingDeque<OrtSession>"
    participant ORT as "ONNX Runtime (C++ JNI)"

    VT1->>Pool: run(ctx1, "fraud_model_v1")
    VT2->>Pool: run(ctx2, "fraud_model_v1")

    Pool->>Deque: pollFirst() -> Session A
    Pool->>Deque: pollFirst() -> Session B

    VT1->>ORT: Session A: run(tensor1)
    VT2->>ORT: Session B: run(tensor2)

    ORT-->>VT1: probabilities1 [0.08, 0.92]
    ORT-->>VT2: probabilities2 [0.71, 0.29]

    VT1->>Deque: addFirst(Session A)
    VT2->>Deque: addFirst(Session B)

    VT1-->>Pool: 0.92 (fraud)
    VT2-->>Pool: 0.29 (benign)
```

**Key properties:**
- Each `OrtSession` is native C++ state allocated via JNI. Sharing sessions across threads is unsafe.
- `LinkedBlockingDeque` provides O(1) lock-free lease/return under normal (non-contended) conditions.
- Under contention, threads block up to 500 ms for a session before throwing `IllegalStateException`.
- Session count defaults to `max(4, availableProcessors())` and scales linearly with virtual thread count.

---

## 3. Model 2: `ast_reorder_policy.onnx` - Neural AST Reordering Policy

### 3.1 Purpose and Use Case

`ast_reorder_policy` is a Reinforcement Learning (RL) policy network trained to optimize the
evaluation order of AST clauses in boolean AND chains. It learns from observed cost and failure
statistics to produce clause permutations that minimize expected total evaluation cost for a
given workload distribution.

Unlike the analytical `RatioSortPolicy` (which sorts by `C_i / F_i` independently per clause),
the neural policy encodes the joint interaction between all candidate clauses in a single
forward pass and can model non-linear interdependencies between clause ordering decisions.

**Representative use cases:**
- Rules with many interacting AND clauses where pair-wise ratio sort is suboptimal
- Non-stationary traffic distributions where the optimal ordering shifts over time
- Multi-tenant environments where different rule sets share a pool and benefit from learned routing

### 3.2 Observation Vector Schema

The policy network receives an 82-dimensional float observation vector encoding the statistics
of up to 20 candidate clauses. If a rule has fewer than 20 clauses, the remaining slots are
zero-padded.

```mermaid
flowchart LR
    subgraph NodeStats["Per-Clause Statistics (n clauses, up to 20)"]
        S0["NodeStats[0]\nexecCount, failCount\navgCostNanos, failRate\ncostToFailRatio, isMLNode"]
        S1["NodeStats[1]\n..."]
        SN["NodeStats[n-1]\n..."]
    end

    subgraph Encoding["Observation Encoder (82 dims)"]
        direction TB
        Norm["Normalize each stat\n(log scale for costs,\nclip ratios to [0, 1e6])"]
        Flatten["Flatten to float[82]\n[stats_0 | stats_1 | ... | padding]"]
        Norm --> Flatten
    end

    subgraph Tensor["Input Tensor"]
        T["observation\nshape: [1, 82]"]
    end

    S0 --> Norm
    S1 --> Norm
    SN --> Norm
    Flatten --> T
```

The 82 dimensions break down as: `20 candidate slots x 4 features per slot + 2 global context features`.

Each per-clause slot contains:
- `normalized_cost`: log-scaled average cost in nanoseconds
- `failure_rate`: empirical failure rate in `[0.0, 1.0]`
- `ratio`: normalized cost-to-failure ratio
- `is_ml_node`: 1.0 if clause is an `OnnxInferenceNode`, else 0.0

### 3.3 Network Architecture and Inference Path

```mermaid
flowchart TD
    subgraph InputLayer["Input"]
        Obs["observation tensor\nshape: [1, 82]"]
    end

    subgraph PolicyNetwork["RL Policy Network (MLP)"]
        FC1["Linear(82 -> 256)\n+ ReLU"]
        FC2["Linear(256 -> 128)\n+ ReLU"]
        FC3["Linear(128 -> 20)\n(action logits per candidate slot)"]
        FC1 --> FC2
        FC2 --> FC3
    end

    subgraph OutputLayer["Output"]
        Logits["action_logits tensor\nshape: [1, 20]"]
        Mask["Action masking\n(zero-out unused slots for n < 20)"]
        ArgSort["Argsort logits descending\n-> permutation order"]
        Logits --> Mask
        Mask --> ArgSort
    end

    subgraph Application["AST Reordering"]
        Perm["List<Integer> permutation\ne.g. [2, 0, 1] for 3 clauses"]
        Reorder["AND chain reordered\nclause[2] && clause[0] && clause[1]"]
        Perm --> Reorder
    end

    Obs --> FC1
    ArgSort --> Perm
```

### 3.4 Complete Neural Reordering Pipeline

```mermaid
sequenceDiagram
    participant Sched as "Background Optimization Scheduler"
    participant Profiler as "AstNodeProfiler"
    participant Policy as "NeuralAstReorderingPolicy"
    participant Pool as "OnnxSessionPool"
    participant ORT as "ONNX Runtime"
    participant Gen as "AsmBytecodeGenerator"
    participant Cache as "TieredRuleCache"

    Sched->>Profiler: getAllNodeStats()
    Profiler-->>Sched: Map<nodeId, NodeStats>

    Sched->>Policy: determineOrder(nodeStatsList)
    Policy->>Policy: encode 82-dim observation vector
    Policy->>Pool: run(obsContext, "ast_reorder_policy")
    Pool->>ORT: session.run(obsTensor)
    ORT-->>Pool: action_logits [1, 20]
    Pool-->>Policy: float[] logits

    Policy->>Policy: action masking + argsort
    Policy-->>Sched: List<Integer> [2, 0, 1]

    Sched->>Gen: generateBytecode(rule, reorderedAst)
    Gen-->>Sched: byte[] optimizedBytecode + CompiledRule

    Sched->>Cache: hotSwap(cacheKey, compiledRule, bytecode)
    Cache->>Cache: atomic ConcurrentHashMap.put
    Cache-->>Sched: swap complete (zero downtime)
```

### 3.5 Fallback to RatioSortPolicy

The neural policy degrades gracefully under all failure conditions:

```mermaid
flowchart TD
    Start["NeuralAstReorderingPolicy.determineOrder(nodes)"]

    C1{n == 0?}
    C2{n == 1?}
    C3{"n > MAX_CANDIDATE_NODES (20)?"}
    C4{"OnnxSessionPool available?"}
    C5{"ONNX inference succeeded?"}

    Fallback["RatioSortPolicy.determineOrder(nodes)\n(analytical C_i/F_i sort)"]
    Neural["Neural policy:\nobservation encode -> ORT -> argsort logits"]
    Return["return List<Integer> permutation"]

    Start --> C1
    C1 -->|yes| Return
    C1 -->|no| C2
    C2 -->|yes| Return
    C2 -->|no| C3
    C3 -->|yes| Fallback
    C3 -->|no| C4
    C4 -->|no| Fallback
    C4 -->|yes| C5
    C5 -->|exception| Fallback
    C5 -->|yes| Neural
    Fallback --> Return
    Neural --> Return
```

---

## 4. Shared Infrastructure: OnnxSessionPool as a Multi-Model Broker

Both models are served through the same `OnnxSessionPool` instance with per-model independent
session pools backed by `ConcurrentHashMap<modelName, LinkedBlockingDeque<OrtSession>>`.

```mermaid
flowchart TD
    subgraph SharedInfrastructure["Shared ML Infrastructure"]
        Registry["LocalModelRegistry\n(OnnxModelDescriptor per model+version)"]

        subgraph SessionPools["OnnxSessionPool (shared singleton)"]
            Pool1["ModelPool: fraud_model_v1\nLinkedBlockingDeque<OrtSession> x 8"]
            Pool2["ModelPool: ast_reorder_policy\nLinkedBlockingDeque<OrtSession> x 4"]
        end

        Extractor["OnnxFeatureExtractor\n(context -> float[] tensor)"]
        OrtEnv["OrtEnvironment\n(native singleton, thread-safe)"]

        Registry --> Pool1
        Registry --> Pool2
        Extractor --> Pool1
        Extractor --> Pool2
        OrtEnv --> Pool1
        OrtEnv --> Pool2
    end

    subgraph Callers["Callers"]
        AsmBytecode["ASM CompiledRule.eval()\n(ML() expression evaluation)"]
        NeuralPolicy["NeuralAstReorderingPolicy\n(clause reordering)"]
    end

    AsmBytecode -->|"OnnxSessionPool.run(ctx, 'fraud_model_v1')"| Pool1
    NeuralPolicy -->|"OnnxSessionPool.run(obsCtx, 'ast_reorder_policy')"| Pool2
```

### Pool Isolation Properties

| Property | `fraud_model_v1` Pool | `ast_reorder_policy` Pool |
|---|---|---|
| Caller | ASM-generated `CompiledRule.eval()` (hot path) | `NeuralAstReorderingPolicy` (background scheduler) |
| Session count default | `max(4, CPU count)` | `max(4, CPU count)` (separate pool) |
| Call frequency | Per rule evaluation request (very high throughput) | Per optimization cycle (periodic, low frequency) |
| Input shape | `[1, 17]` float | `[1, 82]` float |
| Output shape | `[1, 2]` float (class probabilities) | `[1, 20]` float (action logits) |
| Output extraction | `outputIndex=1` (fraud probability) | `outputIndex=0` (full logit array) |

---

## 5. End-to-End Request Lifecycle: Fraud Detection with Adaptive Optimization

This diagram traces a single payment transaction through the complete dual-model pipeline
from inbound request to final ALLOW/BLOCK decision, showing how both models interact.

```mermaid
sequenceDiagram
    participant Client as "Payment Service"
    participant Engine as "Helix RuleEngine"
    participant Cache as "TieredRuleCache"
    participant Rule as "CompiledRule (optimized)"
    participant FraudPool as "fraud_model_v1 Pool"
    participant ORT1 as "ONNX Runtime (fraud)"
    participant Profiler as "AstNodeProfiler"
    participant Optimizer as "AdaptiveAstOptimizer (background)"
    participant PolicyPool as "ast_reorder_policy Pool"
    participant ORT2 as "ONNX Runtime (policy)"

    Note over Optimizer: Background thread (every 30s)

    Client->>Engine: evaluate(rule, context)
    Engine->>Cache: get(CacheKey)
    Cache-->>Engine: CompiledRule (optimized order)

    Engine->>Rule: execute(context)

    Note over Rule: Optimized order: is_vpn_or_proxy first
    Rule->>Rule: is_vpn_or_proxy == 1.0? -> true (continue)
    Rule->>Rule: amount > 1000? -> true (continue)
    Rule->>FraudPool: run(context, "fraud_model_v1")
    FraudPool->>ORT1: OrtSession.run([1,17] tensor)
    ORT1-->>FraudPool: probabilities [0.08, 0.923]
    FraudPool-->>Rule: 0.923 (fraud score)
    Rule->>Rule: 0.923 > 0.85 -> true

    Rule->>Profiler: record("clause_ml", 18500ns, failed=false)
    Rule->>Profiler: record("clause_vpn", 82ns, failed=false)
    Rule->>Profiler: record("clause_amount", 115ns, failed=false)

    Rule-->>Engine: ExecutionResult(true, "BLOCK")
    Engine-->>Client: BLOCK transaction

    Note over Optimizer: Periodic reorder cycle
    Optimizer->>Profiler: getAllNodeStats()
    Profiler-->>Optimizer: NodeStats per clause
    Optimizer->>PolicyPool: run(obsCtx, "ast_reorder_policy")
    PolicyPool->>ORT2: OrtSession.run([1,82] tensor)
    ORT2-->>PolicyPool: action_logits [...]
    PolicyPool-->>Optimizer: float[] logits
    Optimizer->>Optimizer: decode permutation [vpn, amount, ML]
    Optimizer->>Cache: hotSwap(key, recompiledRule, bytecode)
    Note over Cache: Next request uses optimized order
```

---

## 6. Compilation Pipeline Integration: ML() in the AST

This diagram shows how `ML('fraud_model_v1')` flows through the full compilation pipeline
from expression parsing to native JVM bytecode emission.

```mermaid
flowchart TD
    Expr["Expression:\namount > 1000 && is_vpn_or_proxy == 1.0 && ML('fraud_model_v1') > 0.85"]

    subgraph Parsing["Stage 1: Parsing"]
        Lexer["ExpressionRuleParser\n(recursive descent)"]
        AST["AST Root: BinaryOpNode(AND)\n  BinaryOpNode(AND)\n    BinaryOpNode(GT): amount > 1000\n    BinaryOpNode(EQ): is_vpn == 1.0\n  BinaryOpNode(GT)\n    OnnxInferenceNode('fraud_model_v1')\n    LiteralNode(0.85)"]
    end

    subgraph TypeCheck["Stage 2: Type Checking"]
        MLCheck["MlTypeChecker.check()\nverify 'fraud_model_v1' registered in ModelRegistry\nverify output is numeric (comparable with double)"]
    end

    subgraph StaticOpt["Stage 3: Static AST Optimization\n(BytecodeOptimizer)"]
        Fold["Constant folding + dead branch elimination\n(no-op for this expression - all dynamic)"]
    end

    subgraph AdaptOpt["Stage 4: Adaptive AST Reordering\n(AdaptiveAstOptimizer - runtime, background)"]
        Reorder["Clause reorder by C_i/F_i:\n1. is_vpn_or_proxy == 1.0  (ratio: 89)\n2. amount > 1000            (ratio: 343)\n3. ML('fraud_model_v1')     (ratio: 120,000)"]
    end

    subgraph Codegen["Stage 5: ASM Bytecode Generation"]
        ASMGen["AsmBytecodeGenerator\nemits INVOKESTATIC to OnnxSessionPool.run\nfor OnnxInferenceNode"]
        Bytecode["JVM bytecode:\nGETFIELD ctx\nINVOKEVIRTUAL ExecutionContext.getVariable('is_vpn_or_proxy')\nDCMPL + IFEQ short_circuit\n...\nINVOKESTATIC OnnxSessionPool.run(ctx, 'fraud_model_v1')\nLDC 0.85\nDCMPL\nIFLE short_circuit\nICONST_1\nIRETURN"]
    end

    subgraph Cache["Stage 6: Cache Storage"]
        Store["TieredRuleCache.put(CacheKey, CompiledRule)\nL1 Strong -> L2 Soft -> L3 Weak"]
    end

    Expr --> Lexer
    Lexer --> AST
    AST --> MLCheck
    MLCheck --> Fold
    Fold --> Reorder
    Reorder --> ASMGen
    ASMGen --> Bytecode
    Bytecode --> Store
```

---

## 7. Performance and Latency Characteristics

### Fraud Model Inference

| Metric | In-Process ONNX | Simulated External REST |
|---|---|---|
| Mean latency | 18.9 us | 15,102 us |
| P50 latency | ~15 us | ~14,000 us |
| P90 latency | ~25 us | ~16,000 us |
| P99 latency | 164.8 us | ~18,000 us |
| Speedup | 1x | ~800x slower |
| Network hops | 0 | Serialize + HTTP + Deserialize |
| JVM heap bytes | Shared context reference | Allocation per request |

### Adaptive Reordering Impact

| Metric | Unoptimized (ML first) | Adaptively Optimized (ML last) |
|---|---|---|
| Mean latency (95% benign) | 22.7 us | 1.5 us |
| P99 latency | ~190 us | ~12 us |
| Throughput | 59,000 ops/sec | 730,000 ops/sec |
| Relative improvement | baseline | 93.4% latency reduction, 12.4x throughput |

The fundamental reason for this improvement: 95% of transactions are benign and have
`is_vpn_or_proxy == 0.0`. When placed first, this 80 ns predicate short-circuits the entire
AND chain — the 18,000 ns fraud model inference is never invoked for 95% of requests.

---

## 8. Related Documentation

- [ONNX Model Inference](../core-guides/onnx-model-inference) - ML() grammar reference, OnnxSessionPool configuration, and Python export workflow.
- [Adaptive AST Optimizer](../core-guides/adaptive-ast-optimizer) - Cost-to-failure theory, ReorderingPolicy SPI, and hot-swap mechanism.
- [Compilation Pipeline](compilation-pipeline) - Full 5-stage rule compilation from JSON DSL to JVM bytecode.
- [Flamegraph Profiling](../core-guides/flamegraph-profiling) - JFR-based node-level profiling for measuring per-clause cost distributions.
- [Performance Tuning](../performance-tuning/continuous-benchmarking) - JMH benchmark results for OnnxInferenceBenchmark and AdaptiveOptimizerBenchmark.
- [Helix Cortex Model Registry REST API](../helix-cortex/api-and-telemetry#machine-learning-model-registry-rest-api-apiv1models) - Enterprise ONNX model lifecycle management, version activation, and Redis cluster hot-swap.
