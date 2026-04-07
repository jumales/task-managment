-- task_id is already NOT NULL from V1__init.sql; no change needed.

-- Backfill any null content values before enforcing the constraint.
UPDATE task_comments SET content = '' WHERE content IS NULL;
ALTER TABLE task_comments ALTER COLUMN content SET NOT NULL;

-- user_id: no legacy data; enforce NOT NULL directly.
ALTER TABLE task_comments ALTER COLUMN user_id SET NOT NULL;
