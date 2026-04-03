# Create Task Wizard

**Goal**: Replace the flat Create Task modal with a 3-step wizard (title/description → type/status/phase → assignee/dates); progress is always visible and defaulted to 0; type becomes mandatory.

**Branch**: `create_task_wizard`

---

## Chunks

### Chunk 1 — Plan file
**What**: Commit this plan file to version control.
**Files**: `plans/create-task-wizard.md`
**Commit**: `docs: add plan for create-task wizard`

### Chunk 2 — Wizard UI + i18n
**What**:
- Replace the flat create-modal form in `TasksPage.tsx` with an Ant Design `Steps` wizard.
- Step 1: Title (required), Description.
- Step 2: Type (required — change rule from optional to required), Status (default TODO), Phase (required).
- Step 3: Assigned to (required), Planned Start, Planned End.
- Progress is not shown in the create wizard (hardcoded to 0 on submit).
- Navigation: Back / Next buttons between steps; Submit on the last step.
- Edit modal remains unchanged (flat form, no wizard).
- Add i18n keys for step labels and navigation buttons in `en.json` and `hr.json`.
- Rebuild frontend (`npm run build`).
**Files**:
- `web-client/src/pages/TasksPage.tsx`
- `web-client/src/i18n/locales/en.json`
- `web-client/src/i18n/locales/hr.json`
- `web-client/dist/` (rebuilt assets)
**Commit**: `feat: convert create-task modal to 3-step wizard`
