# Refactor TaskDetailPage into Smaller Units

**Goal**: Break the 638-line `TaskDetailPage.tsx` monolith into focused constants, custom hooks, and sub-components so each file has a single responsibility.

**Branch**: `refactor_task_detail_page`

---

## Chunks

### Chunk 1 — Extract constants
**What**: Move `STATUS_COLORS`, `TYPE_COLORS`, and `TIMELINE_STATES` out of `TaskDetailPage.tsx` into a dedicated constants file so they can be reused without importing the full page.
**Files**:
- `web-client/src/pages/taskDetail/taskDetailConstants.ts` ← new
- `web-client/src/pages/TaskDetailPage.tsx` — replace inline constants with imports
**Commit**: `extract task detail constants to dedicated file`

---

### Chunk 2 — Extract custom hooks
**What**: Extract per-domain state + handlers into five custom hooks. Each hook owns its own loading/saving flags, its slice of state, and the API calls that operate on it. The hooks receive `taskId` as a parameter and return state + action functions.

| Hook | Owns |
|---|---|
| `useTaskTimeline` | `timelines`, modal state, save/delete handlers |
| `useTaskPlannedWork` | `plannedWork`, form state, save handler |
| `useTaskBookedWork` | `bookedWork`, form state, save/delete/reset handlers |
| `useTaskParticipants` | `participants`, form state, add/remove handlers |
| `useTaskComments` | `comments`, `newComment`, add handler |

**Files**:
- `web-client/src/hooks/useTaskTimeline.ts` ← new
- `web-client/src/hooks/useTaskPlannedWork.ts` ← new
- `web-client/src/hooks/useTaskBookedWork.ts` ← new
- `web-client/src/hooks/useTaskParticipants.ts` ← new
- `web-client/src/hooks/useTaskComments.ts` ← new
- `web-client/src/pages/TaskDetailPage.tsx` — replace inline state/handlers with hook calls
**Commit**: `extract per-domain custom hooks from TaskDetailPage`

---

### Chunk 3 — Extract tab sub-components
**What**: Move each tab's JSX into its own component file. Each component receives only the data and callbacks it needs as props (no global state).

| Component | Tab |
|---|---|
| `TaskTimelineTab` | Timeline cards + set/edit modal |
| `TaskPlannedWorkTab` | Planned work list + add form |
| `TaskBookedWorkTab` | Booked work list + add/edit form |
| `TaskParticipantsTab` | Participants list + add form |
| `TaskCommentsTab` | Comments list + add form |

**Files**:
- `web-client/src/components/taskDetail/TaskTimelineTab.tsx` ← new
- `web-client/src/components/taskDetail/TaskPlannedWorkTab.tsx` ← new
- `web-client/src/components/taskDetail/TaskBookedWorkTab.tsx` ← new
- `web-client/src/components/taskDetail/TaskParticipantsTab.tsx` ← new
- `web-client/src/components/taskDetail/TaskCommentsTab.tsx` ← new
- `web-client/src/pages/TaskDetailPage.tsx` — replace inline tab JSX with component references
**Commit**: `extract tab sub-components from TaskDetailPage`

---

### Chunk 4 — Extract TaskOverviewCard + final cleanup
**What**: Extract the task overview card (title, status/type tags, descriptions, progress) into its own component. After all chunks, `TaskDetailPage.tsx` should only handle: initial data loading, wiring hooks to components, and rendering the shell layout.
**Files**:
- `web-client/src/components/taskDetail/TaskOverviewCard.tsx` ← new
- `web-client/src/pages/TaskDetailPage.tsx` — use `TaskOverviewCard`, verify final file is slim orchestration only
**Commit**: `extract TaskOverviewCard and finalize TaskDetailPage cleanup`
