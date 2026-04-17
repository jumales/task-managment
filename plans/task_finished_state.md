# Plan: Task Finished State (RELEASED/REJECTED Lock + Filtering)

## Context
Tasks that reach the **RELEASED** or **REJECTED** phase are fully complete and must become immutable — no further edits of any kind. Tasks in the **DONE** phase are "dev finished" (semi-final) — field edits are locked but comments and booked hours are still allowed, and phase can still change (e.g. DONE → RELEASED). Additionally, a `completionStatus` filter must be exposed on `GET /tasks` for use in screens and reports.

---

## Phase Rules Summary

| Operation | DONE (dev_finished) | RELEASED / REJECTED (finished) |
|---|---|---|
| Edit task fields (`PUT /tasks/{id}`) | ❌ blocked | ❌ blocked |
| Change phase (`PATCH /tasks/{id}/phase`) | ✅ allowed | ❌ blocked |
| Add comments | ✅ allowed | ❌ blocked |
| Booked work (create/update/delete) | ✅ allowed | ❌ blocked |
| Planned dates / planned work | ❌ already blocked (PLANNING only) | ❌ already blocked |

---

## Implementation

### 1. Common — Add Constants to `TaskPhaseName` enum
**File:** `common/src/main/java/com/demo/common/dto/TaskPhaseName.java`

Add two static sets:
```java
public static final Set<TaskPhaseName> FINISHED_PHASES = Set.of(RELEASED, REJECTED);
public static final Set<TaskPhaseName> FIELD_LOCKED_PHASES = Set.of(DONE, RELEASED, REJECTED);
```

These are referenced by both `TaskService` and `TaskBookedWorkService` — no duplication.

### 2. Common — Add `TaskCompletionStatus` enum
**File:** `common/src/main/java/com/demo/common/dto/TaskCompletionStatus.java` *(new)*
```java
public enum TaskCompletionStatus {
    FINISHED,       // RELEASED or REJECTED phase
    DEV_FINISHED    // DONE phase
}
```

### 3. Backend Guards — `TaskService`
**File:** `task-service/.../service/TaskService.java`

Add two private guard helpers (reusing `phaseService.getOrThrow(task.getPhaseId()).getName()`):
```java
/** Throws if task is in RELEASED or REJECTED phase (fully finished). */
private void validateNotFinished(Task task) { ... }

/** Throws if task is in DONE, RELEASED, or REJECTED phase (fields locked). */
private void validateFieldsEditable(Task task) { ... }
```

Both throw `BusinessLogicException` (HTTP 422) — consistent with existing `validateNotPlanningPhase`.

Apply guards:
- `update()` → call `validateFieldsEditable(task)` before save
- `updatePhase()` → call `validateNotFinished(task)` before phase change logic
- `addComment()` → call `validateNotFinished(task)` before save

### 4. Backend Guards — `TaskBookedWorkService`
**File:** `task-service/.../service/TaskBookedWorkService.java`

Add same `validateNotFinished(task)` guard (using `TaskPhaseName.FINISHED_PHASES`).

Apply to:
- `create()` → add call after existing `validateNotPlanningPhase(task)`
- `update()` → add call after existing `validateNotPlanningPhase(task)`
- `delete()` → add call

### 5. Backend Filtering — Repository
**File:** `task-service/.../repository/TaskRepository.java`

Add JPQL query joining with phase table:
```java
@Query("SELECT t FROM Task t JOIN TaskPhase p ON t.phaseId = p.id WHERE p.name IN :phaseNames")
Page<Task> findByPhaseNameIn(Collection<TaskPhaseName> phaseNames, Pageable pageable);
```

### 6. Backend Filtering — `TaskService`
**File:** `task-service/.../service/TaskService.java`

Add:
```java
public PageResponse<TaskSummaryResponse> findByCompletionStatus(TaskCompletionStatus status, Pageable pageable) {
    Set<TaskPhaseName> phaseNames = status == TaskCompletionStatus.FINISHED
            ? TaskPhaseName.FINISHED_PHASES
            : Set.of(TaskPhaseName.DONE);
    return toSummaryPageResponse(repository.findByPhaseNameIn(phaseNames, pageable));
}
```

### 7. Backend Filtering — `TaskController`
**File:** `task-service/.../controller/TaskController.java`

Add `@RequestParam(required = false) TaskCompletionStatus completionStatus` to `getAll()`.  
If provided, call `findByCompletionStatus()`; otherwise use existing dispatch logic.

Update OpenAPI docs.

### 8. Frontend — `types.ts`
**File:** `web-client/src/api/types.ts`

- Add `'REJECTED'` to `TaskPhaseName` union (currently missing)
- Add `TaskCompletionStatus` type: `'FINISHED' | 'DEV_FINISHED'`

### 9. Frontend — Utility Helpers
**File:** `web-client/src/utils/taskUtils.ts` *(add helpers or extend existing utils)*

```typescript
export const isTaskFinished = (phase: TaskPhaseName) =>
    phase === 'RELEASED' || phase === 'REJECTED';

export const isTaskFieldsLocked = (phase: TaskPhaseName) =>
    phase === 'DONE' || isTaskFinished(phase);
```

### 10. Frontend — `TaskOverviewCard.tsx`
**File:** `web-client/src/components/taskDetail/TaskOverviewCard.tsx`

- Derive `finished = isTaskFinished(task.phase.name)` and `fieldsLocked = isTaskFieldsLocked(task.phase.name)`
- Disable/hide **Edit** button when `fieldsLocked`
- Show a read-only badge: "Finished" (RELEASED/REJECTED) or "Dev Finished" (DONE) to signal locked state
- Hide **Change Phase** button when `finished`

### 11. Frontend — Comments Section
**File:** wherever the comment form renders (likely `TaskDetailPage.tsx` or a comments component)

- Disable the add-comment form/button when `isTaskFinished(task.phase.name)`

### 12. Frontend — Booked Work Section
**File:** booked work component in task detail

- Disable add/edit/delete booked work actions when `isTaskFinished(task.phase.name)`

### 13. Frontend — `TasksPage.tsx`
**File:** `web-client/src/pages/TasksPage.tsx`

Add `completionStatus` filter to the filter bar — a Select with options:
- (blank) — All tasks
- Finished — RELEASED or REJECTED
- Dev Finished — DONE phase

Pass `completionStatus` as query param to `GET /api/v1/tasks`.

### 14. Update Postman Collection
**File:** `postman/task-service.postman_collection.json`

- Update `GET /api/v1/tasks` request with `completionStatus` query param example
- Add test assertions for 422 on blocked edit/comment/phase/booked-work operations

---

## Files to Create/Modify

| File | Action |
|---|---|
| `common/.../dto/TaskPhaseName.java` | Add `FINISHED_PHASES`, `FIELD_LOCKED_PHASES` static sets |
| `common/.../dto/TaskCompletionStatus.java` | Create new enum |
| `task-service/.../service/TaskService.java` | Add guards + `findByCompletionStatus()` |
| `task-service/.../service/TaskBookedWorkService.java` | Add `validateNotFinished` guards |
| `task-service/.../repository/TaskRepository.java` | Add `findByPhaseNameIn` JPQL query |
| `task-service/.../controller/TaskController.java` | Add `completionStatus` param, update OpenAPI |
| `web-client/src/api/types.ts` | Add `REJECTED`, `TaskCompletionStatus` |
| `web-client/src/utils/taskUtils.ts` | Add `isTaskFinished`, `isTaskFieldsLocked` |
| `web-client/src/components/taskDetail/TaskOverviewCard.tsx` | Disable edit/phase-change, show badge |
| `web-client/src/pages/TasksPage.tsx` | Add completionStatus filter |
| Comments & booked work frontend components | Disable forms when finished |
| `postman/task-service.postman_collection.json` | Update collection |

---

## Verification

1. **Guard tests (IT):** Move a task to RELEASED/REJECTED, then attempt `PUT /tasks/{id}`, `PATCH /tasks/{id}/phase`, `POST /tasks/{id}/comments`, booked work CRUD → all must return 422.
2. **DONE semi-lock test:** Move task to DONE, verify `PUT /tasks/{id}` returns 422 but comment and booked work succeed (200).
3. **Phase change guard:** From RELEASED, attempt `PATCH /tasks/{id}/phase` → 422. From DONE → RELEASED → 200.
4. **Filter test:** `GET /tasks?completionStatus=FINISHED` returns only RELEASED/REJECTED tasks; `DEV_FINISHED` returns only DONE phase tasks.
5. **Frontend:** Manually verify edit button is hidden for DONE/RELEASED/REJECTED; phase-change button hidden for RELEASED/REJECTED; comment/booked-work forms hidden for RELEASED/REJECTED.
6. **Build:** `mvn clean install -DskipTests=true` passes from project root.
