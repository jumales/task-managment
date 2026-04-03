# Phase Custom Name & Project-Scoped Configuration

**Goal**: Add a user-defined `customName` to each TaskPhase, auto-create all phases when a project is created, move phase configuration UI to per-project context, and display the custom name (with enum fallback) on all task screens.

**Branch**: `phase_custom_name_and_project_config`

---

## Chunks

### Chunk 1 — Flyway migration
**What**: Add a nullable `custom_name` column to `task_phases`. No data migration needed — existing rows keep NULL (will fall back to enum name in the UI).
**Files**:
- `task-service/src/main/resources/db/migration/V12__add_custom_name_to_task_phases.sql`

**Commit**: `feat: add custom_name column to task_phases`

---

### Chunk 2 — Entity + DTOs
**What**:
- Add `customName` (String, nullable) field to `TaskPhase` entity.
- Add `customName` to `TaskPhaseRequest` (optional, nullable).
- Add `customName` to `TaskPhaseResponse`.

**Files**:
- `task-service/src/main/java/com/demo/task/model/TaskPhase.java`
- `common/src/main/java/com/demo/common/dto/TaskPhaseRequest.java`
- `common/src/main/java/com/demo/common/dto/TaskPhaseResponse.java`

**Commit**: `feat: add customName field to TaskPhase entity and DTOs`

---

### Chunk 3 — Service: pass-through + auto-create phases on project creation
**What**:
- Update `TaskPhaseService.create()` and `update()` to persist/return `customName`.
- Update `TaskProjectService.create()`: after saving the new project, create one `TaskPhase` for every `TaskPhaseName` enum value (all 7), each with `customName = null`. This ensures every project always has a full set of phases from the start.

**Files**:
- `task-service/src/main/java/com/demo/task/service/TaskPhaseService.java`
- `task-service/src/main/java/com/demo/task/service/TaskProjectService.java`

**Commit**: `feat: pass customName through phase service and auto-create phases on project creation`

---

### Chunk 4 — Integration tests
**What**:
- Test that creating a project auto-creates all 7 phases with null customName.
- Test that updating a phase's customName is persisted and returned.
- Test that customName can be cleared (set back to null).

**Files**:
- `task-service/src/test/java/com/demo/task/TaskProjectIT.java`
- `task-service/src/test/java/com/demo/task/TaskPhaseIT.java`

**Commit**: `test: cover auto-phase creation and customName persistence`

---

### Chunk 5 — Frontend: types, API client, display helper
**What**:
- Add `customName: string | null` to `TaskPhaseResponse` interface in `types.ts`.
- Add `updatePhase(id: string, request: TaskPhaseRequest)` to `taskApi.ts` (currently missing).
- Add a shared display helper `resolvePhaseLabel(phase)` that returns `phase.customName` when non-empty, otherwise a formatted version of `phase.name` (e.g. `"IN_PROGRESS"` → `"In Progress"`). Put it in a small util file so all screens use the same fallback logic.

**Files**:
- `web-client/src/api/types.ts` (or `types.js`)
- `web-client/src/api/taskApi.ts` (or `taskApi.js`)
- `web-client/src/utils/phaseUtils.js` *(new)*

**Commit**: `feat: add customName to phase types, updatePhase API call, and resolvePhaseLabel helper`

---

### Chunk 6 — Frontend: move phase config to project, edit custom name
**What**:
- Remove the standalone **Phases** tab from `ConfigurationPage` (phases are now auto-created and managed per-project).
- In `ProjectsPage`, add an expandable row or a **"Manage Phases"** modal per project. It shows all phases for that project in a table with an inline editable **Custom Name** field (text input, save on blur or via a small save button). Admins can also toggle the default phase from here.
- Since phases are always auto-created, remove the "Add Phase" button; the table is read-only in terms of rows — only custom names are editable.

**Files**:
- `web-client/src/pages/ConfigurationPage.tsx` (or `.js`) — remove PhasesTab
- `web-client/src/pages/ProjectsPage.tsx` (or `.js`) — add phase management per project
- `web-client/src/i18n/locales/en.json` — add/update i18n keys
- `web-client/src/i18n/locales/hr.json` — add/update i18n keys

**Commit**: `feat: move phase config to project, allow editing custom name per phase`

---

### Chunk 7 — Frontend: show custom name on task screens
**What**:
- Replace all raw `phase.name` (or `task.phase?.name`) display strings with `resolvePhaseLabel(phase)` from the helper added in Chunk 5.
- Affects: phase dropdown in TasksPage create/edit wizard, phase column in tasks table, TaskOverviewCard metadata, and any other location showing a phase name.

**Files**:
- `web-client/src/pages/TasksPage.tsx` (or `.js`)
- `web-client/src/components/taskDetail/TaskOverviewCard.tsx` (or `.js`)
- `web-client/src/pages/TaskDetailPage.tsx` (or `.js`)

**Commit**: `feat: display phase customName with enum fallback on all task screens`
