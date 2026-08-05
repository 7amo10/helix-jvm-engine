#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

JAR_FILE="$PROJECT_ROOT/engine-core/target/engine-core-1.0.0-SNAPSHOT.jar"
LST_FILE="$PROJECT_ROOT/engine-core/target/helix.lst"
JSA_FILE="$PROJECT_ROOT/engine-core/target/helix.jsa"

if [ ! -f "$JAR_FILE" ]; then
    echo "[INFO] Building Helix project..."
    mvn clean package -DskipTests -f "$PROJECT_ROOT/pom.xml"
fi

echo "[INFO] Step 1: Dumping class list into $LST_FILE..."
java -XX:DumpLoadedClassList="$LST_FILE" -jar "$JAR_FILE" --help > /dev/null

echo "[INFO] Step 2: Generating AppCDS archive $JSA_FILE..."
java -Xshare:dump -XX:SharedClassListFile="$LST_FILE" -XX:SharedArchiveFile="$JSA_FILE" -jar "$JAR_FILE" > /dev/null || true

if [ -f "$JSA_FILE" ]; then
    echo "[SUCCESS] AppCDS archive generated successfully: $JSA_FILE"
else
    echo "[WARN] AppCDS archive generation finished."
fi
