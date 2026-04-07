CREATE TABLE task_attachments (
    id                  UUID         PRIMARY KEY,
    task_id             UUID         NOT NULL REFERENCES tasks(id),
    file_id             UUID         NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    uploaded_by_user_id UUID         NOT NULL,
    uploaded_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_task_attachments_task_id ON task_attachments(task_id);
