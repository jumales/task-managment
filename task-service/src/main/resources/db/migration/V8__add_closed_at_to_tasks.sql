-- Tracks when a task entered a terminal phase (RELEASED or REJECTED).
-- Used by the archive scheduler to enforce the configurable TTL window (default: 90 days after close).
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;

CREATE INDEX idx_tasks_closed_at ON tasks (closed_at)
    WHERE closed_at IS NOT NULL AND deleted_at IS NULL;
