# task_07 — Task detail (read) + comments list

## Goal
Open any task from the list. Detail shows Overview + Comments populated; Participants / Work / Attachments present as placeholder tabs filled in task_08–task_10.

## Changes

### `:feature-tasks`
- `detail/TaskDetailViewModel.kt`:
  - `load(taskId)` → `TaskRepository.getTaskFull(taskId)` (maps to `GET /api/v1/tasks/{id}/full`).
  - `loadComments(taskId)` → `TaskRepository.getComments(taskId)`.
  - `uiState: StateFlow<TaskDetailUiState>` with `Loading` / `Loaded(task, comments)` / `Error(throwable)`.
- `detail/TaskDetailScreen.kt`:
  - `TopAppBar` with task code + title + back button.
  - Tab row: Overview, Comments, Participants (placeholder), Work (placeholder), Attachments (placeholder).
  - Overview tab: status chip, phase, project, assignee, planned dates, description (markdown).
  - Comments tab: `LazyColumn` of `CommentCard` with author avatar, name, relative time, markdown body.
- `detail/CommentCard.kt` — markdown render via `compose-markdown` dependency added in `libs.versions.toml`.

### App nav
- Route `tasks/{taskId}` hosts `TaskDetailScreen`.
- Deep-link-friendly: accept `taskmanager://tasks/{taskId}` (intent filter added in task_17; nav already supports the path here).

## Tests
- Unit: `TaskDetailViewModel` transitions Loading → Loaded correctly when repository emits.
- Instrumented: opening detail from list shows task code in top bar; switching to Comments tab fetches comments exactly once.

## Acceptance
- Task created in webapp visible in Android detail.
- Comments rendered identically to webapp (markdown preserved).
