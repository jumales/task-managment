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
