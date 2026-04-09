-- ============================================================
-- Read-model projection of tasks built from the task-events Kafka topic.
-- One row per task; soft-deleted when a TASK_DELETED event arrives.
-- ============================================================

CREATE TABLE report_tasks (
    id                 UUID        PRIMARY KEY,
    task_code          VARCHAR(50),
    title              VARCHAR(500),
    description        TEXT,
    status             VARCHAR(32),
    project_id         UUID,
    project_name       VARCHAR(255),
    phase_id           UUID,
    phase_name         VARCHAR(64),
    assigned_user_id   UUID,
    assigned_user_name VARCHAR(255),
    planned_start      TIMESTAMPTZ,
    planned_end        TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL,
    deleted_at         TIMESTAMPTZ
);

-- Hot query: "my tasks (optionally within last N days)"
CREATE INDEX idx_report_tasks_assigned_user
    ON report_tasks (assigned_user_id, updated_at DESC)
    WHERE deleted_at IS NULL;
