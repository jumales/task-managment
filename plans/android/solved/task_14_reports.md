# task_14 — Reports (my-tasks, hours, open-by-project)

## Goal
Mirror `web-client/src/pages/ReportsPage.tsx` (minus the Dashboard cards). Read-only — no writes.

## Changes

### New module `android/feature-reports/`
- `build.gradle.kts` — `:core-ui`, `:data`, Vico charts (`com.patrykandpatrick.vico:compose-m3`), Hilt.
- `my/MyTasksSection.kt` — `GET /api/v1/reports/my-tasks?days=` with range chips (7 / 30 / 90 / all). Lists open tasks grouped by status.
- `hours/HoursByTaskScreen.kt` — `GET /api/v1/reports/hours/by-task?projectId=`; bar chart planned vs booked per task; table below.
- `hours/HoursByProjectScreen.kt` — `GET /api/v1/reports/hours/by-project`; same layout at project granularity.
- `hours/HoursDetailedScreen.kt` — `GET /api/v1/reports/hours/detailed?taskId=`; grouped by user + workType.
- `tasks/OpenByProjectScreen.kt` — `GET /api/v1/reports/tasks/open-by-project`; card list with total vs mine counts.
- `export/CsvExport.kt` — build CSV from response and launch share sheet via `Intent.ACTION_SEND` with `FileProvider` URI.

### Navigation
- `reports` tab → `ReportsHomeScreen` with links to each sub-report.

## Tests
- Unit: totals per row match `sum(planned)` / `sum(booked)` against a fixture.
- Instrumented: chart renders with non-empty data set (fixture-backed).

## Acceptance
- Totals on every Android screen match what webapp shows for the same data.
- CSV share opens system chooser with `report.csv`.
