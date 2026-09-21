import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docsSidebar: [
    {
      type: 'category',
      label: 'Getting Started',
      collapsed: false,
      collapsible: true,
      items: [
        'getting-started/introduction',
        'getting-started/quickstart-cli',
        'getting-started/installation',
        'getting-started/appcds-optimization',
      ],
    },
    {
      type: 'category',
      label: 'Architecture & Internals',
      collapsed: false,
      collapsible: true,
      items: [
        'architecture-and-internals/system-overview',
        'architecture-and-internals/compilation-pipeline',
        'architecture-and-internals/bytecode-generators',
        'architecture-and-internals/tiered-cache-internals',
        'architecture-and-internals/classloader-hierarchy',
        'architecture-and-internals/ml-inference-and-adaptive-optimization',
      ],
    },
    {
      type: 'category',
      label: 'Core Guides',
      collapsed: false,
      collapsible: true,
      items: [
        'core-guides/rule-syntax-and-schemas',
        'core-guides/ast-optimizations',
        'core-guides/executors',
        'core-guides/classloader-isolation',
        'core-guides/interactive-repl-and-debugging',
        'core-guides/flamegraph-profiling',
        'core-guides/redis-l4-cache',
        'core-guides/distributed-streaming',
        'core-guides/onnx-model-inference',
        'core-guides/adaptive-ast-optimizer',
      ],
    },
    {
      type: 'category',
      label: 'JVM Behavior Experiments',
      collapsed: false,
      collapsible: true,
      items: [
        'jvm-experiments/overview',
        'jvm-experiments/jit-compilation',
        'jvm-experiments/metaspace-reclamation',
        'jvm-experiments/gc-reference-pressure',
        'jvm-experiments/safepoint-monitoring',
        'jvm-experiments/object-layout-inspection',
      ],
    },
    {
      type: 'category',
      label: 'API & CLI Reference',
      collapsed: false,
      collapsible: true,
      items: [
        'api-and-cli-reference/java-api-reference',
        'api-and-cli-reference/cli-commands',
        'api-and-cli-reference/tui-dashboard',
        'api-and-cli-reference/java-agent-and-jmx',
        'api-and-cli-reference/example-rules-catalog',
      ],
    },
    {
      type: 'category',
      label: 'Performance Tuning',
      collapsed: false,
      collapsible: true,
      items: [
        'performance-tuning/hotspot-jvm-matrix',
        'performance-tuning/abstraction-costs',
        'performance-tuning/continuous-benchmarking',
      ],
    },
    {
      type: 'category',
      label: 'Helix Cortex Platform',
      collapsed: false,
      collapsible: true,
      items: [
        'helix-cortex/overview',
        'helix-cortex/architecture-integration',
        'helix-cortex/deployment-and-setup',
        'helix-cortex/api-and-telemetry',
        'helix-cortex/enterprise-use-cases',
      ],
    },
  ],
};

export default sidebars;
