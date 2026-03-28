-- Index to speed up username uniqueness checks (existsByUsername) on user create/update.
-- Partial so soft-deleted rows do not block re-use of the same username.
CREATE UNIQUE INDEX idx_users_username_active
    ON users (username)
    WHERE deleted_at IS NULL;
