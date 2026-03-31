-- Add task code prefix and sequential counter to projects
ALTER TABLE task_projects ADD COLUMN task_code_prefix VARCHAR(20) NOT NULL DEFAULT 'TASK_';
ALTER TABLE task_projects ADD COLUMN next_task_number INT NOT NULL DEFAULT 1;

-- Add task code to tasks (nullable for backward compatibility; always set on new tasks)
ALTER TABLE tasks ADD COLUMN task_code VARCHAR(50);

-- Backfill task codes for existing tasks using row number per project
UPDATE tasks t
SET task_code = sub.task_code_prefix || sub.rn::text
FROM (
    SELECT t2.id,
           p.task_code_prefix,
           ROW_NUMBER() OVER (PARTITION BY t2.project_id ORDER BY t2.id) AS rn
    FROM tasks t2
    JOIN task_projects p ON p.id = t2.project_id
) sub
WHERE t.id = sub.id;

-- Set next_task_number to (existing task count + 1) for each project
UPDATE task_projects p
SET next_task_number = COALESCE((
    SELECT COUNT(*) + 1
    FROM tasks t
    WHERE t.project_id = p.id
), 1);

-- Unique index so no two active tasks in a project share the same code
CREATE UNIQUE INDEX uq_tasks_task_code ON tasks (task_code) WHERE deleted_at IS NULL;
