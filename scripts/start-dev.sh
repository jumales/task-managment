#!/usr/bin/env bash
# start-dev.sh — starts the full dev stack on macOS
# Opens each microservice in its own Terminal window and starts infrastructure via Docker Compose.
#
# Usage:
#   ./scripts/start-dev.sh                      # start everything
#   ./scripts/start-dev.sh --no-elk             # skip Elasticsearch / Logstash / Kibana (saves ~1 GB RAM)
#   ./scripts/start-dev.sh --restart <service>  # kill & reopen one service terminal
#
# Valid service names for --restart:
#   eureka-server | api-gateway | user-service | task-service | audit-service | web-client

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SKIP_ELK=false
RESTART_SERVICE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-elk)        SKIP_ELK=true ;;
    --restart)       RESTART_SERVICE="${2:-}"; shift ;;
    *) ;;
  esac
  shift
done

# ── Helpers ──────────────────────────────────────────────────────────────────

log()  { echo "[start-dev] $*"; }
warn() { echo "[start-dev] WARNING: $*" >&2; }

# Open a new Terminal.app window and run <command> inside it.
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

# Kill any Terminal window whose title matches <title>, then open a fresh one.
restart_terminal_window() {
  local title="$1"
  local command="$2"

  # Close existing window for this service (matched by tab title)
  osascript <<EOF 2>/dev/null || true
tell application "Terminal"
  set windowList to every window
  repeat with w in windowList
    repeat with t in every tab of w
      if name of t contains "${title}" then
        do script "kill %1" in t
        close (every window whose name contains "${title}")
        exit repeat
      end if
    end repeat
  end repeat
end tell
EOF

  sleep 1
  open_terminal_window "$title" "$command"
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

# Start a single named service in a new (or restarted) Terminal window.
start_service() {
  local name="$1"
  case "$name" in
    eureka-server|api-gateway|user-service|task-service|audit-service)
      open_terminal_window "$name" \
        "cd '$PROJECT_ROOT' && mvn spring-boot:run -pl $name; exec \$SHELL"
      ;;
    web-client)
      open_terminal_window "web-client" \
        "cd '$PROJECT_ROOT/web-client' && npm run dev; exec \$SHELL"
      ;;
    *)
      warn "Unknown service '$name'."
      echo "Valid names: eureka-server api-gateway user-service task-service audit-service web-client"
      exit 1
      ;;
  esac
}

# ── Single-service restart mode ───────────────────────────────────────────────

if [[ -n "$RESTART_SERVICE" ]]; then
  log "Restarting $RESTART_SERVICE ..."
  restart_terminal_window "$RESTART_SERVICE" ""   # close old window
  start_service "$RESTART_SERVICE"
  log "$RESTART_SERVICE restarted."
  exit 0
fi

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
start_service eureka-server

wait_for_http "Eureka" "http://localhost:8761/actuator/health" 120

# ── Step 4: Other microservices (parallel, all register with Eureka) ──────────

for service in api-gateway user-service task-service audit-service; do
  log "Starting $service ..."
  start_service "$service"
done

# ── Step 5: Web client (Vite dev server) ──────────────────────────────────────

log "Starting web-client ..."
start_service web-client

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
  Restart one service:  ./scripts/start-dev.sh --restart <name>
  Stop infrastructure:  ./scripts/stop-dev.sh

BANNER
