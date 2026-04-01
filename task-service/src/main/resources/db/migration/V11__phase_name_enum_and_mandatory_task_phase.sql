-- 1. Add default_phase_id to task_projects; tracks which phase is auto-assigned to new tasks.
ALTER TABLE task_projects ADD COLUMN default_phase_id UUID;

-- 2. Populate default_phase_id from the phases that previously had is_default = true.
UPDATE task_projects p
SET default_phase_id = (
    SELECT ph.id
    FROM task_phases ph
    WHERE ph.project_id = p.id
      AND ph.is_default = true
      AND ph.deleted_at IS NULL
    LIMIT 1
);

-- 3. For tasks that have no phase yet, assign the project's default phase (if one exists).
UPDATE tasks t
SET phase_id = (
    SELECT p.default_phase_id
    FROM task_projects p
    WHERE p.id = t.project_id
)
WHERE t.phase_id IS NULL;

-- 4. Make phase_id NOT NULL — every task must belong to a phase.
--    Migration fails here if any task still has a NULL phase_id (project had no default phase).
ALTER TABLE tasks ALTER COLUMN phase_id SET NOT NULL;

-- 5. Drop is_default from task_phases — default phase is now owned by task_projects.
ALTER TABLE task_phases DROP COLUMN is_default;
