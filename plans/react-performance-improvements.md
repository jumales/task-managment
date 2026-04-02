# React Performance Improvements

**Goal**: Fix 11 performance issues identified by Vercel React best practices analysis — eliminating sequential API waterfalls, removing per-render object recreation, and adding memoization throughout the web-client.

**Branch**: `react_performance_improvements`

---

## Chunks

### Chunk 1 — Plan file + shared label constants
**What**:
- Commit this plan file.
- Move `workTypeLabels` (duplicated in `TaskPlannedWorkTab` and `TaskBookedWorkTab`) into `taskDetailConstants.ts` as a shared exported constant.
- Move `typeLabels` (in `TaskOverviewCard`) into `taskDetailConstants.ts`.
- Remove the duplicate inline declarations from both tab components and `TaskOverviewCard`; import from constants instead.

**Files**:
- `plans/react-performance-improvements.md` ← this file
- `web-client/src/pages/taskDetail/taskDetailConstants.ts`
- `web-client/src/components/taskDetail/TaskPlannedWorkTab.tsx`
- `web-client/src/components/taskDetail/TaskBookedWorkTab.tsx`
- `web-client/src/components/taskDetail/TaskOverviewCard.tsx`

**Commit**: `move workTypeLabels and typeLabels to taskDetailConstants`

---

### Chunk 2 — Parallelize task detail API calls
**What**:
- Create `web-client/src/hooks/useTaskDetailData.ts` — a single hook that fetches all 5 tab datasets (timelines, plannedWork, bookedWork, participants, comments) and the users list in **one `Promise.all`** call. Returns `{ data, loading, error }`.
- Refactor each of the 5 existing hooks (`useTaskTimeline`, `useTaskPlannedWork`, `useTaskBookedWork`, `useTaskParticipants`, `useTaskComments`) to:
  1. Accept pre-fetched `initialData` as a prop instead of fetching it themselves (remove the initial-load `useEffect`).
  2. Own their own error state internally (remove the `setError` callback parameter).
- Update `TaskDetailPage.tsx`:
  - Replace the `Promise.all([getTask, getUsers])` effect + 5 hook calls with `useTaskDetailData(id)` + `getTask(id)`.
  - Pass `initialData` slices to each hook.
  - Read `error` from `useTaskDetailData` instead of passing `setError` down.

**Files**:
- `web-client/src/hooks/useTaskDetailData.ts` ← new
- `web-client/src/hooks/useTaskTimeline.ts`
- `web-client/src/hooks/useTaskPlannedWork.ts`
- `web-client/src/hooks/useTaskBookedWork.ts`
- `web-client/src/hooks/useTaskParticipants.ts`
- `web-client/src/hooks/useTaskComments.ts`
- `web-client/src/pages/TaskDetailPage.tsx`

**Commit**: `batch task detail API calls with Promise.all; hooks own error state`

---

### Chunk 3 — useMemo / useCallback for derived values
**What**:
- `TasksPage.tsx`: wrap `statusOptions`, `typeLabels`, `typeOptions` in `useMemo` (depend on `t`).
- `TasksPage.tsx`: wrap `columns` array in `useMemo`.
- `UsersPage.tsx`: wrap `columns` array in `useMemo`.
- `AppLayout.tsx`: wrap `navItems` in `useMemo` (depends on `t`).
- `TaskTimelineTab.tsx`: wrap `users.map(...)` options in `useMemo`.
- `TaskPlannedWorkTab.tsx`: wrap workType and user Select `options` in `useMemo`.
- `TaskBookedWorkTab.tsx`: wrap workType and user Select `options` in `useMemo`.
- `TaskParticipantsTab.tsx`: wrap user Select `options` in `useMemo`.
- `SearchPage.tsx`: wrap `onRow` handler in `useCallback`.

**Files**:
- `web-client/src/pages/TasksPage.tsx`
- `web-client/src/pages/UsersPage.tsx`
- `web-client/src/components/AppLayout.tsx`
- `web-client/src/components/taskDetail/TaskTimelineTab.tsx`
- `web-client/src/components/taskDetail/TaskPlannedWorkTab.tsx`
- `web-client/src/components/taskDetail/TaskBookedWorkTab.tsx`
- `web-client/src/components/taskDetail/TaskParticipantsTab.tsx`
- `web-client/src/pages/SearchPage.tsx`

**Commit**: `memoize derived arrays and callbacks to prevent unnecessary re-renders`

---

### Chunk 4 — O(n²) → O(n) in ConfigurationPage
**What**:
- `ConfigurationPage.tsx` lines 160–163: replace `templates.find(t => t.eventType === eventType)` inside `.map()` with a `Map<eventType, template>` built once before the map, reducing complexity from O(n×m) to O(n+m).

**Files**:
- `web-client/src/pages/ConfigurationPage.tsx`

**Commit**: `replace O(n²) array.find loop with Map lookup in ConfigurationPage`
