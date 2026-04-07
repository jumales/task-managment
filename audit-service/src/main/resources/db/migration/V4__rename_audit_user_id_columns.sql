-- Rename the generic assigned_user_id column in comment and phase audit tables
-- to more descriptive names that reflect the actual semantics.
ALTER TABLE comment_audit_records RENAME COLUMN assigned_user_id TO comment_created_by_user_id;
ALTER TABLE phase_audit_records   RENAME COLUMN assigned_user_id TO changed_by_user_id;
