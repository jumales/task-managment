CREATE TABLE device_tokens (
    id           UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    token        TEXT         NOT NULL,
    platform     VARCHAR(16)  NOT NULL,
    app_version  VARCHAR(64),
    created_at   TIMESTAMPTZ  NOT NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL,
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);

-- Per CLAUDE.md: partial unique index so soft-deleted rows do not block re-insertion.
CREATE UNIQUE INDEX uidx_device_tokens_user_token
    ON device_tokens (user_id, token)
    WHERE deleted_at IS NULL;
