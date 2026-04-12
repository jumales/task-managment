-- Migrate outbox_events.aggregate_type from 'Task' (old String constant) to 'TASK'
-- (uppercase enum name written by @Enumerated(EnumType.STRING) on OutboxAggregateType).
-- Safe to run repeatedly: WHERE clause is a no-op when all rows are already 'TASK'.
UPDATE outbox_events SET aggregate_type = 'TASK' WHERE aggregate_type = 'Task';
