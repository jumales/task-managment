# Plan: Resolve All TODO Comments

## Context
The codebase has ~30 actionable TODO comments accumulated across multiple microservices (api-gateway, file-service, notification-service, audit-service, task-service, common). This plan fixes every addressable TODO and removes the comment after fixing. Complex architectural TODOs (async operations, `TaskChangedEvent` refactor, `aggregateType` enum) are explicitly deferred.

**Integration branch:** `todo_fixes` (base for all phase PRs, merges to `main` when all phases done)  
**Per-phase branches:** each phase gets its own branch off `todo_fixes` with a PR targeting `todo_fixes`

| Phase | Branch | PR |
|-------|--------|----|
| 1 — api-gateway | `fix/todos_phase1_api_gateway` | jumales/task-managment#62 ✅ |
| 2 — file-service | `fix/todos_phase2_file_service` | TBD |
| 3 — notification-service | `fix/todos_phase3_notification` | TBD |
| 4 — audit-service | `fix/todos_phase4_audit` | TBD |
| 5 — task-service repositories | `fix/todos_phase5_repositories` | TBD |
| 6 — task-service services | `fix/todos_phase6_services` | TBD |
| 7 — TaskService.java | `fix/todos_phase7_task_service` | TBD |
| 8 — controllers + outbox | `fix/todos_phase8_controllers` | TBD |
| 9 — model null constraints | `fix/todos_phase9_models` | TBD |

---

## Phase 1 — api-gateway (3 TODOs, zero-risk)
Files: `api-gateway/src/main/java/com/demo/gateway/config/`

1. **`CorsProperties.java:14`** — Add Lombok to `api-gateway/pom.xml`, add `@Getter @Setter` to class, delete manual getter/setter methods.
2. **`RateLimiterConfig.java:24`** — Change `.map(principal -> principal.getName())` → `.map(Principal::getName)`.
3. **`SecurityConfig.java:37`** — Delete the first duplicate Javadoc block; keep the second (more complete) one.

---

## Phase 2 — file-service (1 TODO)
File: `file-service/src/main/java/com/demo/file/service/FileService.java:184`

Extract nested content-type check into private method:
```java
private void validateContentType(MultipartFile file, List<String> allowedTypes) {
    if (allowedTypes.isEmpty()) return;
    String contentType = file.getContentType();
    if (contentType == null || !allowedTypes.contains(contentType)) {
        throw new IllegalArgumentException("Content type '" + contentType + "' is not allowed. Accepted: " + allowedTypes);
    }
}
```
Replace the nested `if` block with `validateContentType(file, config.getAllowedTypes())`.

---

## Phase 3 — notification-service (2 TODOs)
File: `notification-service/src/main/java/com/demo/notification/service/NotificationService.java`

- **Line 36** — Remove `userClient` field, constructor param, and assignment (`userClientHelper`, `emailService`, `taskServiceClient` are actively used — keep them).
- **Line 131** — Remove the TODO comment; code already uses early return correctly.

---

## Phase 4 — audit-service (3 TODOs, multi-file)
No DB migrations needed: `@Table` and `@Column` annotations preserve existing table/column names.

### 4a — Rename `AuditRecord` → `StatusAuditRecord`
- Rename `AuditRecord.java` → `StatusAuditRecord.java`, update class name; keep `@Table(name = "audit_records")`.
- Update references in: `AuditRepository.java`, `TaskEventConsumer.java`, `AuditController.java`, `AuditConsumerIT.java`, `e2e-tests/TaskStatusAuditFlowIT.java`.

### 4b — Rename `CommentAuditRecord.assignedUserId` → `commentCreatedByUserId`
- Add `@Column(name = "assigned_user_id")` to preserve DB column.
- Update builder call in `TaskEventConsumer.java` (wherever `.assignedUserId(...)` is called for comment records).

### 4c — Rename `PhaseAuditRecord.assignedUserId` → `changedByUserId`
- Add `@Column(name = "assigned_user_id")` to preserve DB column.
- Update builder call in `TaskEventConsumer.java` (wherever `.assignedUserId(...)` is called for phase records).

---

## Phase 5 — task-service: repositories (5 TODOs)

### Delete unused methods (confirmed via grep — zero callers):
| File | Method to delete |
|------|-----------------|
| `TaskBookedWorkRepository.java` | `existsByTaskId()` |
| `TaskPlannedWorkRepository.java` | `existsByTaskId()` (keep `existsByTaskIdAndWorkType`) |
| `TaskTimelineRepository.java` | `existsByTaskIdAndState()` |
| `TaskCommentRepository.java` | `softDeleteByTaskId()` AND `findByTaskIdIn()` — both unused. Also remove now-orphaned `@Modifying`/`@Query`/`@Param` imports. |

### Add Javadoc:
- **`TaskProjectRepository.java:15`** — remove the `//TODO: describe me this method` comment; the existing Javadoc on line 16 is already adequate.

---

## Phase 6 — task-service: services (5 TODOs)

### 6a — Remove unused `findRaw()` (`ProjectNotificationTemplateService.java:106`)
Delete the method and its Javadoc. No callers.

### 6b — Refactor `setAssignee()` in `TaskParticipantService.java:121`
Add to `TaskParticipantRepository.java`:
```java
/** Deletes all participants with the given role on the given task in one query. */
@Modifying
@Query("DELETE FROM TaskParticipant p WHERE p.taskId = :taskId AND p.role = :role")
void deleteByTaskIdAndRole(@Param("taskId") UUID taskId, @Param("role") TaskParticipantRole role);
```
Replace the stream-filter-deleteById loop in `setAssignee()` with `repository.deleteByTaskIdAndRole(taskId, TaskParticipantRole.ASSIGNEE)`.

### 6c — Bulk insert in `TaskTimelineService.java:98`
Replace two `save()` calls with:
```java
repository.saveAll(List.of(
    buildEntry(taskId, TimelineState.PLANNED_START, plannedStart, creatorId),
    buildEntry(taskId, TimelineState.PLANNED_END, plannedEnd, creatorId)
));
```

### 6d — `BusinessLogicException` for `TaskBookedWorkService.java:132`
1. Create `common/src/main/java/com/demo/common/exception/BusinessLogicException.java`:
   ```java
   public class BusinessLogicException extends RuntimeException {
       public BusinessLogicException(String message) { super(message); }
   }
   ```
2. Add handler in `GlobalExceptionHandler.java` → HTTP 422:
   ```java
   @ExceptionHandler(BusinessLogicException.class)
   @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
   public ErrorResponse handleBusinessLogic(BusinessLogicException ex, HttpServletRequest request) {
       return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
   }
   ```
3. Replace `throw new IllegalArgumentException(...)` at line 133 with `throw new BusinessLogicException(...)`.

### 6e — Make `toResponse()` private in `TaskProjectService` and `TaskPhaseService`
Currently called via method reference from `TaskService`. Fix by inlining the mapping in `TaskService`:

In `TaskService.fetchBaseData()` (lines 431-432): replace `projectService.toResponse(projectService.getOrThrow(...))` and `phaseService.toResponse(phaseService.getOrThrow(...))` with inline `new TaskProjectResponse(...)` / `new TaskPhaseResponse(...)` calls using entity fields.

In `TaskService.toResponseList()` (lines 454, 459): replace `projectService::toResponse` and `phaseService::toResponse` method references with equivalent lambdas.

Then change both `toResponse()` methods to `private` in their respective service files.

> **Before coding:** Read exact constructor signatures of `TaskProjectResponse` and `TaskPhaseResponse` from `common/src/main/java/com/demo/common/dto/`.

---

## Phase 7 — task-service: TaskService.java (5 TODOs)

### 7a — Remove stale enum comparison comments (lines 263, 349)
Just delete the `//TODO` comments. Java `==`/`!=` for enums is correct; no code change.

### 7b — Invert guard in `publishStatusChangedEvent()` (line 287)
```java
// before
if (newStatus != null && !newStatus.equals(oldStatus)) { outboxWriter.write(...); }

// after
if (newStatus == null || newStatus.equals(oldStatus)) return;
outboxWriter.write(...);
```

### 7c — Delete unused `toPageResponse(Task)` (line 475-485)
Confirmed by grep: zero callers in task-service. `toSummaryPageResponse()` at line 533 is the active one. Delete the method.

### 7d — Builder pattern for `TaskSummaryResponse` (line 513)
Add `@Builder` to `common/src/main/java/com/demo/common/dto/TaskSummaryResponse.java`.  
Replace the 12-argument constructor call in `toSummaryResponseList()` with a builder call using named fields.

---

## Phase 8 — task-service: controllers + outbox (3 TODOs)

### 8a — `TaskController.java:43`
Remove the `//TODO: needs to split into 3 methods` comment only — method is already clean.

### 8b — `ProjectNotificationTemplateController.java:38`
Remove the TODO comment. The `@PathVariable UUID projectId` must stay (path requires it). Remove `@Parameter(description = "Project UUID")` from `getPlaceholders()` to reduce noise.

### 8c — `ProjectNotificationTemplateController.java:49`
Rename method `getAll()` → `findByProject()`. Update Postman: `postman/task-service.postman_collection.json` — update the `"name"` of that request to `"Find templates by project"`.

### 8d — Remove `TOPIC` constant from `OutboxPublisher.java:28`
In `OutboxWriter.java`: change `OutboxPublisher.TOPIC` → `KafkaTopics.TASK_CHANGED` (import already present in that file or add it).  
In `OutboxPublisher.java`: delete `public static final String TOPIC = KafkaTopics.TASK_CHANGED;` and both TODO comments. The topic-per-action discussion TODO (#27) is deferred — just remove the comment.

---

## Phase 9 — Model null constraints (TODOs in Task.java and TaskComment.java)

### `Task.java` (lines 31, 34, 48)
Add `@Column(nullable = false)` to `status`, `type`, `taskCode` fields.  
Skip `assignedUserId` (line 39) — tasks may have no assignee, keep nullable.  
Remove all four `//TODO: cant be null` comments.

**Flyway migration** (check current highest version first via `ls task-service/src/main/resources/db/migration/`):
```sql
-- V{N}__add_not_null_to_task_status_type_code.sql
-- status was already NOT NULL in V1; add for type and task_code only if not already constrained.
ALTER TABLE tasks ALTER COLUMN type SET NOT NULL;
ALTER TABLE tasks ALTER COLUMN task_code SET NOT NULL;
```

### `TaskComment.java` (lines 30, 37)
Add `@Column(nullable = false)` to `taskId` and `content`.  
Skip `userId` (line 33) — explicitly nullable for legacy rows.  
Remove all three `//TODO: cant be null` comments.

**Flyway migration:**
```sql
-- V{N+1}__add_not_null_to_task_comment_task_id_content.sql
UPDATE task_comments SET content = '' WHERE content IS NULL;
ALTER TABLE task_comments ALTER COLUMN content SET NOT NULL;
-- task_id should already be NOT NULL from V1; verify before adding constraint
```

---

## Deferred (intentionally out of scope)
- `TaskService.java` lines 131, 192, 451–461, 498–506 — async operations
- `TaskProjectService.java:58` — async phase creation
- `FileService.java:147` — async MinIO deletion
- `common/event/TaskChangedEvent.java:30` — break into separate event classes
- `OutboxEvent.java:22` — `aggregateType` → enum (cascade across user-service)
- `OutboxPublisher.java:27` — topic-per-action architecture design

---

## Verification
```bash
mvn clean install -DskipTests=true          # full build must pass before push
mvn -pl audit-service test                   # AuditConsumerIT rename check
mvn -pl task-service test                    # TaskControllerIT, TaskBookedWorkControllerIT, etc.

# Confirm no addressed TODOs remain:
grep -rn "//TODO\|// TODO" api-gateway/src file-service/src notification-service/src audit-service/src task-service/src common/src
```
Expected: only deferred TODO lines remain (async, event refactor, aggregateType, topic design).
