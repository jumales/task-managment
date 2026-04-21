# task_05 — Domain + core-ui + navigation skeleton

## Goal
`:domain` models and use-case interfaces usable by every feature module. `:core-ui` design system and i18n. `:app` navigation host with empty routes.

## Changes

### `:domain`
- Pure Kotlin (no Android). Depends on nothing.
- `model/` — Task, Project, Phase, Comment, User, Attachment, BookedWork, PlannedWork, ReportTotals, etc. Plain `data class`es — separate from `:data` DTOs so we can decouple over time.
- `mapper/` — extension functions `TaskDto.toDomain()` (kept in `:data` for DTO side).
- `usecase/` — one interface per major action (e.g. `LoadTasksUseCase`, `CreateTaskUseCase`). Implementations live in `:data`.

### `:core-ui`
- `theme/AppTheme.kt` — Material 3 with palette mirroring `web-client/src/App.tsx` CSS variables.
- `theme/Typography.kt`, `theme/Shapes.kt`.
- Shared composables:
  - `LoadingScreen`, `ErrorState`, `EmptyState`
  - `TaskStatusChip` driven by `TaskStatus` enum
  - `UserAvatar` (Coil async image)
  - `SectionHeader`, `ConfirmationDialog`
- `i18n/` — string resources split en/hr (mirror webapp `web-client/src/i18n/`). Wire `AppCompatDelegate.setApplicationLocales`.
- `a11y/` — content-description helpers.

### `:app` navigation
- `android/app/src/main/kotlin/com/demo/taskmanager/nav/AppNavGraph.kt`:
  - Routes: `login`, `tasks`, `projects`, `users`, `search`, `reports`, `config`, `profile`.
  - Each route placeholder `Text("tasks screen")` etc.
  - Bottom nav with icons for Tasks / Search / Reports / Profile; side drawer for Projects / Users / Config (admin-gated placeholder).
- `AuthGate.kt` composable — observes `AuthManager.authState`; `Unauthenticated` → NavController pops to `login`.

## Tests
- Unit: `AppNavGraph` starts at `login` when unauthenticated, `tasks` when authenticated (`rememberNavController` test harness).

## Acceptance
- Running the app with a fake authenticated state navigates between all tabs.
- Theme survives process restart; language switch (en ↔ hr) flips labels live.
