-- Adds an optimistic-locking version counter to the tasks table.
-- DEFAULT 0 initialises all existing rows without touching application code.
ALTER TABLE tasks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
