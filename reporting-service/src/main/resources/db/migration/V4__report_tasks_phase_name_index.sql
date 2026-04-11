-- Replace the two-column index with a three-column composite that covers the new
-- phase_name filter used by the "My Open Tasks" and "My Finished Tasks" queries.
DROP INDEX idx_report_tasks_assigned_user;
CREATE INDEX idx_report_tasks_assigned_user
    ON report_tasks (assigned_user_id, phase_name, updated_at DESC)
    WHERE deleted_at IS NULL;
