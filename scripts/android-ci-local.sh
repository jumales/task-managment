#!/usr/bin/env bash
# Run the Android CI workflow locally via act (self-hosted mode).
# Runs only the unit-test job; instrumented tests require a real emulator.
#
# Usage:
#   scripts/android-ci-local.sh          # run unit-test job
#   scripts/android-ci-local.sh -l       # list jobs
set -euo pipefail

cd "$(dirname "$0")/.."

WORKFLOW=.github/workflows/android.yml
EVENT=workflow_dispatch
JOB=unit-test

if [[ "${1:-}" == "-l" || "${1:-}" == "--list" ]]; then
  act -W "$WORKFLOW" -l
  exit 0
fi

if ! command -v act >/dev/null 2>&1; then
  echo "❌ act not installed. brew install act" >&2
  exit 1
fi

echo "────────────────────────────────────────"
echo "▶ act $EVENT -j $JOB (android CI)"
echo "────────────────────────────────────────"
act "$EVENT" -W "$WORKFLOW" -j "$JOB"
