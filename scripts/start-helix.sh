#!/usr/bin/env bash
set -e

# Detect script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

JAR_FILE="$PROJECT_ROOT/engine-core/target/engine-core-1.0.0-SNAPSHOT.jar"
JSA_FILE="$PROJECT_ROOT/engine-core/target/helix.jsa"

if [ ! -f "$JAR_FILE" ]; then
    echo "[INFO] Building Helix project..."
    mvn clean package -DskipTests -f "$PROJECT_ROOT/pom.xml"
fi

# HotSpot Tuning Flags for Fast CLI Startup
JAVA_OPTS="-XX:TieredStopAtLevel=1 -XX:+UseG1GC"

if [ -f "$JSA_FILE" ]; then
    echo "[INFO] AppCDS Archive detected. Enabling Class Data Sharing..."
    JAVA_OPTS="$JAVA_OPTS -XX:SharedArchiveFile=$JSA_FILE"
fi

echo "[INFO] Starting Helix Application..."
exec java $JAVA_OPTS -jar "$JAR_FILE" "$@"
