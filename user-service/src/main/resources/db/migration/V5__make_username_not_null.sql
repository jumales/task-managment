-- Backfill any legacy rows that pre-date the username column before enforcing NOT NULL
UPDATE users SET username = 'user_' || id WHERE username IS NULL;

-- Enforce username as mandatory
ALTER TABLE users ALTER COLUMN username SET NOT NULL;
