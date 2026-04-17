-- Dedicated schema for archived audit data.
-- Archive tables follow the naming convention {table}_{YYYYMM} where YYYYMM is derived from
-- the task's creation date. Tables are created dynamically by AuditArchiveService.
CREATE SCHEMA IF NOT EXISTS archive;
