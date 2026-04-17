-- Explicit creation timestamp for tasks, used by the archive scheduler to determine the
-- archive table suffix (YYYYMM). Backfilled for existing rows using the Unix millisecond
-- timestamp embedded in the UUID v7 id (high 48 bits = ms since epoch).
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

UPDATE tasks
SET created_at = TO_TIMESTAMP(
    (
        (('x' || TRANSLATE(SPLIT_PART(id::text, '-', 1), '-', ''))::bit(32)::bigint << 16)
        + (('x' || TRANSLATE(SPLIT_PART(id::text, '-', 2), '-', ''))::bit(16)::bigint)
    ) / 1000.0
)
WHERE created_at IS NULL;

ALTER TABLE tasks ALTER COLUMN created_at SET DEFAULT NOW();
ALTER TABLE tasks ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX idx_tasks_created_at ON tasks (created_at) WHERE deleted_at IS NULL;
