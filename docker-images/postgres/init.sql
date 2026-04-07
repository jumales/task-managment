-- ============================================================
-- Microservice Demo - PostgreSQL Initialization Script
-- Creates a dedicated database and user for each service.
-- Note: user-service was removed; users are now managed in Keycloak.
--
-- Note: GRANT ALL ON SCHEMA public is required for PostgreSQL 15+
-- which revoked the default CREATE privilege on the public schema.
-- ============================================================

-- ── Task Service ─────────────────────────────────────────────
CREATE USER task_svc WITH PASSWORD 'task_svc_pass';
CREATE DATABASE task_db OWNER task_svc;
GRANT ALL PRIVILEGES ON DATABASE task_db TO task_svc;
\c task_db
GRANT ALL ON SCHEMA public TO task_svc;

-- ── Audit Service ─────────────────────────────────────────────
CREATE USER audit_svc WITH PASSWORD 'audit_svc_pass';
CREATE DATABASE audit_db OWNER audit_svc;
GRANT ALL PRIVILEGES ON DATABASE audit_db TO audit_svc;
\c audit_db
GRANT ALL ON SCHEMA public TO audit_svc;

-- ── File Service ──────────────────────────────────────────────
CREATE USER file_svc WITH PASSWORD 'file_svc_pass';
CREATE DATABASE file_db OWNER file_svc;
GRANT ALL PRIVILEGES ON DATABASE file_db TO file_svc;
\c file_db
GRANT ALL ON SCHEMA public TO file_svc;

-- ── Notification Service ───────────────────────────────────────
CREATE USER notification_svc WITH PASSWORD 'notification_svc_pass';
CREATE DATABASE notification_db OWNER notification_svc;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO notification_svc;
\c notification_db
GRANT ALL ON SCHEMA public TO notification_svc;
