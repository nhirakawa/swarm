#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/swarm-runner/target/swarm-runner-1.0-SNAPSHOT.jar"

exec java \
  -Dlog.level="${LOG_LEVEL:-info}" \
  -jar "$JAR" \
  local "$@"
