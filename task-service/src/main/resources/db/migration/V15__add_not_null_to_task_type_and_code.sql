-- status was already NOT NULL from V1__init.sql; no change needed.

-- Backfill any null type values before enforcing the constraint (added as NULL in V4).
UPDATE tasks SET type = 'FEATURE' WHERE type IS NULL;
ALTER TABLE tasks ALTER COLUMN type SET NOT NULL;

-- task_code was backfilled for all existing rows in V9__add_task_code.sql.
ALTER TABLE tasks ALTER COLUMN task_code SET NOT NULL;
