-- Rename misleading assigned_user_id columns to names that reflect their actual meaning.
-- In comment_audit_records the column stores the comment author, not the task assignee.
-- In phase_audit_records the column stores who performed the phase change.
ALTER TABLE comment_audit_records RENAME COLUMN assigned_user_id TO comment_created_by_user_id;
ALTER TABLE phase_audit_records   RENAME COLUMN assigned_user_id TO changed_by_user_id;
