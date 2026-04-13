#!/usr/bin/env bash
# start-dev.sh — starts the full dev stack on macOS
# Opens each microservice in its own Terminal window and starts infrastructure via Docker Compose.
#
# Usage:
#   ./scripts/start-dev.sh                      # start everything
#   ./scripts/start-dev.sh --no-elk             # skip Elasticsearch / Logstash / Kibana (saves ~1 GB RAM)
#   ./scripts/start-dev.sh --docker-only        # start Docker infrastructure only (no service terminals)
#   ./scripts/start-dev.sh --docker-only --no-elk  # Docker only, skip ELK
#   ./scripts/start-dev.sh --restart <service>  # kill & reopen one service terminal
#
# Valid service names for --restart:
#   eureka-server | api-gateway | user-service | task-service | audit-service | file-service | search-service | notification-service | reporting-service | web-client

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
    eureka-server|api-gateway|user-service|task-service|audit-service|file-service|search-service|notification-service|reporting-service)
      local extra_args=""
      if [[ "$SKIP_ELK" == false ]]; then
        # Activate logstash profile and point TCP appender to localhost (Docker exposes port 5000)
        extra_args="-Dspring-boot.run.jvmArguments='-Dspring.profiles.active=logstash -Dlogback.destination=localhost:5000'"
      fi
      open_terminal_window "$name" \
        "cd '$PROJECT_ROOT' && mvn spring-boot:run -pl $name $extra_args; exec \$SHELL"
      ;;
    web-client)
      open_terminal_window "web-client" \
        "cd '$PROJECT_ROOT/web-client' && npm run dev; exec \$SHELL"
      ;;
    *)
      warn "Unknown service '$name'."
      echo "Valid names: eureka-server api-gateway user-service task-service audit-service file-service search-service notification-service reporting-service web-client"
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

# ── Step 0: Stop everything and free ports ────────────────────────────────────

log "Stopping existing services and freeing ports ..."

# Kill all Java Spring Boot processes started from this project
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "task-managment.*java"  2>/dev/null || true

# Kill Vite / node dev server
pkill -f "web-client.*vite" 2>/dev/null || true
pkill -f "web-client.*node" 2>/dev/null || true

# Free known service ports: eureka (primary + HA peer), gateway, microservices, web-client
for port in 8761 8762 8080 8081 8082 8083 8084 8085 8086 3000; do
  pids=$(lsof -ti:"$port" 2>/dev/null) || true
  if [[ -n "$pids" ]]; then
    log "Freeing port $port (pids: $pids)"
    echo "$pids" | xargs kill -9 2>/dev/null || true
  fi
done

# Stop Docker infrastructure (ignore errors if already down)
log "Stopping Docker infrastructure ..."
docker compose -f "$PROJECT_ROOT/docker-compose.yml" -f "$PROJECT_ROOT/docker-compose.override.yml" down 2>/dev/null || true

sleep 2
log "All stopped. Starting fresh ..."

# ── Step 1: Docker infrastructure ────────────────────────────────────────────

INFRA_SERVICES="postgres kafka keycloak minio minio-init redis mailhog prometheus grafana"
if [[ "$SKIP_ELK" == false ]]; then
  INFRA_SERVICES="$INFRA_SERVICES elasticsearch logstash kibana"
  log "Starting Docker infrastructure (including ELK) ..."
else
  log "Starting Docker infrastructure (ELK skipped) ..."
fi

docker compose -f "$PROJECT_ROOT/docker-compose.yml" -f "$PROJECT_ROOT/docker-compose.override.yml" up -d $INFRA_SERVICES

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

# Redis — required by api-gateway rate limiter
log "Waiting for Redis ..."
until docker inspect --format='{{.State.Health.Status}}' ms-redis 2>/dev/null | grep -q "healthy"; do
  sleep 3
done
log "Redis is healthy."

# Prometheus — wait for it to signal ready
wait_for_http "Prometheus" "http://localhost:9090/-/ready" 30
log "Grafana will be available at http://localhost:3001"

# ── Docker-only mode: exit after infrastructure is healthy ────────────────────

if [[ "$DOCKER_ONLY" == true ]]; then
  log "Docker-only mode — infrastructure is up. Skipping service terminals."
  cat <<'BANNER'

  ┌───────────────────────────────────────────┐
  │  Docker infrastructure is running         │
  │                                           │
  │  Keycloak    http://localhost:8180        │
  │  Kibana      http://localhost:5601        │
  │  MinIO       http://localhost:9001        │
  │  Redis       localhost:6379              │
  │  MailHog     http://localhost:8025        │
  │  Prometheus  http://localhost:9090        │
  │  Grafana     http://localhost:3001        │
  └───────────────────────────────────────────┘

  Start services:  ./scripts/start-dev.sh
  Stop infra:      ./scripts/stop-dev.sh

BANNER
  exit 0
fi

# ── Step 3: Eureka Server (service registry — must start first) ───────────────
# Single-node mode (default for local dev). For HA testing, start a second node:
#   mvn spring-boot:run -pl eureka-server -Dspring-boot.run.profiles=peer2 &
# Alternatively, run both peers via Docker: docker compose up -d eureka-peer1 eureka-peer2

log "Starting eureka-server ..."
start_service eureka-server

wait_for_http "Eureka" "http://localhost:8761/actuator/health" 120

# ── Step 4: Other microservices (parallel, all register with Eureka) ──────────

for service in api-gateway user-service task-service audit-service file-service search-service notification-service reporting-service; do
  log "Starting $service ..."
  start_service "$service"
done

# ── Step 5: Web client (Vite dev server) ──────────────────────────────────────

log "Starting web-client ..."
start_service web-client

# ── Done ──────────────────────────────────────────────────────────────────────

cat <<'BANNER'

  ┌────────────────────────────────────────────────┐
  │  Dev stack is starting up                      │
  │                                                │
  │  Eureka        http://localhost:8761           │
  │  Gateway       http://localhost:8080           │
  │  Keycloak      http://localhost:8180           │
  │  Web app       http://localhost:3000           │
  │  Kibana        http://localhost:5601           │
  │  MinIO         http://localhost:9001           │
  │  Elasticsearch http://localhost:9200           │
  │  Redis         localhost:6379                 │
  │  MailHog       http://localhost:8025           │
  │  Prometheus    http://localhost:9090           │
  │  Grafana       http://localhost:3001           │
  └────────────────────────────────────────────────┘

  Each service has its own Terminal window.
  Restart one service:  ./scripts/start-dev.sh --restart <name>
  Stop infrastructure:  ./scripts/stop-dev.sh

BANNER
