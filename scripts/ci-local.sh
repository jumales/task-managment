#!/usr/bin/env bash
# Run the CI workflow locally via act (self-hosted mode).
# Replaces `git push` + `gh run watch` loop — no GitHub round-trip needed.
#
# Usage:
#   scripts/ci-local.sh              # run every service job sequentially
#   scripts/ci-local.sh task-service # run a single job
#   scripts/ci-local.sh -l           # list jobs
set -euo pipefail

cd "$(dirname "$0")/.."

WORKFLOW=.github/workflows/ci.yml
EVENT=workflow_dispatch

JOBS=(task-service user-service audit-service file-service search-service notification-service reporting-service)

if [[ "${1:-}" == "-l" || "${1:-}" == "--list" ]]; then
  act -W "$WORKFLOW" -l
  exit 0
fi

if ! command -v act >/dev/null 2>&1; then
  echo "❌ act not installed. brew install act" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "❌ Docker daemon not reachable — Testcontainers will fail." >&2
  exit 1
fi

run_job() {
  local job="$1"
  echo "────────────────────────────────────────"
  echo "▶ act $EVENT -j $job"
  echo "────────────────────────────────────────"
  act "$EVENT" -W "$WORKFLOW" -j "$job"
}

if [[ $# -ge 1 ]]; then
  run_job "$1"
  exit $?
fi

failed=()
for job in "${JOBS[@]}"; do
  if ! run_job "$job"; then
    failed+=("$job")
  fi
done

if ((${#failed[@]} > 0)); then
  echo "❌ Failed jobs: ${failed[*]}"
  exit 1
fi
echo "✅ All CI jobs passed locally"
