#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-app/src/main/assets/runtime/arm64}"
test -x "$ROOT/bin/java"
find "$ROOT" -name libjli.so -print -quit | grep -q .
find "$ROOT" -name libjvm.so -print -quit | grep -q .
echo "OK: ARM64 Java runtime is complete: $ROOT"
