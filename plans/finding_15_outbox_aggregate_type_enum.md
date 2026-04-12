# Finding #15 — Replace `aggregateType` string constant with an enum

## Status
UNRESOLVED

## Severity
MEDIUM — magic string `"Task"` hardcoded in two classes; existing TODO comment requests this change

## Context
`OutboxEvent.aggregateType` is a `String` field. The value `"Task"` is declared as a private
`static final` constant in both `TaskService` and `OutboxWriter`. There is an existing
`//TODO: aggregateType maybe enum?` comment on `OutboxEvent.java:22` requesting this conversion.
Per CLAUDE.md: "Never use string literals for values that have meaning."

## Root Cause
- `task-service/src/main/java/com/demo/task/model/OutboxEvent.java:22` — `String aggregateType` with TODO
- `task-service/src/main/java/com/demo/task/service/TaskService.java:60` — `private static final String AGGREGATE_TYPE = "Task"`
- `task-service/src/main/java/com/demo/task/outbox/OutboxWriter.java:22` — duplicate constant `AGGREGATE_TYPE = "Task"`

## Files to Modify

### 1. Create `task-service/src/main/java/com/demo/task/model/OutboxAggregateType.java`
```java
package com.demo.task.model;

/** Identifies the domain aggregate type stored in an outbox event. */
public enum OutboxAggregateType {
    TASK
}
```

### 2. `task-service/src/main/java/com/demo/task/model/OutboxEvent.java`
```java
// Before (line 22):
//TODO: aggregateType maybe enum?
private String aggregateType;  // e.g. "Task"

// After:
@Enumerated(EnumType.STRING)
private OutboxAggregateType aggregateType;
```
Remove the TODO comment per CLAUDE.md convention.

### 3. `task-service/src/main/java/com/demo/task/service/TaskService.java`
```java
// Remove (line 60):
private static final String AGGREGATE_TYPE = "Task";

// Update usage (line 506):
// Before:
.aggregateType(AGGREGATE_TYPE)
// After:
.aggregateType(OutboxAggregateType.TASK)
```

### 4. `task-service/src/main/java/com/demo/task/outbox/OutboxWriter.java`
```java
// Remove:
private static final String AGGREGATE_TYPE = "Task";

// Update usage:
.aggregateType(OutboxAggregateType.TASK)
```

## Database Migration
`@Enumerated(EnumType.STRING)` writes `"TASK"` (the enum name) to the column. If the `outbox_events`
table currently contains rows with `aggregate_type = 'Task'`, a Flyway migration is needed:

```sql
-- VN__migrate_outbox_aggregate_type.sql
UPDATE outbox_events SET aggregate_type = 'TASK' WHERE aggregate_type = 'Task';
```

Check the running DB before deciding:
```sql
SELECT DISTINCT aggregate_type FROM outbox_events;
```
If the table is empty or all rows are already `published = true` and processed, the migration
can be skipped (old rows are never re-read).

## Verification
1. Create a task — verify outbox event is written with `aggregate_type = 'TASK'`
2. Verify audit-service and notification-service still receive and process the event
3. `mvn clean install` must pass with no compilation errors across task-service

## Notes
- `OutboxEventType` enum already exists in the same package — follow the same pattern
- The enum could be moved to `common` if other services ever write to the outbox — for now, task-service only
