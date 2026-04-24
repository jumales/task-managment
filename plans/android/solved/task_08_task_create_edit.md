# task_08 — Task create/edit + comment add + participants

## Goal
Write-path on tasks: create, update fields, add comment, add/remove participants. Mirror webapp validation.

## Changes

### `:feature-tasks`
- `create/TaskCreateScreen.kt` — scaffold with `rememberSaveable` form state:
  - Fields: title, description, projectId (dropdown from projects repo), phaseId (dependent), assignedUserId (user picker), status, plannedStart, plannedEnd.
  - Backend write rules from `.claude/CLAUDE.md`:
    - `validateNotPlanningPhase` — planned dates only in PLANNING.
    - `validateNotFinished` — block edits for RELEASED/REJECTED.
    - `validateFieldsEditable` — block field edit in DONE/RELEASED/REJECTED.
  - Duplicate validation client-side; snackbar on 400 with `fieldErrors` mapped from `ApiErrorResponse`.
- `edit/TaskEditScreen.kt` — same screen reused in Edit mode.
- `detail/CommentComposer.kt` — `OutlinedTextField` + send icon → `TaskRepository.addComment(taskId, text)` (`POST /api/v1/tasks/{id}/comments`).
- `detail/ParticipantsTab.kt` — list `GET /api/v1/tasks/{id}/participants`; add via user picker (`POST /api/v1/tasks/{id}/participants/add`); remove via swipe (`POST /.../participants/remove`). Matches webapp endpoints exactly.

### Repository additions
- `TaskRepository.createTask(request)`
- `TaskRepository.updateTask(id, request)`
- `TaskRepository.addComment(taskId, body)`
- `TaskRepository.addParticipant(taskId, userId)`
- `TaskRepository.removeParticipant(taskId, userId)`

### UX
- After create: snackbar + navigate to detail of new task.
- After comment add: optimistic insert + rollback on error.
- Role gating: hide edit affordances for SUPERVISOR (read-only role per `common/src/main/java/com/demo/common/config/JwtAuthConverter.java` mapping).

## Tests
- Unit: `TaskCreateViewModel` blocks submit when title blank.
- Unit: `TaskDetailViewModel.addComment` rolls back on 500.
- Instrumented: filling form and tapping Save calls MockWebServer with correct JSON.

## Acceptance
- Create task in Android → visible in webapp.
- Edit title from Android → webapp's open detail receives STOMP push and updates.
- Comment added in Android shows in webapp immediately.
