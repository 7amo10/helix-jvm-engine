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
