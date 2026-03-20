# New Kafka Event

Add a new change type to the unified `TaskChangedEvent` and wire it end-to-end: outbox → Kafka → audit-service.

## Required input
The user must provide:
- **Change type name** (e.g. `LABEL_CHANGED`) — becomes a value in `TaskChangeType` enum
- **Payload fields** — the data specific to this event (e.g. `fromLabel`, `toLabel`)
- **Description** of when the event is fired

## Steps

### 1. Enum
Add the new value to `common/src/main/java/com/demo/common/event/TaskChangeType.java`.

### 2. Event class
In `TaskChangedEvent.java`:
- Add the new payload fields with a comment `// Populated when changeType == <TYPE>`
- Add a static factory method following the existing pattern:
  ```java
  public static TaskChangedEvent <camelCaseName>(UUID taskId, UUID assignedUserId, ...) { ... }
  ```
- Update the class-level Javadoc `<ul>` to include the new type

### 3. task-service: fire the event
In the relevant `*Service.java`:
- Detect the change (compare old vs new value)
- Call `writeToOutbox(TaskChangedEvent.<factory>(...))`  within the existing `@Transactional` method

### 4. audit-service: audit record
Create `audit-service/src/main/java/com/demo/audit/model/<Type>AuditRecord.java`:
- Standard `@Entity` without soft delete (audit records are immutable)
- Fields: `id`, `taskId`, `assignedUserId`, event-specific payload fields, `changedAt`, `recordedAt`
- Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`

Create `audit-service/src/main/java/com/demo/audit/repository/<Type>AuditRepository.java`:
- `findByTaskIdOrderByChangedAtAsc(UUID taskId)`

### 5. audit-service: consumer
In `TaskEventConsumer.java`:
- Add the new `case <TYPE> -> persist<Type>Change(event);` to the switch
- Add the `private void persist<Type>Change(TaskChangedEvent event)` method
- Inject the new repository via constructor

### 6. audit-service: controller
In `AuditController.java`:
- Add `GET /api/audit/tasks/{taskId}/<plural>` endpoint
- Add `@Operation` and `@Parameter` documentation

### 7. Tests
In `task-service`:
- Add a test asserting the outbox event is written when the change occurs
- Add a test asserting the outbox event is published to Kafka

In `audit-service/AuditConsumerIT.java`:
- Add tests: single event persisted correctly, multiple events in order, routing to correct store
