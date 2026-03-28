-- Audit records for work log create, update, and delete operations
CREATE TABLE work_log_audit_records (
    id               UUID                     PRIMARY KEY,
    task_id          UUID                     NOT NULL,
    work_log_id      UUID                     NOT NULL,
    change_type      VARCHAR(32)              NOT NULL,
    work_log_user_id UUID,
    work_type        VARCHAR(32),
    planned_hours    INTEGER,
    booked_hours     INTEGER,
    changed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_work_log_audit_records_task_id ON work_log_audit_records (task_id);
