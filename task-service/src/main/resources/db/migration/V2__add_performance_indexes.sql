-- Performance indexes for high-volume query paths

-- tasks: filter by project (most common filter in UI)
CREATE INDEX idx_tasks_project_id       ON tasks (project_id)       WHERE deleted_at IS NULL;

-- tasks: filter by assigned user (My Tasks view)
CREATE INDEX idx_tasks_assigned_user_id ON tasks (assigned_user_id) WHERE deleted_at IS NULL;

-- tasks: filter by status (board/kanban view)
CREATE INDEX idx_tasks_status           ON tasks (status)           WHERE deleted_at IS NULL;

-- task_comments: fetch comments for a task (ordered by created_at)
CREATE INDEX idx_task_comments_task_id  ON task_comments (task_id, created_at) WHERE deleted_at IS NULL;

-- task_phases: fetch phases for a project
CREATE INDEX idx_task_phases_project_id ON task_phases (project_id) WHERE deleted_at IS NULL;
