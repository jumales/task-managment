# Block Booked Work in PLANNING Phase

**Goal**: Prevent booked-work entries from being created or updated while the task is in the PLANNING phase — PLANNING is for estimation, not for booking actual hours.

**Branch**: `block_booked_work_in_planning`

---

## Business Rule

Booked work (actual hours) may not be created or updated on a task that is currently in the PLANNING phase. Attempts return `400 Bad Request`.

---

## Chunks

### Chunk 1 — Backend: phase guard on create and update
**What**:
- Inject `TaskPhaseService` into `TaskBookedWorkService`.
- In `create()`: after loading the task, resolve its phase name and throw `IllegalArgumentException` if it is `PLANNING`.
- In `update()`: apply the same phase guard before modifying the entry.
- Extract the guard into a private `validateNotPlanningPhase(Task task)` helper to avoid duplication.
- Update Postman collection description to document the restriction.

**Files**:
- `task-service/src/main/java/com/demo/task/service/TaskBookedWorkService.java`
- `postman/task-service.postman_collection.json`

**Commit**: `feat: block booked work creation and update in PLANNING phase`

---

### Chunk 2 — Frontend: hide add/edit form in PLANNING phase
**What**:
- Add `'PLANNING'` to the `TaskPhaseName` type (it can appear as the task's current phase in responses).
- Add `taskPhaseName: TaskPhaseName` prop to `TaskBookedWorkTab`.
- Hide the add/edit form (Divider + Space) when `taskPhaseName === 'PLANNING'`.
- Pass `taskPhaseName={data.task.phase.name}` from `TaskDetailPage`.

**Files**:
- `web-client/src/api/types.ts`
- `web-client/src/components/taskDetail/TaskBookedWorkTab.tsx`
- `web-client/src/pages/TaskDetailPage.tsx`

**Commit**: `feat: hide booked work form when task is in PLANNING phase`

---

### Chunk 3 — Integration tests
**What**:
- Add IT case: create booked work on a task in PLANNING phase → `400 Bad Request`.
- Add IT case: update booked work on a task in PLANNING phase → `400 Bad Request`.
- Existing create/update tests (task not in PLANNING) continue to pass unchanged.

**Files**:
- `task-service/src/test/java/com/demo/task/TaskBookedWorkControllerIT.java`

**Commit**: `test: cover booked work blocked in PLANNING phase`
