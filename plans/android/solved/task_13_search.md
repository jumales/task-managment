# task_13 — Search (tasks + users)

## Goal
Unified search with tabs Tasks / Users; debounced input; recent queries persisted.

## Changes

### New module `android/feature-search/`
- `build.gradle.kts` — `:core-ui`, `:data`, `:domain`, DataStore preferences, Hilt.
- `SearchViewModel.kt`:
  - `query: MutableStateFlow<String>` with `.debounce(300).distinctUntilChanged()`.
  - `tasksFlow: Flow<List<TaskHit>>` from `SearchRepository.searchTasks(q)` → `GET /api/v1/search/tasks?q=`.
  - `usersFlow: Flow<List<UserHit>>` from `SearchRepository.searchUsers(q)` → `GET /api/v1/search/users?q=`.
  - Last 10 queries stored in DataStore `recent_queries.pb`.
- `SearchScreen.kt`:
  - Top `TextField` with clear button.
  - Tab row Tasks / Users.
  - Recent chips shown when `query.isBlank()`.
  - Result rows tap into detail (tasks) or profile (users).

## Tests
- Unit: debounce collapses rapid typing into one request.
- Unit: DataStore stores the last 10 unique queries, FIFO eviction.

## Acceptance
- Typing 5 chars fires at most 2 requests.
- Recent-query chip survives app kill.
- Match parity with `web-client/src/pages/SearchPage.tsx`.
