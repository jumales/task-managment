-- Work log entries tracking planned and booked hours per user and work type on a task
CREATE TABLE task_work_logs (
    id           UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id      UUID         NOT NULL REFERENCES tasks(id),
    user_id      UUID         NOT NULL,
    work_type    VARCHAR(32)  NOT NULL,
    planned_hours INTEGER      NOT NULL DEFAULT 0,
    booked_hours  INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    deleted_at   TIMESTAMPTZ  NULL,

    CONSTRAINT chk_work_log_planned_hours CHECK (planned_hours >= 0),
    CONSTRAINT chk_work_log_booked_hours  CHECK (booked_hours  >= 0)
);

CREATE INDEX idx_work_logs_task_id ON task_work_logs(task_id) WHERE deleted_at IS NULL;
