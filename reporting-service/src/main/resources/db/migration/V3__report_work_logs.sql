-- Planned/booked work projection tables for the hours report.
-- Populated from TASK_CHANGED Kafka events (PLANNED_WORK_CREATED, BOOKED_WORK_*).

CREATE TABLE report_planned_works (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    project_id UUID,
    user_id UUID,
    work_type VARCHAR(64),
    planned_hours BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_report_planned_works_task    ON report_planned_works (task_id);
CREATE INDEX idx_report_planned_works_project ON report_planned_works (project_id);

CREATE TABLE report_booked_works (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    project_id UUID,
    user_id UUID,
    work_type VARCHAR(64),
    booked_hours BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_report_booked_works_task    ON report_booked_works (task_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_report_booked_works_project ON report_booked_works (project_id) WHERE deleted_at IS NULL;
