CREATE TABLE task_projects (
    id                UUID         PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    task_code_prefix  VARCHAR(20)  NOT NULL DEFAULT 'TASK_',
    next_task_number  INT          NOT NULL DEFAULT 1,
    default_phase_id  UUID,
    deleted_at        TIMESTAMP WITH TIME ZONE
);

CREATE TABLE task_phases (
    id          UUID         PRIMARY KEY,
    project_id  UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    custom_name VARCHAR(255),
    deleted_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_task_phases_project_id ON task_phases (project_id) WHERE deleted_at IS NULL;

CREATE TABLE tasks (
    id               UUID         PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    status           VARCHAR(50)  NOT NULL,
    type             VARCHAR(32)  NOT NULL,
    progress         INT          NOT NULL DEFAULT 0,
    task_code        VARCHAR(50)  NOT NULL,
    assigned_user_id UUID,
    project_id       UUID         NOT NULL,
    phase_id         UUID         NOT NULL,
    deleted_at       TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_task_progress CHECK (progress >= 0 AND progress <= 100)
);

-- Unique task code per project among non-deleted tasks
CREATE UNIQUE INDEX uq_tasks_task_code ON tasks (project_id, task_code) WHERE deleted_at IS NULL;

-- Performance indexes for high-volume query paths
CREATE INDEX idx_tasks_project_id       ON tasks (project_id)       WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_assigned_user_id ON tasks (assigned_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_status           ON tasks (status)           WHERE deleted_at IS NULL;

CREATE TABLE task_comments (
    id         UUID  PRIMARY KEY,
    task_id    UUID  NOT NULL REFERENCES tasks(id),
    user_id    UUID  NOT NULL,
    content    TEXT  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_task_comments_task_id ON task_comments (task_id, created_at) WHERE deleted_at IS NULL;

CREATE TABLE task_participants (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id    UUID        NOT NULL REFERENCES tasks(id),
    user_id    UUID        NOT NULL,
    role       VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- A user can hold each role at most once per task at any time
CREATE UNIQUE INDEX task_participants_unique_idx ON task_participants (task_id, user_id, role) WHERE deleted_at IS NULL;
CREATE INDEX task_participants_task_id_idx ON task_participants (task_id);

CREATE TABLE task_planned_works (
    id            UUID        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id       UUID        NOT NULL REFERENCES tasks(id),
    user_id       UUID        NOT NULL,
    work_type     VARCHAR(32) NOT NULL,
    planned_hours INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_work_log_planned_hours CHECK (planned_hours >= 0)
);

-- One planned-work entry per work type per task
CREATE UNIQUE INDEX ux_task_planned_works_task_work_type ON task_planned_works (task_id, work_type);

CREATE TABLE task_booked_works (
    id           UUID        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id      UUID        NOT NULL REFERENCES tasks(id),
    user_id      UUID        NOT NULL,
    work_type    VARCHAR(32) NOT NULL,
    booked_hours INTEGER     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    deleted_at   TIMESTAMPTZ,

    CONSTRAINT chk_booked_work_hours CHECK (booked_hours > 0)
);

CREATE INDEX idx_task_booked_works_task_id ON task_booked_works (task_id) WHERE deleted_at IS NULL;

CREATE TABLE task_timelines (
    id               UUID        NOT NULL PRIMARY KEY,
    task_id          UUID        NOT NULL REFERENCES tasks(id),
    state            VARCHAR(32) NOT NULL,
    timestamp        TIMESTAMPTZ NOT NULL,
    set_by_user_id   UUID        NOT NULL,
    set_by_user_name VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL,
    deleted_at       TIMESTAMPTZ
);

-- One active entry per task + state combination
CREATE UNIQUE INDEX uq_task_timelines_task_state ON task_timelines (task_id, state) WHERE deleted_at IS NULL;
CREATE INDEX idx_task_timelines_task_id ON task_timelines (task_id) WHERE deleted_at IS NULL;

CREATE TABLE project_notification_templates (
    id               UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID         NOT NULL,
    event_type       VARCHAR(32)  NOT NULL,
    subject_template VARCHAR(255) NOT NULL,
    body_template    TEXT         NOT NULL,
    deleted_at       TIMESTAMPTZ
);

-- Only one active template per project + event type combination
CREATE UNIQUE INDEX uq_project_notification_templates_project_event
    ON project_notification_templates (project_id, event_type)
    WHERE deleted_at IS NULL;

CREATE TABLE task_attachments (
    id                  UUID         PRIMARY KEY,
    task_id             UUID         NOT NULL REFERENCES tasks(id),
    file_id             UUID         NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    uploaded_by_user_id UUID         NOT NULL,
    uploaded_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_task_attachments_task_id ON task_attachments (task_id);

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
