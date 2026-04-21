# task_09 — Booked-work + planned-work

## Goal
Work tab on task detail supports planned-work list (PLANNING-only writes) and booked-work CRUD. Writes go to task-service; reads cross-check against reporting-service endpoints.

## Changes

### New module `android/feature-work/`
- `build.gradle.kts` — depends on `:core-ui`, `:domain`, `:data`, Hilt.
- `WorkTab.kt` — section inside `TaskDetailScreen` (via slot parameter from `:feature-tasks`).
- `PlannedWorkSection.kt`:
  - List of `PlannedWorkRow` with user, workType, hours.
  - Add FAB enabled only when task phase == `PLANNING`.
  - `WorkCreateDialog` for planned entries.
- `BookedWorkSection.kt`:
  - List rows grouped by user; swipe-to-edit / swipe-to-delete.
  - Block all writes when `task.phase in {RELEASED, REJECTED}` (the `FINISHED_PHASES` from CLAUDE.md).
  - Allow edits in `DONE` since CLAUDE.md says booked-work create/update/delete stays allowed there.
- `DurationPicker.kt` — hours + minutes wheel; outputs `BigInteger` hours to match backend.
- `WorkType` dropdown driven by enum from task_04.

### Repository additions
- `BookedWorkRepository.list(taskId)`, `.create(request)`, `.update(id, request)`, `.delete(id)` → `/api/v1/booked-work`.
- `PlannedWorkRepository.list(taskId)`, `.create(request)` → `/api/v1/planned-work`.

## Tests
- Unit: `BookedWorkViewModel` rejects writes when phase is RELEASED.
- Unit: `PlannedWorkViewModel` allows create only when phase == PLANNING.
- Instrumented: adding 2h DEVELOPMENT booked-work appears in the list immediately.

## Acceptance
- After adding 2h booked-work in Android, `GET /api/v1/reports/hours/by-task` (reporting-service) returns matching total.
- Phase guards match webapp behavior.
