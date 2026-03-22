#!/usr/bin/env bash
# start-dev.sh — starts the full dev stack on macOS
# Opens each microservice in its own Terminal window and starts infrastructure via Docker Compose.
#
# Usage:
#   ./scripts/start-dev.sh           # start everything
#   ./scripts/start-dev.sh --no-elk  # skip Elasticsearch / Logstash / Kibana (saves ~1 GB RAM)

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SKIP_ELK=false

for arg in "$@"; do
  [[ "$arg" == "--no-elk" ]] && SKIP_ELK=true
done

# ── Helpers ──────────────────────────────────────────────────────────────────

log()  { echo "[start-dev] $*"; }
warn() { echo "[start-dev] WARNING: $*" >&2; }

# Open a new Terminal.app window and run <command> inside it.
# The window title is set via the PROMPT_COMMAND trick (works in bash & zsh).
open_terminal_window() {
  local title="$1"
  local command="$2"
  osascript <<EOF
tell application "Terminal"
  do script "printf '\\\\033]0;${title}\\\\007'; ${command}"
  activate
end tell
EOF
}

# Poll <url> until it returns HTTP 200 (or until <timeout_s> seconds pass).
wait_for_http() {
  local name="$1"
  local url="$2"
  local timeout_s="${3:-120}"
  local elapsed=0
  log "Waiting for $name ($url) ..."
  until curl -sf -o /dev/null "$url" 2>/dev/null; do
    if (( elapsed >= timeout_s )); then
      warn "$name did not become ready within ${timeout_s}s — continuing anyway."
      return
    fi
    sleep 3
    (( elapsed += 3 ))
  done
  log "$name is ready."
}

# ── Step 1: Docker infrastructure ────────────────────────────────────────────

INFRA_SERVICES="postgres zookeeper kafka keycloak"
if [[ "$SKIP_ELK" == false ]]; then
  INFRA_SERVICES="$INFRA_SERVICES elasticsearch logstash kibana"
  log "Starting Docker infrastructure (including ELK) ..."
else
  log "Starting Docker infrastructure (ELK skipped) ..."
fi

docker compose -f "$PROJECT_ROOT/docker-compose.yml" up -d $INFRA_SERVICES

# ── Step 2: Wait for critical infrastructure ──────────────────────────────────

# Postgres — required by every service
log "Waiting for Postgres to pass its healthcheck ..."
until docker inspect --format='{{.State.Health.Status}}' ms-postgres 2>/dev/null | grep -q "healthy"; do
  sleep 3
done
log "Postgres is healthy."

# Keycloak — required for JWT validation; takes ~30 s on first boot
wait_for_http "Keycloak" "http://localhost:8180/realms/demo" 180

# Kafka — required by task-service and audit-service
log "Waiting for Kafka ..."
until docker inspect --format='{{.State.Health.Status}}' ms-kafka 2>/dev/null | grep -q "healthy"; do
  sleep 3
done
log "Kafka is healthy."

# ── Step 3: Eureka Server (service registry — must start first) ───────────────

log "Starting eureka-server ..."
open_terminal_window "eureka-server" \
  "cd '$PROJECT_ROOT' && mvn spring-boot:run -pl eureka-server; exec \$SHELL"

wait_for_http "Eureka" "http://localhost:8761/actuator/health" 120

# ── Step 4: Other microservices (parallel, all register with Eureka) ──────────

for service in api-gateway user-service task-service audit-service; do
  log "Starting $service ..."
  open_terminal_window "$service" \
    "cd '$PROJECT_ROOT' && mvn spring-boot:run -pl $service; exec \$SHELL"
done

# ── Step 5: Web client (Vite dev server) ──────────────────────────────────────

log "Starting web-client ..."
open_terminal_window "web-client" \
  "cd '$PROJECT_ROOT/web-client' && npm run dev; exec \$SHELL"

# ── Done ──────────────────────────────────────────────────────────────────────

cat <<'BANNER'

  ┌─────────────────────────────────────────┐
  │  Dev stack is starting up               │
  │                                         │
  │  Eureka    http://localhost:8761         │
  │  Gateway   http://localhost:8080         │
  │  Keycloak  http://localhost:8180         │
  │  Web app   http://localhost:3000         │
  │  Kibana    http://localhost:5601         │
  └─────────────────────────────────────────┘

  Each service has its own Terminal window.
  Run ./scripts/stop-dev.sh to tear down Docker infrastructure.

BANNER
