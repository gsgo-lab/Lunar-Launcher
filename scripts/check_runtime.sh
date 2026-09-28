#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
R="$ROOT/app/src/main/assets/runtime/arm64"
if [[ ! -d "$R" ]]; then echo "missing: $R"; exit 1; fi
count=$(find "$R" -type f | wc -l | tr -d ' ')
if [[ "$count" == "0" ]]; then echo "runtime directory is empty"; exit 1; fi
echo "runtime payload files: $count"
find "$R" -maxdepth 2 -type f -printf '%p %s bytes\n' | head -50
