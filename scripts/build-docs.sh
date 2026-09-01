#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "[INFO] Step 1: Compiling D2 Architecture Diagrams..."
"$SCRIPT_DIR/generate-diagrams.sh"

echo "[INFO] Step 2: Generating Aggregate Javadoc Site..."
mvn javadoc:aggregate -DskipTests -f "$PROJECT_ROOT/pom.xml"

echo "[INFO] Step 3: Copying Javadocs to website/static/api/..."
mkdir -p "$PROJECT_ROOT/website/static/api"
if [ -d "$PROJECT_ROOT/target/site/apidocs" ]; then
    cp -r "$PROJECT_ROOT/target/site/apidocs/"* "$PROJECT_ROOT/website/static/api/"
fi

echo "[INFO] Step 4: Building Docusaurus Production Static Site..."
cd "$PROJECT_ROOT/website"
npm run build

echo "[SUCCESS] Helix documentation build complete! Static site generated in website/build/"
