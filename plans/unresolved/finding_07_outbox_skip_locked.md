# Finding #7 — Fix outbox poller race condition with SELECT FOR UPDATE SKIP LOCKED

## Status
UNRESOLVED

## Severity
HIGH — duplicate Kafka event publishing when running multiple task-service instances

## Context
`OutboxPublisher.publishPending()` is scheduled every 5 seconds. With 2+ task-service instances,
both poll `outbox_events WHERE published = false` simultaneously, read the same rows, and each
publish the same events to Kafka. Downstream consumers (audit-service, notification-service,
search-service) have no idempotency guard — each duplicate produces a duplicate audit record,
duplicate email notification, and duplicate search index write.

## Root Cause
- `task-service/src/main/java/com/demo/task/outbox/OutboxPublisher.java:39` — calls `outboxRepository.findByPublishedFalse()`
- `task-service/src/main/java/com/demo/task/repository/OutboxRepository.java:11` — `List<OutboxEvent> findByPublishedFalse()` — no locking, no batch limit

## Files to Modify

### 1. `task-service/src/main/java/com/demo/task/repository/OutboxRepository.java`
Replace the derived query with a native `SELECT FOR UPDATE SKIP LOCKED`:
```java
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches up to 100 unpublished outbox events with a row-level lock.
     * SKIP LOCKED ensures concurrent pollers (multiple task-service instances)
     * each claim a distinct, non-overlapping batch — preventing double-publishing.
     */
    @Query(
        value = "SELECT * FROM outbox_events WHERE published = false ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 100",
        nativeQuery = true
    )
    List<OutboxEvent> findUnpublishedForUpdate();
}
```

### 2. `task-service/src/main/java/com/demo/task/outbox/OutboxPublisher.java`
Update the method call (one line):
```java
// Before:
List<OutboxEvent> pending = outboxRepository.findByPublishedFalse();

// After:
List<OutboxEvent> pending = outboxRepository.findUnpublishedForUpdate();
```

The existing `@Transactional` annotation on `publishPending()` is required for `FOR UPDATE` to work
— the lock is held until the transaction commits after `saveAll()`. No change needed there.

## Verification
1. Start two task-service instances
2. Create a task (triggers outbox event write)
3. Check audit-service — exactly ONE audit record should exist per task event
4. Check notification-service emails — exactly ONE notification per event
5. Query `outbox_events` — all rows should have `published = true` with no duplicates

## Notes
- `LIMIT 100` prevents a single instance from holding locks on the entire table during a large backlog
- `SKIP LOCKED` is supported by PostgreSQL 9.5+ (the project uses PG via docker-compose)
- The `@Transactional` on `publishPending()` already exists — the lock scope is correct
- No Flyway migration needed — this is a query change only
- If adding idempotency to consumers is also planned, do that as a separate finding
