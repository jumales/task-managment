# TTL & Archiving — Manual Testing Guide

## Context

The TTL/archiving system (branch `ttl_archiving_system`) archives closed tasks (RELEASED/REJECTED)
after a configurable number of days, publishes a `TASK_ARCHIVED` Kafka event, and each downstream
service cleans up its own projections. Schedulers run nightly so manual testing requires overrides.

---

## Step 1 — Override TTL to 0 days

In `config-repo/application.yml`, temporarily set:
```yaml
ttl:
  task:
    archive-after-closed-days: 0
  file:
    deleted-object-retention-days: 0
```

Restart task-service and file-service (they pull config from config-server on startup).

---

## Step 2 — Create and close a task

1. Create a project + task via Postman or the UI
2. Add a comment, attach a file, add a participant
3. Transition the task phase to **RELEASED** or **REJECTED**
4. Verify `closed_at` was set:
```sql
SELECT id, closed_at FROM tasks WHERE id = '<task-id>';
-- Must be non-null
```

---

## Step 3 — Trigger the archive scheduler

Schedulers run at 02:00/03:00 nightly. To trigger without waiting:

**Option A (cleanest) — local profile cron override:**

Create `task-service/src/main/resources/application-local.yml`:
```yaml
# temporary — revert after testing
# overrides the nightly cron to run every 30 seconds
```
Then in `TaskArchiveScheduler`, temporarily change the cron to `0/30 * * * * *`, run once, revert.

**Option B — verify eligibility first via SQL:**
```sql
SELECT t.id, t.closed_at, tp.name
FROM tasks t
JOIN task_phases tp ON t.phase_id = tp.id
WHERE tp.name IN ('RELEASED','REJECTED')
  AND t.closed_at IS NOT NULL
  AND t.deleted_at IS NULL;
```

---

## Step 4 — Verify archive tables

```sql
-- Task moved to archive (suffix = task's created_at month, e.g. 202604)
SELECT * FROM archive.tasks_202604 WHERE id = '<task-id>';

-- Row gone from main table
SELECT * FROM tasks WHERE id = '<task-id>';
-- Expected: 0 rows

-- Related data archived
SELECT * FROM archive.task_comments_202604   WHERE task_id = '<task-id>';
SELECT * FROM archive.task_attachments_202604 WHERE task_id = '<task-id>';
SELECT * FROM archive.task_participants_202604 WHERE task_id = '<task-id>';
```

---

## Step 5 — Verify cross-service cleanup (after TASK_ARCHIVED event consumed)

**Audit-service:**
```sql
SELECT * FROM audit_records WHERE task_id = '<task-id>';
-- Gone from main; check archive.audit_records_YYYYMM
```

**Reporting-service (hard-delete, no archive):**
```sql
SELECT * FROM report_tasks WHERE task_id = '<task-id>';
-- Expected: 0 rows
```

**Search-service (Elasticsearch):**
```bash
curl -X GET "localhost:9200/tasks/_doc/<task-id>"
# Expected: 404
```

**File-service — soft-delete triggered:**
```sql
SELECT id, deleted_at FROM file_metadata WHERE id = '<file-id>';
-- deleted_at should now be set
```

---

## Step 6 — Verify MinIO cleanup (FileCleanupScheduler)

Trigger `FileCleanupScheduler` (same cron override approach). Then:

1. Open MinIO console at `http://localhost:9001`
2. Confirm the file object no longer exists in its bucket
3. Confirm hard-delete:
```sql
SELECT * FROM file_metadata WHERE id = '<file-id>';
-- Expected: 0 rows
```

---

## Step 7 — Revert all overrides

Restore in `config-repo/application.yml`:
```yaml
ttl:
  task:
    archive-after-closed-days: 90
  file:
    deleted-object-retention-days: 30
```

Restart task-service and file-service.

---

## Key tip

The cleanest way to avoid waiting until 02:00 AM without touching shared config-repo is a
**local Spring profile override** (`application-local.yml` activated via `spring.profiles.active=local`).
This layers over config-server values without modifying the shared repo.
