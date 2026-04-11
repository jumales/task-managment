# Task List Active-Only Default + Report Open/Finished Tabs

**Goal**: Make the task list show only active tasks by default (exclude RELEASED and REJECTED phases), add a filter to optionally see all or finished-only tasks, and split the Reports page "My Tasks" into "My Open Tasks" and "My Finished Tasks" tabs.

**Branch**: `task_list_active_filter`

---

## Context

Currently `GET /tasks` returns all tasks including RELEASED and REJECTED ones. The task list has a "Completion" dropdown that filters *to* FINISHED or DEV_FINISHED, but there is no way to exclude finished tasks from the default view. The Reports page "My Tasks" tabs also show all tasks assigned to the user regardless of their phase.

---

## Chunks

### Chunk 1 — task-service backend: exclude finished from default query

**What**:
- Add `@RequestParam(defaultValue = "false") boolean includeFinished` to `GET /tasks` in `TaskController`.
- In `TaskService`, update the general `findAll(...)` method to accept `includeFinished`. When `false`, resolve RELEASED+REJECTED phase IDs via `phaseService.findIdsByNameIn(TaskPhaseName.FINISHED_PHASES)` and pass them as an exclusion list to the repository.
- Add `Page<Task> findByPhaseIdNotInAndOptionalFilters(...)` query to `TaskRepository` — use a `@Query` JPQL method. The existing `findByCompletionStatus` path is unchanged.
- Update Postman collection (`postman/task-service.postman_collection.json`): add `includeFinished` param to the list-tasks request.

**Flyway migration** (`task-service V3__add_phase_id_index.sql`):
```sql
-- Supports phase-based filtering (findByPhaseIdIn / findByPhaseIdNotIn)
CREATE INDEX idx_tasks_phase_id ON tasks (phase_id) WHERE deleted_at IS NULL;
```

**Files**:
- `task-service/src/main/resources/db/migration/V3__add_phase_id_index.sql`
- `task-service/src/main/java/com/demo/task/controller/TaskController.java`
- `task-service/src/main/java/com/demo/task/service/TaskService.java`
- `task-service/src/main/java/com/demo/task/repository/TaskRepository.java`
- `postman/task-service.postman_collection.json`

**Commit**: `feat(task-service): exclude RELEASED/REJECTED phases from default task list`

---

### Chunk 2 — reporting-service backend: filter my-tasks by open vs finished

**What**:
- Add `phaseName` field to `MyTaskResponse` (already present on `ReportTask` entity as a String).
- Add `@RequestParam(defaultValue = "false") boolean finished` to `GET /api/v1/reports/my-tasks` in `ReportingController`.
- Add two new repository methods to `ReportTaskRepository`:
  - `findByAssignedUserIdAndPhaseNameNotIn(UUID, Collection<String>, Sort)` — open tasks (excludes "RELEASED", "REJECTED")
  - `findByAssignedUserIdAndPhaseNameIn(UUID, Collection<String>, Sort)` — finished tasks
- Update `MyTasksService.findMyTasks(userId, days, finished)`:
  - `finished=false` (default): call `findByAssignedUserIdAndPhaseNameNotIn` (with `RELEASED`, `REJECTED`)
  - `finished=true`: call `findByAssignedUserIdAndPhaseNameIn`
  - `days` filter still applies when non-null (combine via extra query methods or JPQL `@Query`)
- Define the two finished phase name constants directly in `MyTasksService` (e.g. `private static final Set<String> FINISHED_PHASE_NAMES = Set.of("RELEASED", "REJECTED")`).

**Flyway migration** (`reporting-service V4__report_tasks_phase_name_index.sql`):
```sql
-- Improves open/finished task queries that filter by (assigned_user_id, phase_name)
DROP INDEX idx_report_tasks_assigned_user;
CREATE INDEX idx_report_tasks_assigned_user
    ON report_tasks (assigned_user_id, phase_name, updated_at DESC)
    WHERE deleted_at IS NULL;
```

**Files**:
- `reporting-service/src/main/resources/db/migration/V4__report_tasks_phase_name_index.sql`
- `reporting-service/src/main/java/com/demo/reporting/dto/MyTaskResponse.java`
- `reporting-service/src/main/java/com/demo/reporting/controller/ReportingController.java`
- `reporting-service/src/main/java/com/demo/reporting/service/MyTasksService.java`
- `reporting-service/src/main/java/com/demo/reporting/repository/ReportTaskRepository.java`

**Commit**: `feat(reporting-service): add finished flag to my-tasks endpoint and expose phaseName`

---

### Chunk 3 — frontend: active-only default + report tabs

**What**:
- **`taskApi.ts`**: add `includeFinished?: boolean` to `getTasks()` params; pass as query string when present.
- **`TasksPage.tsx`**: replace the current two-option completion dropdown with a three-option Select:
  - Cleared / no value → `includeFinished=false` (active only, default)
  - `"FINISHED"` → `completionStatus=FINISHED` (only RELEASED/REJECTED)
  - `"ALL"` → `includeFinished=true` (all tasks)
  - Change placeholder from "Completion" to "Show" for clarity.
  - `loadTasks` default call passes `includeFinished=false`.
- **`reportingApi.ts`**: add `getMyFinishedTasks(): Promise<MyTaskReport[]>` → `GET /api/v1/reports/my-tasks?finished=true`; update `getMyTasks()` to pass `?finished=false`.
- **`ReportsPage.tsx`**:
  - Rename "My Tasks" tab → "My Open Tasks" (passes `finished=false` implicitly).
  - Rename "My Tasks (last 5 d)" → "My Open Tasks (last 5 d)".
  - Rename "My Tasks (last 30 d)" → "My Open Tasks (last 30 d)".
  - Add new tab "My Finished Tasks" using `getMyFinishedTasks()`; reuse `MyTasksTab` component with a `finished` flag or a dedicated `finishedTasks` prop.
  - The `MyTaskReport` type in `types.ts` gains a `phaseName?: string` field (optional, backward-compat).

**Files**:
- `web-client/src/api/taskApi.ts`
- `web-client/src/pages/TasksPage.tsx`
- `web-client/src/api/reportingApi.ts`
- `web-client/src/pages/ReportsPage.tsx`
- `web-client/src/api/types.ts`

**Commit**: `feat(frontend): active-only task list default, Show filter, My Finished Tasks report tab`

---

## Verification

1. Start services. Open Tasks page — confirm RELEASED/REJECTED tasks are hidden by default.
2. Set "Show" → "All" — confirm RELEASED/REJECTED tasks reappear.
3. Set "Show" → "Finished" — confirm only RELEASED/REJECTED tasks appear.
4. Open Reports → "My Open Tasks" — confirm no RELEASED/REJECTED tasks shown.
5. Open Reports → "My Finished Tasks" — confirm only RELEASED/REJECTED tasks shown.
6. Run integration tests: `mvn test -pl task-service` and `mvn test -pl reporting-service`.
