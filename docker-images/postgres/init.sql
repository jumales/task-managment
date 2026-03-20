-- ============================================================
-- Microservice Demo - PostgreSQL Initialization Script
-- Creates a dedicated database and user for each service
-- ============================================================

-- ── User Service ─────────────────────────────────────────────
CREATE USER user_svc WITH PASSWORD 'user_svc_pass';
CREATE DATABASE user_db OWNER user_svc;
GRANT ALL PRIVILEGES ON DATABASE user_db TO user_svc;

-- ── Task Service ─────────────────────────────────────────────
CREATE USER task_svc WITH PASSWORD 'task_svc_pass';
CREATE DATABASE task_db OWNER task_svc;
GRANT ALL PRIVILEGES ON DATABASE task_db TO task_svc;

-- ── Audit Service ─────────────────────────────────────────────
CREATE USER audit_svc WITH PASSWORD 'audit_svc_pass';
CREATE DATABASE audit_db OWNER audit_svc;
GRANT ALL PRIVILEGES ON DATABASE audit_db TO audit_svc;
