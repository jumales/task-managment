# Flyway Migrations

## How it works on restart

Every time the application starts, Flyway runs before any request is served:

1. Looks at the `flyway_schema_history` table in the database
2. Compares it against migration files in `db/migration/`
3. Runs any files not yet recorded there, in version order
4. If nothing is new → does nothing, app starts normally

Normal restart with no new migrations: zero SQL executed.

---

## How to trigger a schema update

Add a new file to `src/main/resources/db/migration/` with the next version number:

```
V2__add_due_date_to_tasks.sql
```

```sql
ALTER TABLE tasks ADD COLUMN due_date TIMESTAMP WITH TIME ZONE;
```

On the next restart Flyway sees `V2` is missing from `flyway_schema_history`, runs it, records it, and the app starts. No manual intervention needed.

---

## The immutability rule

If you edit an already-applied migration file, Flyway detects the checksum mismatch and **refuses to start**:

```
Migration checksum mismatch for migration version 1
```

This is intentional — it protects against silent schema drift. The fix is always to create a new `V{n}` file, never edit an existing one.

---

## Baseline — migrating an existing database

When Flyway is added to a project that already has a schema (created by Hibernate `ddl-auto: update` or manually), `flyway_schema_history` does not exist. On the first startup Flyway will try to run `V1__init.sql` and fail because the tables already exist.

**Fix — run once per database before the first deploy:**

```bash
flyway -url=jdbc:postgresql://localhost:5432/task_db \
       -user=task_svc -password=task_svc_pass \
       baseline -baselineVersion=1
```

This creates `flyway_schema_history` with `V1` pre-marked as applied. Future migrations (`V2`, `V3`, ...) run normally from that point.

Repeat for every service database:

```bash
flyway ... -url=.../user_db  baseline -baselineVersion=1
flyway ... -url=.../audit_db baseline -baselineVersion=1
```

---

## Key lessons

- Flyway runs automatically on startup — no cron job or manual trigger needed
- Migration files are immutable after merge — edit = startup failure
- Each service owns its own `flyway_schema_history` and its own migration folder
- Prefer backward-compatible changes: add nullable columns, never drop/rename in one step
- Soft-delete tables need partial unique indexes (`WHERE deleted_at IS NULL`) — a regular `UNIQUE` constraint would block re-insertion of soft-deleted rows
