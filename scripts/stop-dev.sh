#!/usr/bin/env bash
# stop-dev.sh — stops all Docker infrastructure started by start-dev.sh
# Java services and the Vite dev server must be stopped manually in their Terminal windows (Ctrl+C).

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[stop-dev] Stopping Docker infrastructure ..."
docker compose -f "$PROJECT_ROOT/docker-compose.yml" down

echo "[stop-dev] Done. Close the individual Terminal windows to stop Java services and the web client."
