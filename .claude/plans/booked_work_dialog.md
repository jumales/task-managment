# Plan: Booked Work — Dialog Entry, Auto-User, Work Type Summary

## Context

Currently, booked work is entered via an inline form at the top of the `TaskBookedWorkTab`. The form exposes a user selector, allowing anyone to book hours on behalf of any user — which is undesirable. The form also shows no context about planned vs already-booked hours for the selected work type.

This plan:
1. Moves the add/edit form into a modal dialog.
2. Makes the backend auto-assign the authenticated user (removes the user selector entirely).
3. Adds a contextual info message inside the dialog that shows planned hours and total already-booked hours for the selected work type.

---

## Changes

### Backend

#### 1. `common/src/main/java/com/demo/common/dto/TaskBookedWorkRequest.java`
- Remove the `userId` field entirely. The request now only carries `workType` and `bookedHours`.

#### 2. `task-service/.../controller/TaskBookedWorkController.java`
- Inject `UserClientHelper` via constructor.
- Add `Authentication authentication` parameter to `create()` and `update()`.
- Resolve `userId` via `userClientHelper.resolveUserId(authentication)` and pass it to the service.

#### 3. `task-service/.../service/TaskBookedWorkService.java`
- Change `create(UUID taskId, TaskBookedWorkRequest request)` → `create(UUID taskId, UUID userId, TaskBookedWorkRequest request)`.
- Change `update(UUID taskId, UUID bookedWorkId, TaskBookedWorkRequest request)` → `update(UUID taskId, UUID bookedWorkId, UUID userId, TaskBookedWorkRequest request)`.
- Remove `request.getUserId()` calls; use the `userId` parameter instead.

#### 4. `postman/task-service.postman_collection.json`
- Remove `userId` from the request body of "Add Booked Work" and "Update Booked Work" requests.

---

### Frontend

#### 5. `web-client/src/hooks/useTaskBookedWork.ts`
- Remove `bwUserId` / `setBwUserId` state (no longer needed).
- Add `dialogOpen` / `setDialogOpen` boolean state.
- Update `startEditing(bw)` to also call `setDialogOpen(true)`.
- Add `openAddDialog()` helper that resets the form and sets `dialogOpen(true)`.
- Update `handleSaveBookedWork()` — remove `bwUserId` guard and from request payload; close dialog on success.
- Update `resetBwForm()` to also close dialog.
- Accept `plannedWork: TaskPlannedWorkResponse[]` as a second parameter so the hook can expose the summary.
- Expose `plannedHoursForType` and `bookedHoursForType` derived values (computed from `bwType`, `plannedWork`, and `bookedWork`).

#### 6. `web-client/src/components/taskDetail/TaskBookedWorkTab.tsx`
- Accept `plannedWork` prop (array of `TaskPlannedWorkResponse`).
- Remove `bwUserId` / `setBwUserId` from props.
- Replace the inline form block with:
  - An "Add Booked Work" `Button` (hidden in PLANNING/finished).
  - An Ant Design `Modal` component (controlled by `dialogOpen`).
- Inside the Modal:
  - Work type `Select`.
  - `InputNumber` for hours.
  - Info message (`Typography.Text` or `Alert` type `info`) showing: `"Planned: Xh | Already booked: Yh"` for the selected work type. Show even when planned = 0.
- Edit flow: clicking "Edit" on a list item opens the dialog pre-populated.
- List section unchanged.

#### 7. `web-client/src/pages/TaskDetailPage.tsx`
- Pass `plannedWorkData` to `useTaskBookedWork` (or pass as prop to `TaskBookedWorkTab`).

---

## Data Flow for the Work Type Summary

Computed client-side — no new API call needed:
- **Planned hours**: `plannedWork.find(p => p.workType === bwType)?.plannedHours ?? 0`
- **Already booked**: `bookedWork.filter(b => b.workType === bwType && b.id !== editingBw?.id).reduce((acc, b) => acc + Number(b.bookedHours), 0)`

---

## Critical Files

| File | Change |
|------|--------|
| `common/src/main/java/com/demo/common/dto/TaskBookedWorkRequest.java` | Remove `userId` field |
| `task-service/.../controller/TaskBookedWorkController.java` | Inject `UserClientHelper`, add `Authentication` params |
| `task-service/.../service/TaskBookedWorkService.java` | Accept `userId` param in `create` / `update` |
| `web-client/src/hooks/useTaskBookedWork.ts` | Dialog state, remove user state, planned work summary |
| `web-client/src/components/taskDetail/TaskBookedWorkTab.tsx` | Replace inline form with button + Modal |
| `web-client/src/pages/TaskDetailPage.tsx` | Pass `plannedWorkData` to booked work hook/tab |
| `postman/task-service.postman_collection.json` | Remove userId from booked work requests |

---

## Verification

1. `mvn clean install -DskipTests=true` — confirm build passes.
2. Start dev stack, open a task in a non-PLANNING, non-finished phase.
3. "Add Booked Work" button visible → click → dialog opens with work type selector and hours field (no user selector).
4. Change work type → info line updates: "Planned: Xh | Already booked: Yh".
5. Submit → entry appears in list attributed to the currently logged-in user.
6. Click Edit on an entry → dialog opens pre-populated; save updates correctly.
7. In PLANNING or finished tasks → button is hidden.
8. Run existing integration tests: `TaskBookedWorkIT` — confirm they still pass after removing `userId` from request.
