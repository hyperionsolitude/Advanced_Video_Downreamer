#!/usr/bin/env bash

set -euo pipefail

# Resolve repository root based on this script's location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

echo "[sort_imports] Running ktlint auto-format for main source set..."
./gradlew ktlintMainSourceSetFormat --no-daemon --quiet

echo "[sort_imports] Verifying ktlint after formatting..."
./gradlew ktlintMainSourceSetCheck --no-daemon --quiet || {
  echo "[sort_imports] ktlint check failed. See reports under app/build/reports/ktlint/" >&2
  exit 1
}

echo "[sort_imports] Imports sorted and ktlint passed."


