CREATE TABLE notifications (
    id                UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id           UUID         NOT NULL,
    recipient_user_id UUID         NOT NULL,
    recipient_email   VARCHAR(255) NOT NULL,
    change_type       VARCHAR(32)  NOT NULL,
    subject           VARCHAR(255) NOT NULL,
    body              TEXT         NOT NULL,
    sent_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_task_id ON notifications (task_id);
