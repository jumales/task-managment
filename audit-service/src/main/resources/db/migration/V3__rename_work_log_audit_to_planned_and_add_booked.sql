-- Rename work_log_audit_records to planned_work_audit_records (tracks PLANNED_WORK_CREATED events).
-- Create booked_work_audit_records for BOOKED_WORK_CREATED/UPDATED/DELETED events.

-- 1. Rename and drop booked_hours column (not relevant for planned-work audit records)
ALTER TABLE work_log_audit_records RENAME TO planned_work_audit_records;
ALTER TABLE planned_work_audit_records DROP COLUMN booked_hours;
DROP INDEX IF EXISTS idx_work_log_audit_records_task_id;
CREATE INDEX idx_planned_work_audit_records_task_id ON planned_work_audit_records (task_id);

-- 2. Booked work audit records: tracks create, update, and delete of booked hours
CREATE TABLE booked_work_audit_records (
    id               UUID                     PRIMARY KEY,
    task_id          UUID                     NOT NULL,
    booked_work_id   UUID                     NOT NULL,
    change_type      VARCHAR(32)              NOT NULL,
    booked_work_user_id UUID,
    work_type        VARCHAR(32),
    booked_hours     INTEGER,
    changed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_booked_work_audit_records_task_id ON booked_work_audit_records (task_id);
