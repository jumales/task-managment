-- Timeline entries tracking planned and actual start/end dates per task state
CREATE TABLE task_timelines (
    id              UUID         NOT NULL PRIMARY KEY,
    task_id         UUID         NOT NULL REFERENCES tasks(id),
    state           VARCHAR(32)  NOT NULL,
    timestamp       TIMESTAMPTZ  NOT NULL,
    set_by_user_id  UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    deleted_at      TIMESTAMPTZ
);

-- One active entry per task + state combination
CREATE UNIQUE INDEX uq_task_timelines_task_state
    ON task_timelines (task_id, state)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_task_timelines_task_id ON task_timelines (task_id) WHERE deleted_at IS NULL;
