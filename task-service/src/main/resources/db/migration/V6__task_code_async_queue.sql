-- Allow a task to exist before its code is assigned by the background scheduler
ALTER TABLE tasks ALTER COLUMN task_code DROP NOT NULL;

-- Durable job queue: each row represents a task that still needs a code assigned.
-- Processed rows (processed_at IS NOT NULL) are retained for auditability.
CREATE TABLE task_code_jobs (
    id           UUID        NOT NULL PRIMARY KEY,
    task_id      UUID        NOT NULL REFERENCES tasks(id),
    project_id   UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

-- Partial index covers only unprocessed jobs — keeps the scheduler query fast
-- even after millions of processed rows accumulate in the table.
CREATE INDEX idx_task_code_jobs_pending
    ON task_code_jobs (created_at ASC)
    WHERE processed_at IS NULL;
