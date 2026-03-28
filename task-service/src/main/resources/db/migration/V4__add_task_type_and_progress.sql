-- Add task type classification and completion progress tracking
ALTER TABLE tasks ADD COLUMN type VARCHAR(32) NULL;
ALTER TABLE tasks ADD COLUMN progress INT NOT NULL DEFAULT 0;

-- Ensure progress stays within 0–100
ALTER TABLE tasks ADD CONSTRAINT chk_task_progress CHECK (progress >= 0 AND progress <= 100);
