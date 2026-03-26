#!/usr/bin/env bash
# start-dev.sh — starts the full dev stack on macOS
# Opens each microservice in its own Terminal tab and starts infrastructure via Docker Compose.
#
# Usage:
#   ./scripts/start-dev.sh                      # start everything
#   ./scripts/start-dev.sh --no-elk             # skip Elasticsearch / Logstash / Kibana (saves ~1 GB RAM)
#   ./scripts/start-dev.sh --docker-only        # start Docker infrastructure only (no service terminals)
#   ./scripts/start-dev.sh --docker-only --no-elk  # Docker only, skip ELK
#   ./scripts/start-dev.sh --restart <service>  # kill & reopen one service tab
#
# Valid service names for --restart:
#   eureka-server | api-gateway | user-service | task-service | audit-service | file-service | web-client

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SKIP_ELK=false
RESTART_SERVICE=""
DOCKER_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-elk)        SKIP_ELK=true ;;
    --docker-only)   DOCKER_ONLY=true ;;
    --restart)       RESTART_SERVICE="${2:-}"; shift ;;
    *) ;;
  esac
  shift
done

# ── Helpers ──────────────────────────────────────────────────────────────────

log()  { echo "[start-dev] $*"; }
warn() { echo "[start-dev] WARNING: $*" >&2; }

# Open a new Terminal.app tab and run <command> inside it.
open_terminal_tab() {
  local title="$1"
  local command="$2"
  osascript <<EOF
tell application "Terminal"
  activate
  if (count of windows) = 0 then
    do script "printf '\\\\033]0;${title}\\\\007'; ${command}"
  else
    tell application "System Events" to keystroke "t" using {command down}
    do script "printf '\\\\033]0;${title}\\\\007'; ${command}" in front window
  end if
end tell
EOF
}

# Close any Terminal tab whose title matches <title>, then open a fresh one.
restart_terminal_tab() {
  local title="$1"
  local command="$2"

  # Close existing tab for this service (matched by tab title)
  osascript <<EOF 2>/dev/null || true
tell application "Terminal"
  set windowList to every window
  repeat with w in windowList
    repeat with t in every tab of w
      if name of t contains "${title}" then
        close t
        exit repeat
      end if
    end repeat
  end repeat
end tell
EOF

  sleep 1
  open_terminal_tab "$title" "$command"
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

# Start a single named service in a new Terminal tab.
start_service() {
  local name="$1"
  case "$name" in
    eureka-server|api-gateway|user-service|task-service|audit-service|file-service)
      open_terminal_tab "$name" \
        "cd '$PROJECT_ROOT' && mvn spring-boot:run -pl $name; exec \$SHELL"
      ;;
    web-client)
      open_terminal_tab "web-client" \
        "cd '$PROJECT_ROOT/web-client' && npm run dev; exec \$SHELL"
      ;;
    *)
      warn "Unknown service '$name'."
      echo "Valid names: eureka-server api-gateway user-service task-service audit-service file-service web-client"
      exit 1
      ;;
  esac
}

# ── Single-service restart mode ───────────────────────────────────────────────

if [[ -n "$RESTART_SERVICE" ]]; then
  log "Restarting $RESTART_SERVICE ..."
  restart_terminal_tab "$RESTART_SERVICE" ""   # close old tab
  start_service "$RESTART_SERVICE"
  log "$RESTART_SERVICE restarted."
  exit 0
fi

# ── Step 1: Docker infrastructure ────────────────────────────────────────────

INFRA_SERVICES="postgres zookeeper kafka keycloak minio minio-init"
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

# MinIO — required by file-service
log "Waiting for MinIO ..."
until docker inspect --format='{{.State.Health.Status}}' ms-minio 2>/dev/null | grep -q "healthy"; do
  sleep 3
done
log "MinIO is healthy."

# ── Docker-only mode: exit after infrastructure is healthy ────────────────────

if [[ "$DOCKER_ONLY" == true ]]; then
  log "Docker-only mode — infrastructure is up. Skipping service terminals."
  cat <<'BANNER'

  ┌─────────────────────────────────────────┐
  │  Docker infrastructure is running       │
  │                                         │
  │  Keycloak  http://localhost:8180         │
  │  Kibana    http://localhost:5601         │
  │  MinIO     http://localhost:9001         │
  └─────────────────────────────────────────┘

  Start services:  ./scripts/start-dev.sh
  Stop infra:      ./scripts/stop-dev.sh

BANNER
  exit 0
fi

# ── Step 3: Eureka Server (service registry — must start first) ───────────────

log "Starting eureka-server ..."
start_service eureka-server

wait_for_http "Eureka" "http://localhost:8761/actuator/health" 120

# ── Step 4: Other microservices (parallel, all register with Eureka) ──────────

for service in api-gateway user-service task-service audit-service file-service; do
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
  │  MinIO     http://localhost:9001         │
  └─────────────────────────────────────────┘

  Each service has its own Terminal tab.
  Restart one service:  ./scripts/start-dev.sh --restart <name>
  Stop infrastructure:  ./scripts/stop-dev.sh

BANNER
