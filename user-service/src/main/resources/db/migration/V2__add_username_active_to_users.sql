-- Add username (nullable for backward compatibility with existing rows)
ALTER TABLE users ADD COLUMN username VARCHAR(255);

-- Add active flag; existing users are considered active
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- Unique username among non-deleted users; soft-deleted rows must not block re-registration
CREATE UNIQUE INDEX idx_users_username_active ON users (username) WHERE deleted_at IS NULL;
