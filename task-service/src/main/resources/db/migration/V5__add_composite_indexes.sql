-- Composite index for the most common list query: tasks by project excluding finished phases.
-- Used by: findByProjectIdAndPhaseIdNotIn — covers both equality on project_id and the NOT IN on phase_id.
-- Without this, Postgres intersects two single-column indexes which is slower under concurrent load.
CREATE INDEX idx_tasks_project_id_phase_id
    ON tasks (project_id, phase_id)
    WHERE deleted_at IS NULL;

-- Composite index for tasks-by-user excluding finished phases.
-- Used by: findByAssignedUserIdAndPhaseIdNotIn — mirrors the project+phase pattern above.
CREATE INDEX idx_tasks_assigned_user_id_phase_id
    ON tasks (assigned_user_id, phase_id)
    WHERE deleted_at IS NULL;
