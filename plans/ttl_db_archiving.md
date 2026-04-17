# TTL & DB Archiving Plan

## Context
Tasks and related data accumulate indefinitely. Closed tasks (RELEASED/REJECTED) must move to
archive tables after a configurable number of days, and archive tables must be dropped after a
configurable retention period. Cross-service projections (audit, reporting, search, notification,
files) are cleaned up via a Kafka event published after each archive batch.

## Constraints & Decisions
- Archive tables live in a separate `archive` PostgreSQL schema per service
- YYYYMM suffix is derived from **task creation date** (not close date)
- `closed_at` column added to `tasks` — set when phase transitions to RELEASED or REJECTED
- All TTL values are in `config-repo/application.yml` under `ttl.*`
- `TaskEventType.ARCHIVED` published on existing `task-events` Kafka topic — one event per task
- Archiving runs in nightly batches; configurable batch size (default 100) to avoid long locks

---

## Part A — Common Module Changes

**Files to modify:**

| File | Change |
|---|---|
| `common/.../dto/TaskEventType.java` | Add `ARCHIVED` enum value |
| `common/.../event/TaskEvent.java` | Add `List<UUID> archivedFileIds` (null for non-ARCHIVED types) |
| `common/.../event/TaskEvent.java` | Add `String archiveMonth` (e.g. `"202401"`, null for non-ARCHIVED) |

---

## Part B — Task-Service Changes

### B1. Flyway Migrations

**`V{n}__add_closed_at_to_tasks.sql`**
```sql
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
CREATE INDEX idx_tasks_closed_at ON tasks (closed_at)
    WHERE closed_at IS NOT NULL AND deleted_at IS NULL;
```

**`V{n+1}__create_archive_schema.sql`**
```sql
CREATE SCHEMA IF NOT EXISTS archive;
```

### B2. Phase Transition — Set closed_at

**File:** `task-service/.../service/TaskPhaseService.java` (wherever phase change is applied)

When setting new phase:
- If new phase name is in `TaskPhaseName.FINISHED_PHASES` (`RELEASED`, `REJECTED`)
  **and** `task.closedAt == null`: set `task.closedAt = Instant.now()` before saving

### B3. New Classes

**`TtlProperties.java`** — `@ConfigurationProperties("ttl")`

Binds all TTL values from config (see Configuration section below).

---

**`ArchiveTableManager.java`** — dynamic DDL via `JdbcTemplate`

```
ensureTableExists(String baseTable, YearMonth month)
  → CREATE TABLE IF NOT EXISTS archive.{baseTable}_{yyyyMM} (LIKE {baseTable} INCLUDING ALL)
  
dropExpiredTables(String baseTable, int retentionMonths)
  → SELECT tablename FROM pg_tables WHERE schemaname='archive' AND tablename LIKE '{baseTable}_%'
  → parse YYYYMM suffix; DROP TABLE IF EXISTS archive.{table} for tables older than cutoff
```

Note: `LIKE table INCLUDING ALL` copies columns, check constraints, NOT NULL, indexes, and defaults —
but intentionally skips FK constraints (correct for archive; FK targets won't be in archive schema).

Table names built from a hardcoded allowlist — no user input reaches DDL.

---

**`TaskArchiveService.java`** — core archiving logic

```
@Transactional
archiveExpiredTasks():
  1. SELECT id, created_at FROM tasks t
     JOIN task_phases tp ON t.phase_id = tp.id
     WHERE tp.name IN ('RELEASED','REJECTED')
       AND t.closed_at < NOW() - INTERVAL '{archiveAfterClosedDays} days'
       AND t.deleted_at IS NULL
     LIMIT {batchSize}

  2. Group taskIds by YearMonth(task.createdAt)

  3. For each (yearMonth, taskIds):
     a. ensureTableExists for all 7 tables (see list below)
     b. INSERT INTO archive.tasks_{MM}             SELECT * FROM tasks             WHERE id IN (?)
     c. INSERT INTO archive.task_comments_{MM}     SELECT * FROM task_comments     WHERE task_id IN (?)
     d. INSERT INTO archive.task_participants_{MM} SELECT * FROM task_participants  WHERE task_id IN (?)
     e. INSERT INTO archive.task_timelines_{MM}    SELECT * FROM task_timelines    WHERE task_id IN (?)
     f. INSERT INTO archive.task_planned_works_{MM} SELECT * FROM task_planned_works WHERE task_id IN (?)
     g. INSERT INTO archive.task_booked_works_{MM} SELECT * FROM task_booked_works  WHERE task_id IN (?)
     h. SELECT file_id FROM task_attachments WHERE task_id IN (?)  → collect fileIds
     i. INSERT INTO archive.task_attachments_{MM}  SELECT * FROM task_attachments   WHERE task_id IN (?)
     j. DELETE child rows (reverse FK order)
     k. DELETE FROM tasks WHERE id IN (?)
     l. publishArchivedEvents(taskIds, fileIds, yearMonth)
```

Tables archived per task:

| Main table | Archive table pattern |
|---|---|
| `tasks` | `archive.tasks_YYYYMM` |
| `task_comments` | `archive.task_comments_YYYYMM` |
| `task_participants` | `archive.task_participants_YYYYMM` |
| `task_timelines` | `archive.task_timelines_YYYYMM` |
| `task_planned_works` | `archive.task_planned_works_YYYYMM` |
| `task_booked_works` | `archive.task_booked_works_YYYYMM` |
| `task_attachments` | `archive.task_attachments_YYYYMM` |

NOT archived (cleaned up separately):
- `task_code_jobs` — delete processed rows after N days
- `outbox_events` — delete published rows after N days

---

**`TaskArchiveScheduler.java`**

```java
@Scheduled(cron = "0 2 * * *")   // nightly 02:00
void archiveExpiredTasks() { taskArchiveService.archiveExpiredTasks(); }

@Scheduled(cron = "0 3 * * *")   // nightly 03:00
void dropExpiredArchiveTables() { /* ArchiveTableManager.dropExpiredTables() per base table */ }
```

**`OutboxCleanupService.java`**
```java
@Scheduled(cron = "0 4 * * *")
void cleanup() {
    // DELETE FROM outbox_events WHERE published = TRUE
    //   AND created_at < NOW() - INTERVAL '{ttl.outbox.retention-days} days'
}
```

**`TaskCodeJobCleanupService.java`**
```java
@Scheduled(cron = "0 4 * * *")
void cleanup() {
    // DELETE FROM task_code_jobs WHERE processed_at IS NOT NULL
    //   AND created_at < NOW() - INTERVAL '{ttl.task-code-job.retention-days} days'
}
```

---

## Part C — Cross-Service Cleanup via TASK_ARCHIVED Event

### C1. Audit-Service

**Flyway migration:** `V{n}__create_archive_schema.sql` → `CREATE SCHEMA IF NOT EXISTS archive;`

**New:** `audit-service/.../listener/TaskArchivedEventListener.java`
- For each table: `ensureTableExists`, INSERT archive.*, DELETE from main by taskId

Tables archived:
- `audit_records` → `archive.audit_records_YYYYMM`
- `comment_audit_records` → `archive.comment_audit_records_YYYYMM`
- `phase_audit_records` → `archive.phase_audit_records_YYYYMM`
- `booked_work_audit_records` → `archive.booked_work_audit_records_YYYYMM`
- `planned_work_audit_records` → `archive.planned_work_audit_records_YYYYMM`

Archive TTL: `@Scheduled` drops expired audit archive tables (configurable, default 24 months).

### C2. Reporting-Service

**New:** `reporting-service/.../listener/TaskArchivedEventListener.java`
- DELETE from `report_booked_works`, `report_planned_works`, `report_tasks` by taskId
- No archiving — projections are not authoritative

### C3. Search-Service

**New:** `search-service/.../listener/TaskArchivedEventListener.java`
```java
elasticsearchOperations.delete(taskId.toString(), TaskDocument.class);
```

### C4. Notification-Service

**New:** `notification-service/.../scheduler/NotificationCleanupScheduler.java`
```java
@Scheduled(cron = "0 5 * * *")
// DELETE FROM notifications WHERE sent_at < NOW() - INTERVAL '{ttl.notification.retention-days} days'
```
Independent TTL — not triggered by TASK_ARCHIVED event.

### C5. File-Service

**New:** `file-service/.../listener/TaskArchivedEventListener.java`
- For each `fileId` in `event.archivedFileIds`:
  `UPDATE file_metadata SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL`

**New:** `file-service/.../scheduler/FileCleanupScheduler.java`
```java
@Scheduled(cron = "0 6 * * *")
// 1. SELECT id, bucket, object_key FROM file_metadata
//    WHERE deleted_at IS NOT NULL
//      AND deleted_at < NOW() - INTERVAL '{ttl.file.deleted-object-retention-days} days'
// 2. minioClient.removeObject(bucket, objectKey) per row
// 3. DELETE FROM file_metadata WHERE id = ?
```

---

## Configuration (config-repo/application.yml additions)

```yaml
ttl:
  task:
    archive-after-closed-days: 90
    archive-retention-months: 12
    batch-size: 100
  outbox:
    retention-days: 30
  task-code-job:
    retention-days: 30
  audit:
    archive-retention-months: 24
  notification:
    retention-days: 365
  file:
    deleted-object-retention-days: 30
```

---

## Implementation Sequence

```
1. common           — TaskEventType.ARCHIVED, TaskEvent fields (archiveMonth, archivedFileIds)
2. task-service     — Flyway: V{n}__add_closed_at_to_tasks.sql
3. task-service     — Flyway: V{n+1}__create_archive_schema.sql
4. task-service     — Phase service: set closed_at on FINISHED_PHASES transition
5. task-service     — TtlProperties, ArchiveTableManager, TaskArchiveService
6. task-service     — TaskArchiveScheduler, OutboxCleanupService, TaskCodeJobCleanupService
7. audit-service    — Flyway: archive schema migration; ArchiveTableManager; TaskArchivedEventListener
8. reporting-service — TaskArchivedEventListener
9. search-service   — TaskArchivedEventListener
10. notification-service — NotificationCleanupScheduler
11. file-service    — TaskArchivedEventListener, FileCleanupScheduler
12. config-repo     — ttl.* block in application.yml
```

---

## Critical Files

| File | Action |
|---|---|
| `common/.../dto/TaskEventType.java` | Modify |
| `common/.../event/TaskEvent.java` | Modify |
| `task-service/.../service/TaskPhaseService.java` | Modify |
| `task-service/src/main/resources/db/migration/V{n}__add_closed_at_to_tasks.sql` | Create |
| `task-service/src/main/resources/db/migration/V{n+1}__create_archive_schema.sql` | Create |
| `audit-service/src/main/resources/db/migration/V{n}__create_archive_schema.sql` | Create |
| `task-service/.../config/TtlProperties.java` | Create |
| `task-service/.../archive/ArchiveTableManager.java` | Create |
| `task-service/.../archive/TaskArchiveService.java` | Create |
| `task-service/.../archive/TaskArchiveScheduler.java` | Create |
| `task-service/.../archive/OutboxCleanupService.java` | Create |
| `task-service/.../archive/TaskCodeJobCleanupService.java` | Create |
| `audit-service/.../listener/TaskArchivedEventListener.java` | Create |
| `audit-service/.../archive/ArchiveTableManager.java` | Create |
| `reporting-service/.../listener/TaskArchivedEventListener.java` | Create |
| `search-service/.../listener/TaskArchivedEventListener.java` | Create |
| `notification-service/.../scheduler/NotificationCleanupScheduler.java` | Create |
| `file-service/.../listener/TaskArchivedEventListener.java` | Create |
| `file-service/.../scheduler/FileCleanupScheduler.java` | Create |
| `config-repo/application.yml` | Modify |

---

## Verification

1. `closed_at` set — transition task to RELEASED/REJECTED; confirm `closed_at IS NOT NULL` in DB
2. Archive trigger — set `ttl.task.archive-after-closed-days: 0`, run scheduler; confirm `archive.tasks_YYYYMM` populated, row deleted from `tasks`
3. Cascade — confirm related tables (comments, participants, etc.) also moved
4. Event — consume `task-events`; confirm `ARCHIVED` event has correct `archiveMonth` and `archivedFileIds`
5. Cross-service — confirm audit records archived, report_tasks deleted, Elasticsearch document deleted, file_metadata marked `deleted_at`
6. File cleanup — run FileCleanupScheduler; confirm MinIO object deleted, file_metadata row removed
7. Archive drop — set `archive-retention-months: 0`; run drop scheduler; confirm archive tables dropped
8. Queue cleanup — confirm `outbox_events` and `task_code_jobs` rows deleted after TTL
9. Integration tests — cover: archive trigger, event publish, archive table creation, related data migration, delete from main table
