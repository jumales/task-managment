CREATE TABLE task_projects (
    id          UUID                     PRIMARY KEY,
    name        VARCHAR(255)             NOT NULL,
    description TEXT,
    deleted_at  TIMESTAMP WITH TIME ZONE
);

CREATE TABLE task_phases (
    id          UUID                     PRIMARY KEY,
    project_id  UUID                     NOT NULL,
    name        VARCHAR(255)             NOT NULL,
    description TEXT,
    is_default  BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP WITH TIME ZONE
);

CREATE TABLE tasks (
    id               UUID                     PRIMARY KEY,
    title            VARCHAR(255)             NOT NULL,
    description      TEXT,
    status           VARCHAR(50)              NOT NULL,
    assigned_user_id UUID,
    project_id       UUID                     NOT NULL,
    phase_id         UUID,
    deleted_at       TIMESTAMP WITH TIME ZONE
);

CREATE TABLE task_comments (
    id         UUID                     PRIMARY KEY,
    task_id    UUID                     NOT NULL,
    content    TEXT,
    created_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE outbox_events (
    id             UUID                     PRIMARY KEY,
    aggregate_type VARCHAR(255)             NOT NULL,
    aggregate_id   UUID                     NOT NULL,
    event_type     VARCHAR(100)             NOT NULL,
    topic          VARCHAR(255)             NOT NULL,
    payload        TEXT                     NOT NULL,
    published      BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index for the outbox poller: quickly find unpublished events
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (published) WHERE published = FALSE;
