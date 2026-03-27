-- Task participants: flexible user-role associations per task
-- Replaces the single assigned_user_id with a multi-role model
CREATE TABLE task_participants (
    id          UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id     UUID                     NOT NULL REFERENCES tasks(id),
    user_id     UUID                     NOT NULL,
    role        VARCHAR(50)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP WITH TIME ZONE
);

-- A user can hold each role at most once per task at any time
CREATE UNIQUE INDEX task_participants_unique_idx
    ON task_participants(task_id, user_id, role)
    WHERE deleted_at IS NULL;

CREATE INDEX task_participants_task_id_idx ON task_participants(task_id);

-- Migrate existing assignees into the new table as ASSIGNEE participants
INSERT INTO task_participants (task_id, user_id, role, created_at)
SELECT id, assigned_user_id, 'ASSIGNEE', NOW()
FROM tasks
WHERE assigned_user_id IS NOT NULL
  AND deleted_at IS NULL;
