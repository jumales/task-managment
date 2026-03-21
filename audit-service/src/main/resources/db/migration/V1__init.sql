CREATE TABLE audit_records (
    id               UUID                     PRIMARY KEY,
    task_id          UUID                     NOT NULL,
    assigned_user_id UUID,
    from_status      VARCHAR(50),
    to_status        VARCHAR(50)              NOT NULL,
    changed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE comment_audit_records (
    id               UUID                     PRIMARY KEY,
    task_id          UUID                     NOT NULL,
    assigned_user_id UUID,
    comment_id       UUID                     NOT NULL,
    content          TEXT,
    added_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE phase_audit_records (
    id               UUID                     PRIMARY KEY,
    task_id          UUID                     NOT NULL,
    assigned_user_id UUID,
    from_phase_id    UUID,
    from_phase_name  VARCHAR(255),
    to_phase_id      UUID                     NOT NULL,
    to_phase_name    VARCHAR(255)             NOT NULL,
    changed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index for querying history by task
CREATE INDEX idx_audit_records_task_id         ON audit_records         (task_id);
CREATE INDEX idx_comment_audit_records_task_id ON comment_audit_records (task_id);
CREATE INDEX idx_phase_audit_records_task_id   ON phase_audit_records   (task_id);
