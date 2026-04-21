# Outbox At-Least-Once → Consumer-Side Idempotency (Option A)

## TL;DR

`OutboxPublisher` uses `SELECT ... FOR UPDATE SKIP LOCKED` to let multiple task-service
instances cooperate on draining the outbox table. That lock protects against two instances
picking up the same row at the same instant — but it does **not** protect against crash
recovery. If an instance sends an event to Kafka, Kafka ACKs, and then the instance dies
before committing `published = true` on the outbox row, the next poll re-sends the same
event. Consumers receive duplicates.

Option A fixes this at the consumer boundary: every consumer whose side effects are not
naturally idempotent guards its work with `ProcessedEventService.markProcessed(eventId, group)`.
The dedup table has a unique constraint on `(event_id, consumer_group)` — a duplicate
insert throws `DataIntegrityViolationException`, the service catches it and returns `false`,
and the consumer short-circuits.

---

## Why `SKIP LOCKED` is not enough

```java
// OutboxPublisher (task-service) — simplified
@Transactional
public void publishPending() {
    List<OutboxEvent> pending = outboxRepository.findUnpublishedWithLock(pageSize);
    List<OutboxEvent> published = pending.stream()
            .peek(e -> kafkaTemplate.send(TOPIC, key, e))   // Kafka ACK received
            .peek(e -> e.setPublished(true))
            .toList();
    outboxRepository.saveAll(published);                    // commit — may never happen
}
```

Failure mode:

1. Instance A locks rows with `SKIP LOCKED`, sends to Kafka, Kafka ACKs.
2. Instance A crashes (or the DB connection drops) **before** the `saveAll` commits.
3. Lock is released by the DB on disconnect. Rows still have `published = false`.
4. Next poll (same instance or another) picks the rows up and re-sends them.
5. Downstream consumer sees the event twice.

`SKIP LOCKED` solves concurrent pick-up. It does not solve crash-recovery duplicates.
The only clean fixes are:

- **Option A** — enforce idempotency on consumers (this PR)
- **Option B** — Kafka transactional producer (harder: requires `replication.factor >= 3`
  on `transaction.state.log`, not viable for this single-broker dev setup)

---

## The two tiers of idempotency

Not every consumer needs a dedup table. When the side effect of processing an event twice
converges to the same final state, the operation is **naturally idempotent** — adding a
dedup table would be dead weight. When the side effect is observable to a user or bumps
counters, we need **explicit dedup**.

| Consumer | Side effect | Tier |
|---|---|---|
| `audit/TaskEventConsumer` | `INSERT` into audit tables — duplicate → duplicate row | explicit |
| `audit/TaskLifecycleConsumer` | `archiveService.archiveTask` — bulk moves rows | explicit |
| `notification/TaskEventNotificationConsumer` | email + WebSocket push | explicit |
| `reporting/TaskEventProjectionConsumer` | upsert + **WebSocket push to user** | explicit |
| `reporting/TaskChangedProjectionConsumer` | upsert + **WebSocket push to user** | explicit |
| `search/TaskEventConsumer` | `index(docId)` — upsert by ID | **natural** |
| `search/UserEventConsumer` | `index(docId)` — upsert by ID | **natural** |
| `file/TaskArchivedConsumer` | `softDeleteById` — no-op once `deleted_at` is set | **natural** |

For the three "natural" consumers we added a Javadoc block explaining why no guard is
needed. That beats adding an unused dedup table and an unused migration — and it
documents the reasoning so a future reader doesn't "fix" the "missing" idempotency.

---

## The `ProcessedEventService` pattern

```java
@Service
public class ProcessedEventService {
    private final ProcessedEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessed(UUID eventId, String consumerGroup) {
        try {
            repository.save(ProcessedKafkaEvent.builder()
                    .eventId(eventId).consumerGroup(consumerGroup)
                    .processedAt(Instant.now()).build());
            return true;
        } catch (DataIntegrityViolationException e) {
            return false; // unique index caught a duplicate
        }
    }
}
```

Two details that are not obvious:

- **`REQUIRES_NEW`** — the dedup row is committed *before* any real work runs. If the
  consumer's own transaction later rolls back, the dedup row still lives on and the event
  is treated as processed. That is intentional: replays of a failed event go straight to
  the DLT via `DefaultErrorHandler`, not back into the consumer. If `REQUIRED` propagation
  were used instead, a rollback in the main work would also roll back the dedup row, and
  every subsequent re-delivery would re-run the work.
- **Unique index, not `findBy...`** — checking existence first and then inserting is a
  TOCTOU race. Under concurrent delivery, two threads can both see "not processed", both
  decide to insert, and both succeed. The `(event_id, consumer_group)` unique constraint
  makes the INSERT atomic: exactly one thread wins, the other catches
  `DataIntegrityViolationException`.

Schema (`V{n}__add_processed_kafka_events.sql`):

```sql
CREATE TABLE processed_kafka_events (
    id             UUID                     NOT NULL PRIMARY KEY,
    event_id       UUID                     NOT NULL,
    consumer_group VARCHAR(255)             NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX uidx_processed_kafka_events_event_consumer
    ON processed_kafka_events (event_id, consumer_group);
```

`consumer_group` is part of the unique key because two separate services share the same
`event_id` but have independent processing pipelines — audit-service processing an event
must not block notification-service from processing it too.

---

## Consumer pattern

Every guarded consumer owns its consumer-group constant locally (it has to match the
`@KafkaListener(groupId = ...)` value, and `@KafkaListener` attributes must be
compile-time constants):

```java
@Component
public class TaskEventConsumer {
    private static final String CONSUMER_GROUP = "audit-group";

    @KafkaListener(topics = KafkaTopics.TASK_CHANGED, groupId = CONSUMER_GROUP, concurrency = "12")
    public void consume(TaskChangedEvent event) {
        if (!processedEventService.markProcessed(event.getEventId(), CONSUMER_GROUP)) {
            log.info("Duplicate event {} — skipping", event.getEventId());
            return;
        }
        // ... real work ...
    }
}
```

We moved the `CONSUMER_GROUP` constant from `ProcessedEventService` to the individual
consumers because a single service (audit-service) has two consumers on two different
consumer groups (`audit-group`, `audit-lifecycle-group`). A shared constant only worked
when there was exactly one consumer per service.

For events that might legitimately be missing an `eventId` (tests, backfill):

```java
if (event.getEventId() != null
        && !processedEventService.markProcessed(event.getEventId(), CONSUMER_GROUP)) {
    return;
}
```

---

## Verification

Each explicit-dedup consumer gets a duplicate-delivery IT that sends the same event object
twice and asserts the side effect ran exactly once.

```java
@Test
void duplicateBookedWorkEvent_upsertsOnce() throws Exception {
    UUID bookedId = UUID.randomUUID();
    TaskChangedEvent event = TaskChangedEvent.bookedWorkCreated(TASK_ID, PROJECT_ID, "Hours",
            bookedId, USER_ID, WorkType.DEVELOPMENT, BigInteger.valueOf(6));

    publish(event);
    publish(event); // same object = same eventId

    await().atMost(20, SECONDS).untilAsserted(() -> {
        assertThat(bookedRepository.findAll()).hasSize(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    });
}
```

The assertion on `processedEventRepository.count() == 1` is the critical one — it proves
the dedup row was written exactly once (second insert hit the unique constraint) and
that the consumer short-circuited before the second upsert.

---

## What is NOT addressed here

- **Outbox cleanup** — `processed_kafka_events` rows grow unbounded. A future PR should
  add a scheduler that deletes rows older than some retention (e.g. 7 days). Kafka's own
  retention makes very old duplicates implausible, but the table will bloat over time.
- **Option B (Kafka transactional producer)** — deferred; needs broker config changes we
  cannot make on the single-broker dev environment.
- **Consumer groups per service instance** — if a single service instance runs multiple
  instances (horizontal scale), they share the same `consumer_group` string, so the dedup
  row collision across instances is exactly what we want (first instance commits, second
  skips). Confirmed by the `REQUIRES_NEW` + unique-constraint combo.
