# Add PLANNING Phase with Planned-Date Business Logic

**Goal**: Introduce a PLANNING phase that is automatically assigned on task creation, is a one-way gate (tasks can never return to it once they leave), and is the only phase in which planned dates (PLANNED_START / PLANNED_END) may be changed.

**Branch**: `add_planning_phase`

---

## Business Rules

| Rule | Description |
|------|-------------|
| **Auto-assigned on create** | Every new task starts in the project's PLANNING phase; `phaseId` in `TaskRequest` is ignored during creation |
| **One-way exit** | Once a task's phase changes away from PLANNING, updating the phase back to PLANNING is rejected with `IllegalArgumentException` |
| **Planned-dates lock** | `PLANNED_START` / `PLANNED_END` timeline entries may only be changed when the task is currently in PLANNING phase |
| **New endpoint** | `PUT /api/v1/tasks/{id}/planned-dates` — accepts both dates atomically; enforces the phase lock and start < end ordering |
| **Booked work blocked** | Booked-work entries (actual hours) may not be created or updated while the task is in PLANNING phase; attempts return `400 Bad Request` |
| **Planned work immutable** | Planned-work entries (estimated hours) are immutable once created — no update or delete operations are allowed; only `POST` and `GET` are exposed |

---

## Chunks

### Chunk 1 — Flyway migration
**What**: Insert one PLANNING phase row for every existing project. The code change (Chunk 4) will always look up this row by name on creation, so existing tasks are unaffected.
**Files**:
- `task-service/src/main/resources/db/migration/V14__add_planning_phase_to_projects.sql`

```sql
INSERT INTO task_phases (id, project_id, name, description, custom_name, deleted_at)
SELECT gen_random_uuid(), id, 'PLANNING', NULL, NULL, NULL
FROM task_projects
WHERE deleted_at IS NULL;
```
**Commit**: `feat: V14 — add PLANNING phase row for all existing projects`

---

### Chunk 2 — common: enum value + PlannedDatesRequest DTO
**What**:
1. Add `PLANNING` as the first value in `TaskPhaseName` (it is the initial/entry phase).
2. Add `PlannedDatesRequest` DTO to `common` (used by the new endpoint and by `TaskService`).

**Files**:
- `common/src/main/java/com/demo/common/dto/TaskPhaseName.java` — prepend `PLANNING`
- `common/src/main/java/com/demo/common/dto/PlannedDatesRequest.java` — new DTO with `plannedStart: Instant`, `plannedEnd: Instant`

**Commit**: `feat: add PLANNING to TaskPhaseName enum and PlannedDatesRequest DTO`

---

### Chunk 3 — Repository + PhaseService: findPlanningPhaseOrThrow
**What**:
1. Add `findByProjectIdAndName(UUID projectId, TaskPhaseName name)` to `TaskPhaseRepository` (returns `Optional<TaskPhase>`).
2. Add package-private `findPlanningPhaseOrThrow(UUID projectId)` to `TaskPhaseService` — finds the PLANNING phase for the project or throws `ResourceNotFoundException`.
3. Ensure `createDefaultPhasesForProject` still works (PLANNING is now in the enum so it will be included automatically).

**Files**:
- `task-service/src/main/java/com/demo/task/repository/TaskPhaseRepository.java`
- `task-service/src/main/java/com/demo/task/service/TaskPhaseService.java`

**Commit**: `feat: add findPlanningPhaseOrThrow to TaskPhaseService`

---

### Chunk 4 — TaskService: enforce PLANNING on create + one-way gate on update
**What**:
1. In `create()`: replace `resolvePhaseId(request.getPhaseId(), project)` with `phaseService.findPlanningPhaseOrThrow(project.getId()).getId()`. The `phaseId` field in `TaskRequest` is silently ignored during creation.
2. In `update()`: after resolving the new phase, check — if the **current** phase is not PLANNING **and** the **new** phase IS PLANNING, throw `IllegalArgumentException("Cannot return a task to the PLANNING phase")`.
3. Remove the now-unused `resolvePhaseId()` private method.

**Files**:
- `task-service/src/main/java/com/demo/task/service/TaskService.java`

**Commit**: `feat: force PLANNING phase on task creation and guard against returning to PLANNING`

---

### Chunk 5 — TaskTimelineService: updatePlannedDates
**What**: Add package-private `updatePlannedDates(UUID taskId, Instant plannedStart, Instant plannedEnd, UUID updatingUserId)` to `TaskTimelineService`.
- Validates `plannedStart.isBefore(plannedEnd)` (atomic check; both dates provided together).
- Upserts PLANNED_START via the existing `setState()` path (or calls `repository.save()` directly to avoid double-validation).
- Upserts PLANNED_END the same way.

Since `setState()` validates ordering against the *stored* counterpart (which may be stale), the two-date upsert should validate atomically first, then update both entries. Internally:
1. Validate `plannedStart < plannedEnd`.
2. Find or build PLANNED_START entry; set timestamp + setByUserId.
3. Find or build PLANNED_END entry; set timestamp + setByUserId.
4. Save both in one transaction.

**Files**:
- `task-service/src/main/java/com/demo/task/service/TaskTimelineService.java`

**Commit**: `feat: add updatePlannedDates to TaskTimelineService`

---

### Chunk 6 — TaskService: updatePlannedDates with phase guard
**What**: Add public `updatePlannedDates(UUID taskId, UUID updatingUserId, PlannedDatesRequest request)` to `TaskService`.
1. Load task (`getOrThrow`).
2. Resolve phase name: `phaseService.getOrThrow(task.getPhaseId()).getName()`.
3. If phase is not `TaskPhaseName.PLANNING` → throw `IllegalArgumentException("Planned dates can only be changed while the task is in the PLANNING phase")`.
4. Delegate to `timelineService.updatePlannedDates(taskId, request.getPlannedStart(), request.getPlannedEnd(), updatingUserId)`.
5. Return updated `TaskFullResponse` (or lightweight confirmation — see controller chunk).

**Files**:
- `task-service/src/main/java/com/demo/task/service/TaskService.java`

**Commit**: `feat: add updatePlannedDates to TaskService with PLANNING phase guard`

---

### Chunk 7 — Controller: PUT /tasks/{id}/planned-dates + Postman
**What**:
1. Add `PUT /api/v1/tasks/{id}/planned-dates` to `TaskController`.
   - Extracts `updatingUserId` from `Authentication` via existing `resolveUserId()`.
   - Calls `service.updatePlannedDates(id, updatingUserId, request)`.
   - Returns `200 OK` with `TaskFullResponse`.
   - OpenAPI: `@Operation`, `@ApiResponses` (200, 400 phase-lock violation, 404).
2. Update `postman/task-service.postman_collection.json` — add "Update Planned Dates" request under the Tasks folder.

**Files**:
- `task-service/src/main/java/com/demo/task/controller/TaskController.java`
- `postman/task-service.postman_collection.json`

**Commit**: `feat: add PUT /tasks/{id}/planned-dates endpoint and update Postman collection`

---

### Chunk 8 — Integration tests
**What**: Cover the new business rules in the existing IT class.
1. **Create task → phase is PLANNING**: create a task without specifying `phaseId`; assert response phase name is `PLANNING`.
2. **Create task → phaseId ignored**: create with an explicit non-PLANNING phaseId; assert response phase name is still `PLANNING`.
3. **Update phase away from PLANNING → succeeds**: move task from PLANNING to TODO; assert 200.
4. **Update phase back to PLANNING → rejected**: attempt to set phase back to PLANNING after moving away; assert 400.
5. **updatePlannedDates in PLANNING → succeeds**: assert both PLANNED_START and PLANNED_END are updated.
6. **updatePlannedDates outside PLANNING → rejected**: move task out of PLANNING, then try to update planned dates; assert 400.
7. **updatePlannedDates with invalid ordering (start >= end) → rejected**: assert 400.

**Files**:
- `task-service/src/test/java/com/demo/task/TaskIT.java` (or relevant IT class)

**Commit**: `test: cover PLANNING phase creation, one-way gate, and planned-date lock`

---

### Chunk 9 — Frontend
**What**:
1. **Create task form / wizard**: remove the phase selector entirely; do not include `phaseId` in the create request body.
2. **Task detail — planned dates**: in the timeline section (or overview card), show the "Edit planned dates" button/form only when `task.phase.name === 'PLANNING'`. Outside PLANNING, show the dates as read-only with a lock icon or tooltip ("Planned dates are locked after PLANNING").
3. **Task detail — phase selector**: The existing phase dropdown is already free to move forward; the guard is enforced server-side. No frontend-side restriction needed beyond not showing PLANNING as an option to jump back to (optional UX improvement).
4. Call the new `PUT /api/v1/tasks/{id}/planned-dates` endpoint from the frontend when saving planned dates.

**Files**:
- `web-client/src/pages/TasksPage.js` or create task component — remove phase field
- `web-client/src/components/taskDetail/TaskTimelineTab.js` (or `TaskOverviewCard.js`) — conditional edit button
- `web-client/src/api/` — add `updatePlannedDates(taskId, payload)` call

**Commit**: `feat: remove phase picker from task creation, lock planned-date editing outside PLANNING`
