#!/usr/bin/env bash
# reset-data.sh — wipes all application data except user accounts
#
# Clears:
#   • task_db     — all task-service tables
#   • audit_db    — all audit records
#   • file_db     — file metadata
#   • notification_db — notifications
#   • Elasticsearch   — all indices
#   • Redis           — all cached data
#   • MinIO           — all files in every bucket
#
# Does NOT touch:
#   • user_db     — users, roles, rights and their associations are preserved
#   • Keycloak    — identity provider data is preserved
#
# Usage:
#   ./scripts/reset-data.sh           # interactive — asks for confirmation
#   ./scripts/reset-data.sh --yes     # skip confirmation prompt

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SKIP_CONFIRM=false
for arg in "$@"; do
  [[ "$arg" == "--yes" ]] && SKIP_CONFIRM=true
done

# ── Helpers ───────────────────────────────────────────────────────────────────

log()  { echo "[reset-data] $*"; }
warn() { echo "[reset-data] WARNING: $*" >&2; }

require_container() {
  local name="$1"
  if ! docker inspect --format='{{.State.Running}}' "$name" 2>/dev/null | grep -q "true"; then
    warn "Container '$name' is not running — skipping."
    return 1
  fi
  return 0
}

psql_exec() {
  local db="$1"
  local user="$2"
  local sql="$3"
  docker exec -i ms-postgres psql -U "$user" -d "$db" -c "$sql"
}

# ── Confirmation ──────────────────────────────────────────────────────────────

if [[ "$SKIP_CONFIRM" == false ]]; then
  echo ""
  echo "  ┌──────────────────────────────────────────────────────────┐"
  echo "  │  WARNING: This will permanently delete all data in:      │"
  echo "  │    • task_db, audit_db, file_db, notification_db         │"
  echo "  │    • Elasticsearch indices                                │"
  echo "  │    • Redis cache                                          │"
  echo "  │    • MinIO files (avatars, attachments)                   │"
  echo "  │                                                           │"
  echo "  │  user_db is NOT affected.                                 │"
  echo "  └──────────────────────────────────────────────────────────┘"
  echo ""
  read -r -p "  Type YES to confirm: " answer
  if [[ "$answer" != "YES" ]]; then
    echo "  Aborted."
    exit 0
  fi
  echo ""
fi

# ── PostgreSQL — task_db ──────────────────────────────────────────────────────

if require_container ms-postgres; then
  log "Clearing task_db ..."
  psql_exec task_db task_svc "
    TRUNCATE
      task_timelines,
      task_booked_works,
      task_planned_works,
      task_attachments,
      task_participants,
      task_comments,
      outbox_events,
      tasks,
      task_phases,
      project_notification_templates,
      task_projects
    RESTART IDENTITY CASCADE;
  "
  log "task_db cleared."

# ── PostgreSQL — audit_db ─────────────────────────────────────────────────────

  log "Clearing audit_db ..."
  psql_exec audit_db audit_svc "
    TRUNCATE
      comment_audit_records,
      phase_audit_records,
      booked_work_audit_records,
      planned_work_audit_records,
      audit_records
    RESTART IDENTITY CASCADE;
  "
  log "audit_db cleared."

# ── PostgreSQL — file_db ──────────────────────────────────────────────────────

  log "Clearing file_db ..."
  psql_exec file_db file_svc "
    TRUNCATE file_metadata RESTART IDENTITY CASCADE;
  "
  log "file_db cleared."

# ── PostgreSQL — notification_db ──────────────────────────────────────────────

  log "Clearing notification_db ..."
  psql_exec notification_db notification_svc "
    TRUNCATE notifications RESTART IDENTITY CASCADE;
  "
  log "notification_db cleared."

# ── PostgreSQL — user_db (DISABLED — uncomment when needed) ──────────────────
#
#  log "Clearing user_db ..."
#  psql_exec user_db user_svc "
#    TRUNCATE
#      user_outbox_events,
#      user_roles,
#      role_rights,
#      users,
#      roles,
#      rights
#    RESTART IDENTITY CASCADE;
#  "
#  log "user_db cleared."

fi

# ── Elasticsearch — all indices ───────────────────────────────────────────────

if require_container ms-elasticsearch; then
  log "Deleting all Elasticsearch indices ..."
  curl -sf -X DELETE "http://localhost:9200/*?expand_wildcards=all" \
    -H "Content-Type: application/json" > /dev/null
  log "Elasticsearch indices deleted."
fi

# ── Redis — flush all ─────────────────────────────────────────────────────────

if require_container ms-redis; then
  log "Flushing Redis ..."
  docker exec ms-redis redis-cli FLUSHALL > /dev/null
  log "Redis flushed."
fi

# ── MinIO — all buckets ───────────────────────────────────────────────────────

if require_container ms-minio; then
  log "Clearing MinIO buckets ..."
  docker run --rm --network host --entrypoint sh minio/mc:latest -c "
    mc alias set local http://localhost:9000 minio minio123 --quiet;
    mc rm --recursive --force local/avatars     2>/dev/null || true;
    mc rm --recursive --force local/attachments 2>/dev/null || true;
  "
  log "MinIO buckets cleared."
fi

# ── Done ──────────────────────────────────────────────────────────────────────

cat <<'BANNER'

  ┌──────────────────────────────────────────────────────────┐
  │  Reset complete                                          │
  │                                                          │
  │  Cleared:  task_db, audit_db, file_db, notification_db  │
  │            Elasticsearch, Redis, MinIO                   │
  │                                                          │
  │  Preserved: user_db (users, roles, rights)               │
  └──────────────────────────────────────────────────────────┘

BANNER
