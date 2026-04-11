-- Supports efficient phase-based filtering used by findByPhaseIdIn (completionStatus filter)
-- and findByPhaseIdNotIn (default active-only task list).
CREATE INDEX idx_tasks_phase_id ON tasks (phase_id) WHERE deleted_at IS NULL;
