# task_06 — Tasks list screen (primary entry point)

## Goal
Replace the `tasks` route placeholder with a Paging 3 list mirroring `web-client/src/pages/TasksPage.tsx`.

## Changes

### New module `android/feature-tasks/`
- `build.gradle.kts` — depends on `:core-ui`, `:domain`, `:data`, `:core-network`, Paging 3.3, Hilt.
- `list/TasksListViewModel.kt` — exposes `PagingData<TaskUi>` via `Pager` → `PagingSource`:
  - Filter state: `status`, `completionStatus`, `projectId`, `assignedUserId` (from `common/src/main/java/com/demo/common/dto/TaskCompletionStatus.java`).
  - Calls `TaskRepository.getTasksPaged(filters)` which hits `GET /api/v1/tasks`.
- `list/TasksPagingSource.kt`.
- `list/TasksListScreen.kt` — LazyColumn with `TaskCard` composable, pull-to-refresh (`PullToRefreshBox`), filter chips at top.
- `list/TaskCard.kt` — task code, title, status chip, assigned avatar, project badge.
- `list/TasksFilterBar.kt` — chip row; status & completion status options from Kotlin enums (task_04).

### `:data` wiring
- `TaskRepository.getTasksPaged(filters): Flow<PagingData<Task>>`.
- Map DTO → domain via existing mappers (task_04).
- Cache: pass `cachedIn(viewModelScope)` in ViewModel.

### App nav
- `AppNavGraph` `tasks` route hosts `TasksListScreen`; `onTaskClick(id)` navigates to `tasks/{taskId}` (placeholder in task_07).

## Tests
- Unit: `TasksListViewModel` emits filter changes correctly (Turbine).
- Unit: `TasksPagingSource` maps API page to `LoadResult.Page` with correct keys.
- Instrumented (Compose): filter chip toggles re-query; empty state shown when list empty.

## Acceptance
- Scrolling past 20 tasks loads the next page.
- Filter chips filter server-side (inspect Retrofit logs).
- Tap → navigates to detail with correct `taskId`.
