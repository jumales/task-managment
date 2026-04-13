# Finding #13 — Disable Kafka auto-commit; switch to manual ACK in all consumer services

## Status
RESOLVED

## Severity
MEDIUM — message loss on consumer crash (offset committed before processing completes)

## Context
All 4 consumer services use Kafka's default `enable.auto.commit=true`. Spring Kafka's container
commits the offset on a timer regardless of whether the listener method succeeded. If a consumer
crashes after the periodic commit but before the DB write completes, the message is silently
dropped — no retry, no dead letter queue.

Affected services: `audit-service`, `notification-service`, `search-service`, `reporting-service`

## Files to Modify

### application.yml — all 4 consumer services
Add to each:
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false
    listener:
      ack-mode: MANUAL_IMMEDIATE
```

Files:
- `audit-service/src/main/resources/application.yml`
- `notification-service/src/main/resources/application.yml`
- `search-service/src/main/resources/application.yml`
- `reporting-service/src/main/resources/application.yml`

### @KafkaListener methods — all 4 consumer services
Add `Acknowledgment ack` parameter; call `ack.acknowledge()` after successful processing.

**Pattern:**
```java
@KafkaListener(topics = KafkaTopics.TASK_EVENTS, groupId = "audit-group")
public void consume(TaskChangedEvent event, Acknowledgment ack) {
    try {
        // existing processing logic
        auditRepository.save(toAuditRecord(event));
        ack.acknowledge();   // ← commit only after successful DB write
    } catch (Exception e) {
        log.error("Failed to process event {}: {}", event, e.getMessage(), e);
        // Do NOT call ack.acknowledge() — offset not committed, message will be retried
    }
}
```

Find listener classes:
- `audit-service/src/main/java/com/demo/audit/consumer/` — `TaskEventConsumer.java` or similar
- `notification-service/src/main/java/com/demo/notification/consumer/` — `TaskEventConsumer.java`
- `search-service/src/main/java/com/demo/search/consumer/` — `TaskEventConsumer.java`
- `reporting-service/src/main/java/com/demo/reporting/consumer/` — `TaskEventConsumer.java`

## Verification
1. Start audit-service, produce a task event
2. Kill audit-service before the DB write completes (e.g., after adding a `Thread.sleep`)
3. Restart audit-service — it must re-consume and process the event (offset was not committed)
4. Confirm exactly one audit record per event (no duplicates, no missing records)

## Notes
- `MANUAL_IMMEDIATE` commits the offset synchronously on `ack.acknowledge()` — safest for OLTP workloads
- With `enable-auto-commit: false`, Spring Kafka manages offset commits via the `AcknowledgingMessageListener` contract
- If processing consistently fails (e.g., deserialization error), the consumer will retry indefinitely — consider adding a dead letter topic or retry limit as a follow-up
- `search-service` and `reporting-service` use `StringDeserializer` (not JSON) — their listener signature may differ; adjust accordingly
