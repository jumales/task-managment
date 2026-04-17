# Fix: Outbox At-Least-Once Delivery Under Horizontal Scale

## Context

`OutboxPublisher.java` uses `SELECT FOR UPDATE SKIP LOCKED` — correct for preventing two instances
from picking up the same event simultaneously. However, **Kafka send and DB commit are not atomic**.

The race:
1. Instance A locks rows, sends to Kafka — Kafka ACKs
2. Instance A crashes (or DB blip) before `outboxRepository.saveAll(published)` commits
3. `published = false` rows remain → next poll re-sends the same events
4. Downstream consumers receive duplicates

The `SKIP LOCKED` mechanism protects against concurrent reads, not crash recovery.
Consumers must be idempotent for at-least-once delivery to be safe end-to-end.

## Current State

| Component | File | Idempotent? |
|---|---|---|
| `OutboxPublisher` | `task-service/.../outbox/OutboxPublisher.java:45` | N/A (publisher) |
| `SELECT FOR UPDATE SKIP LOCKED` | `task-service/.../repository/OutboxRepository.java:21` | Prevents concurrent pick-up |
| Consumer idempotency (some) | `ProcessedEventService` (verify location) | Unknown — audit required |

## Problem Details

```java
// OutboxPublisher.java:53-74 — Kafka send and DB commit are NOT in the same transaction
List<CompletableFuture<...>> futures = pending.stream()
        .map(event -> kafkaTemplate.send(...))  // Kafka ACK received here
        .toList();

// If crash here: Kafka has the message, DB still has published=false
outboxRepository.saveAll(published);  // commit — may never happen
```

## Plan

### Option A — Enforce idempotency on all consumers (preferred, lower risk)

Does not require Kafka broker changes. Works with any number of instances.

**Step 1**: Audit all `@KafkaListener` classes across all services:
- `audit-service` — `TaskEventConsumer.java`
- `notification-service` — `TaskEventNotificationConsumer.java`
- `search-service` — `TaskEventConsumer.java`
- `reporting-service` — any event consumer
- `file-service` — any event consumer

For each: verify `ProcessedEventService.markProcessed(eventId)` is called before processing.

**Step 2**: For any consumer that lacks idempotency:
```java
// Pattern to add at the top of each @KafkaListener method:
if (processedEventService.isAlreadyProcessed(event.getEventId())) {
    log.warn("Skipping duplicate event {}", event.getEventId());
    return;
}
// ... existing logic ...
processedEventService.markProcessed(event.getEventId());
```

**Step 3**: Write integration test per affected service:
- Publish same `OutboxEvent` ID twice
- Assert the downstream side effect (DB write, notification, index update) happens exactly once

### Option B — Kafka transactional producer (harder, requires broker config)

Not recommended for this setup — requires `replication.factor >= 3` for
`transaction.state.log`. Single-broker dev environment will reject transactional config.

If pursued in prod:
1. Add `spring.kafka.producer.transaction-id-prefix: task-outbox-` to `task-service/application.yml`
2. Wrap `publishPending()` body in `kafkaTemplate.executeInTransaction(ops -> { ... })`
3. Remove `@Transactional` from `publishPending()` — Kafka transaction manages atomicity

## Files to Modify

```
task-service/src/main/java/com/demo/task/outbox/OutboxPublisher.java         (verify, no change if Option A)
common/src/main/java/com/demo/common/service/ProcessedEventService.java       (verify exists, check thread-safety)
audit-service/src/main/java/com/demo/audit/consumer/TaskEventConsumer.java    (add/verify idempotency)
notification-service/.../consumer/TaskEventNotificationConsumer.java          (add/verify idempotency)
search-service/.../consumer/TaskEventConsumer.java                            (add/verify idempotency)
reporting-service/.../consumer/*.java                                         (add/verify idempotency)
file-service/.../consumer/*.java                                              (add/verify idempotency)
Each service *IT.java                                                         (add duplicate-event test)
```

## Verification

```
IT test flow:
1. Insert OutboxEvent with known UUID into outbox_events
2. Call outboxPublisher.publishPending() twice (simulating duplicate delivery)
3. Assert the processed_events table contains exactly 1 row for that UUID
4. Assert the downstream effect (e.g. audit row, search index doc) exists exactly once
```

## Execution Notes

- Start with audit — may find most consumers already idempotent
- `ProcessedEventService` likely uses a `processed_events` table with a unique index on `event_id`
- Thread-safety of `markProcessed`: must be idempotent under concurrent calls (unique constraint = safe)
- Recommended branch: `fix_outbox_consumer_idempotency`
