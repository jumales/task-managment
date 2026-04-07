CREATE TABLE users (
    id           UUID                     PRIMARY KEY,
    name         VARCHAR(255)             NOT NULL,
    email        VARCHAR(255)             NOT NULL,
    username     VARCHAR(255)             NOT NULL,
    active       BOOLEAN                  NOT NULL DEFAULT TRUE,
    avatar_file_id UUID,
    language     VARCHAR(5)               NOT NULL DEFAULT 'en',
    deleted_at   TIMESTAMP WITH TIME ZONE
);

-- Unique username among non-deleted users; soft-deleted rows must not block re-registration
CREATE UNIQUE INDEX idx_users_username_active ON users (username) WHERE deleted_at IS NULL;

CREATE TABLE user_outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Partial index: only unpublished rows need to be scanned by the scheduled publisher
CREATE INDEX idx_user_outbox_unpublished ON user_outbox_events (published) WHERE published = FALSE;
