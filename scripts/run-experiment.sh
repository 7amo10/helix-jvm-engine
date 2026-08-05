#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

EXPERIMENT_NAME="${1:-all}"

echo "[INFO] Running Helix JVM Experiment: $EXPERIMENT_NAME..."
mvn exec:java -pl engine-experiments \
    -Dexec.mainClass="com.helix.experiments.ExperimentRunner" \
    -Dexec.args="$EXPERIMENT_NAME" -f "$PROJECT_ROOT/pom.xml"
