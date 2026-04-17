#!/usr/bin/env bash
# test-idempotency.sh — verifies that duplicate Kafka messages are deduplicated
#
# Publishes the same task-changed event twice and confirms only one audit record
# and one notification are persisted.
#
# Prerequisites: dev stack must be running (./scripts/start-dev.sh)

set -euo pipefail

EVENT_ID="a1b2c3d4-e5f6-7890-abcd-ef1234567890"
TASK_ID="11111111-2222-3333-4444-555555555555"
PAYLOAD="{\"eventId\":\"${EVENT_ID}\",\"taskId\":\"${TASK_ID}\",\"changeType\":\"STATUS_CHANGED\",\"fromStatus\":\"TODO\",\"toStatus\":\"IN_PROGRESS\",\"changedAt\":\"2026-04-17T10:00:00Z\",\"assignedUserId\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"projectId\":null,\"taskTitle\":\"Idempotency Test Task\",\"commentId\":null,\"commentContent\":null,\"fromPhaseId\":null,\"fromPhaseName\":null,\"toPhaseId\":null,\"toPhaseName\":null,\"workLogId\":null,\"workLogUserId\":null,\"workType\":null,\"plannedHours\":null,\"bookedHours\":null,\"attachmentId\":null,\"fileName\":null}"

psql_audit()   { docker exec -i ms-postgres psql -U audit_svc        -d audit_db        -t -c "$1"; }
psql_notif()   { docker exec -i ms-postgres psql -U notification_svc  -d notification_db -t -c "$1"; }

echo "══════════════════════════════════════════════"
echo "  Idempotent Consumer Test"
echo "══════════════════════════════════════════════"

# ── Cleanup ───────────────────────────────────────────────────────────────────
echo ""
echo "▶ Clearing test data..."
psql_audit "DELETE FROM audit_records WHERE task_id = '${TASK_ID}';"
psql_audit "DELETE FROM processed_kafka_events WHERE event_id = '${EVENT_ID}';"
psql_notif "DELETE FROM notifications WHERE task_id = '${TASK_ID}';"
psql_notif "DELETE FROM processed_kafka_events WHERE event_id = '${EVENT_ID}';"

# ── Before counts ─────────────────────────────────────────────────────────────
echo ""
echo "▶ Before — audit_records for task ${TASK_ID}:"
psql_audit "SELECT COUNT(*) FROM audit_records WHERE task_id = '${TASK_ID}';"
echo "▶ Before — notifications for task ${TASK_ID}:"
psql_notif "SELECT COUNT(*) FROM notifications WHERE task_id = '${TASK_ID}';"

# ── Publish duplicate events ──────────────────────────────────────────────────
echo ""
echo "▶ Publishing the same event TWICE to task-changed topic..."
printf '%s\n%s\n' "$PAYLOAD" "$PAYLOAD" \
  | docker exec -i ms-kafka kafka-console-producer \
      --bootstrap-server kafka:29092 \
      --topic task-changed

# ── Wait for consumers ────────────────────────────────────────────────────────
echo ""
echo "▶ Waiting 6 seconds for consumers to process..."
sleep 6

# ── After counts ─────────────────────────────────────────────────────────────
echo ""
echo "▶ After — audit_records for task ${TASK_ID} (expected: 1):"
psql_audit "SELECT COUNT(*) FROM audit_records WHERE task_id = '${TASK_ID}';"
echo "▶ After — processed_kafka_events in audit_db (expected: 1):"
psql_audit "SELECT event_id, consumer_group, processed_at FROM processed_kafka_events WHERE event_id = '${EVENT_ID}';"
echo ""
echo "▶ After — notifications for task ${TASK_ID} (expected: 1 or 0 if no recipient user in DB):"
psql_notif "SELECT COUNT(*) FROM notifications WHERE task_id = '${TASK_ID}';"
echo "▶ After — processed_kafka_events in notification_db (expected: 1):"
psql_notif "SELECT event_id, consumer_group, processed_at FROM processed_kafka_events WHERE event_id = '${EVENT_ID}';"

echo ""
echo "══════════════════════════════════════════════"
echo "  Done. audit_records and notifications should"
echo "  each show COUNT = 1 (or 0 for notifications"
echo "  if no assignee user exists in Keycloak)."
echo "  processed_kafka_events = 1 in both DBs."
echo "══════════════════════════════════════════════"
