-- ============================================================
-- reporting-service initial schema
-- Local read model built from Kafka task events.
-- Tables for task projection and hours aggregation are added
-- in follow-up migrations as the feature grows.
-- ============================================================

CREATE TABLE reporting_schema_marker (
    id           UUID        PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE reporting_schema_marker IS
    'Placeholder table so Flyway has an initial migration. Real projection tables are added in later versions.';
