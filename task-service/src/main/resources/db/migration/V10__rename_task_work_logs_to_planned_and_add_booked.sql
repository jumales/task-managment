-- Rename task_work_logs to task_planned_works; planned hours are immutable, one per work type per task.
-- Create task_booked_works to store actual booked hours (multiple per task/work type).

-- 1. Rename and restructure planned work table
ALTER TABLE task_work_logs RENAME TO task_planned_works;
ALTER TABLE task_planned_works DROP COLUMN booked_hours;
ALTER TABLE task_planned_works DROP COLUMN deleted_at;

-- Drop old partial index (used deleted_at which no longer exists)
DROP INDEX IF EXISTS idx_work_logs_task_id;

-- One planned-work entry per work type per task; no soft-delete so a plain unique index is sufficient
CREATE UNIQUE INDEX ux_task_planned_works_task_work_type ON task_planned_works (task_id, work_type);

-- 2. Create booked work table: multiple entries per task/work type, soft-deletable
CREATE TABLE task_booked_works (
    id           UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id      UUID         NOT NULL REFERENCES tasks(id),
    user_id      UUID         NOT NULL,
    work_type    VARCHAR(32)  NOT NULL,
    booked_hours INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    deleted_at   TIMESTAMPTZ  NULL,

    CONSTRAINT chk_booked_work_hours CHECK (booked_hours > 0)
);

CREATE INDEX idx_task_booked_works_task_id ON task_booked_works (task_id) WHERE deleted_at IS NULL;
