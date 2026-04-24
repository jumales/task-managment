# task_11 — Projects + phases

## Goal
Projects list, project detail, phase CRUD nested inside a project — mirror `web-client/src/pages/ProjectsPage.tsx`.

## Changes

### New module `android/feature-projects/`
- `build.gradle.kts` — `:core-ui`, `:domain`, `:data`, Hilt.
- `list/ProjectsListViewModel.kt` — paged via Paging 3 against `GET /api/v1/projects`.
- `list/ProjectsListScreen.kt` — name, phase count, task count.
- `detail/ProjectDetailViewModel.kt` — loads project + phases.
- `detail/ProjectDetailScreen.kt`:
  - Header section (name, description, edit).
  - Phases section — list with reorder handle (drag-to-reorder with `reorderable` compose lib).
  - Default phase flag displayed.
- `detail/PhaseEditDialog.kt`.
- `detail/ProjectEditDialog.kt`.

### Repository additions
- `ProjectRepository.list(page, size)`, `.create`, `.update`, `.delete`.
- `PhaseRepository.listForProject(projectId)`, `.create`, `.update`, `.delete`, `.setDefault`.

### Role gating
- Edit/delete actions hidden when JWT has only SUPERVISOR (read-only). `AuthManager.authState.roles` exposes the set.
- Delete blocks with message from `RelatedEntityActiveException` → show snackbar (existing server error shape).

## Tests
- Unit: `ProjectDetailViewModel.reorderPhases` calls `PATCH` per repo method.
- Instrumented: default-phase badge shows on exactly one phase; tapping "Set default" updates UI optimistically.

## Acceptance
- Create project from Android → visible in webapp.
- Delete phase with active tasks → server returns error → Android shows snackbar with reason.
