-- Allows efficient lookup of all tasks a given user participates in (e.g. "watched tasks" queries)
CREATE INDEX idx_task_participants_user_id ON task_participants (user_id);

-- Standalone task_id filter on planned works, separate from the composite unique index on (task_id, work_type)
CREATE INDEX idx_task_planned_works_task_id ON task_planned_works (task_id);
