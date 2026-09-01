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
- `-m, --mode=<mode>`: Execution mode: `sync`, `async`, `batch`. Default: `sync`.
- `-o, --output=<format>`: `text`, `json`, `csv`.

---

## 3. `profile`

Runs a performance profiling session or launches the interactive TUI.

```bash
./scripts/start-helix.sh profile [--tui] [--mode=<mode>] [--seconds=<sec>]
```

---

## 4. `experiment`

Runs JVM behavior experiments.

```bash
./scripts/start-helix.sh experiment --name=<name> [--output=<format>]
```
- `--name`: `jit`, `metaspace`, `gc`, `safepoint`, `layout`, `all`.
