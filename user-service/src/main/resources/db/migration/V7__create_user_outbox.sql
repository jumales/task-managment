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

-- Partial index: only unpublished rows need to be scanned by the scheduled publisher.
CREATE INDEX idx_user_outbox_unpublished ON user_outbox_events (published) WHERE published = FALSE;
