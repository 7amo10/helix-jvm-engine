---
id: cli-commands
title: Command-Line Interface (CLI) Reference
sidebar_position: 2
---

# CLI Commands Reference

Helix includes a CLI powered by Picocli supporting machine-readable JSON/CSV output formats.

---

## Global Options

```bash
./scripts/start-helix.sh [-hV] [COMMAND]
```

- `-h, --help`: Displays usage instructions and available subcommands.
- `-V, --version`: Prints version metadata.

---

## 1. `compile`

Compiles a JSON rule into JVM bytecode and outputs timing metrics.

```bash
./scripts/start-helix.sh compile -r=<ruleFile> [-o=<outputFormat>] [-q]
```

### Options
- `-r, --rule=<ruleFile>` *(Required)*: Path to the JSON rule file.
- `-o, --output=<format>`: Output format (`text`, `json`, `csv`). Default: `text`.
- `-q, --quiet`: Suppresses non-essential log lines.

---

## 2. `execute`

Evaluates a rule against input context variables.

```bash
./scripts/start-helix.sh execute -r=<ruleFile> -c=<contextFile> [-m=<mode>] [-o=<output>]
```

### Options
- `-r, --rule=<ruleFile>` *(Required)*: Path to JSON rule.
- `-c, --context=<contextFile>` *(Required)*: Path to JSON context data.
- `-m, --mode=<mode>`: Execution mode: `sync`, `async`, `batch`, `virtual`. Default: `sync`.
- `-o, --output=<format>`: Output format (`text`, `json`, `csv`). Default: `text`.

---

## 3. `profile`

Runs a performance profiling session, captures folded stack flame graphs, or launches the interactive TUI.

```bash
./scripts/start-helix.sh profile [--tui] [--flamegraph] [--dimension=<dim>] [--export=<file>] [--seconds=<sec>]
```

### Options
- `--tui`: Launches the full interactive Lanterna Terminal UI dashboard with live metrics and flame graph browser.
- `--flamegraph`: Aggregates in-memory folded stack traces and renders a Unicode ASCII flame graph in the terminal.
- `--dimension=<dim>`: Profiling sample dimension: `cpu` (execution time) or `alloc` (heap allocations). Default: `cpu`.
- `--export=<file>`: Exports flame graph to an interactive vector SVG (`.svg`) or HTML (`.html`) file.
- `--seconds=<sec>`: Duration of the profiling capture session in seconds. Default: `5`.

---

## 4. `repl`

Launches the interactive JLine 3 terminal shell for rapid rule prototyping, AST analysis, and bytecode disassembly.

```bash
./scripts/start-helix.sh repl [-r=<ruleFile>]
```

### Options
- `-r, --rule=<ruleFile>` *(Optional)*: Pre-loads a JSON rule into the shell upon startup.

### Interactive Shell Commands
- `:load <file>`: Reads and compiles a JSON rule specification.
- `:eval <contextJson>`: Evaluates the active compiled rule against input JSON variables.
- `:disasm`: Decompiles and displays generated JVM bytecode opcodes for the active rule.
- `:ast`: Dumps the initial and optimized Abstract Syntax Trees.
- `:history`: Displays command execution history.
- `:clear`: Clears the terminal screen.
- `:quit`: Exits the REPL session.

---

## 5. `experiment`

Runs JVM behavior experiments.

```bash
./scripts/start-helix.sh experiment --name=<name> [--output=<format>]
```
- `--name`: `jit`, `metaspace`, `gc`, `safepoint`, `layout`, `all`.

---

## 6. `stream`

Evaluates streaming event streams via local Disruptor ring buffer or distributed Kafka topics.

```bash
# Disruptor Ring Buffer Mode
./scripts/start-helix.sh stream --mode=disruptor --rule=<ruleFile> --events=<eventsFile> [--buffer-size=65536] [--threads=4]

# Clustered Kafka Mode
./scripts/start-helix.sh stream --mode=kafka --rule=<ruleFile> --bootstrap-servers=<servers> --topic=<inputTopic> --output-topic=<outputTopic> --group-id=<groupId>
```

### Options
- `-m, --mode=<mode>`: Streaming engine mode: `disruptor` (intra-process ring buffer) or `kafka` (clustered partitioned streaming). Default: `disruptor`.
- `-r, --rule=<ruleFile>` *(Required)*: Path to JSON rule specification.
- `-e, --events=<eventsFile>`: Path to JSON array file of event contexts (Disruptor mode).
- `-b, --bootstrap-servers=<servers>`: Kafka broker bootstrap servers (Kafka mode).
- `-t, --topic=<topic>`: Kafka input topic to consume events from.
- `--output-topic=<topic>`: Kafka destination topic for evaluated rule results.
- `-g, --group-id=<groupId>`: Kafka consumer group identifier. Default: `helix-stream-group`.
- `--buffer-size=<size>`: Disruptor ring buffer slot capacity (power of 2). Default: `65536`.
- `--threads=<n>`: Concurrency worker threads. Default: available processors.

---

## 7. `cache`

Manages and inspects the L4 out-of-process distributed Redis cache tier.

```bash
./scripts/start-helix.sh cache <subcommand> [options]
```

### Subcommands
- `status`: Connects to Redis and prints connection status, memory utilization, and active cache key metrics.
- `get --rule=<name>`: Retrieves and inspects serialized bytecode metadata for a cached rule.
- `invalidate --rule=<name> [--version=<version>]`: Invalidates a cached rule across the cluster via Redis Pub/Sub invalidation broadcast.
- `clear`: Purges all cached Helix rule bytecode from Redis.

### Options
- `-H, --host=<host>`: Redis server hostname. Default: `localhost` (or `REDIS_HOST` env var).
- `-p, --port=<port>`: Redis server port. Default: `6379` (or `REDIS_PORT` env var).
- `-a, --auth=<password>`: Redis authentication password (or `REDIS_PASSWORD` env var).
- `-r, --rule=<ruleName>`: Name of rule to inspect or invalidate.
- `-v, --version=<version>`: Optional version tag of rule to invalidate.
- `-c, --channel=<channel>`: Redis Pub/Sub invalidation channel. Default: `helix:cache:invalidation`.

