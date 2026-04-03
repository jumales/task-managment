# Planned Work — Creator from Auth, Not Request

**Goal**: Remove `userId` from the planned work request body. The creating user is resolved automatically from the authenticated principal, keeping the audit trail without requiring callers to supply it.

**Branch**: `planned_work_creator_from_auth`

---

## Chunks

### Chunk 1 — Backend: remove userId from request, wire auth user
**What**:
- Remove `userId` field from `TaskPlannedWorkRequest` DTO.
- In `TaskPlannedWorkController.create()`: extract the authenticated user's ID (same pattern used in other controllers) and pass it to the service.
- In `TaskPlannedWorkService.create()`: accept `UUID creatorId` as a parameter instead of reading it from the request; skip the `userClient.getUserById()` validation call (creator is already authenticated).
- Update Postman collection to remove `userId` from the request body.

**Files**:
- `common/src/main/java/com/demo/common/dto/TaskPlannedWorkRequest.java`
- `task-service/src/main/java/com/demo/task/controller/TaskPlannedWorkController.java`
- `task-service/src/main/java/com/demo/task/service/TaskPlannedWorkService.java`
- `postman/task-service.postman_collection.json`

**Commit**: `feat: resolve planned work creator from auth context instead of request body`

---

### Chunk 2 — Frontend: remove userId from form and request type
**What**:
- Remove `userId` field from `TaskPlannedWorkRequest` interface.
- Remove the user selector from `TaskPlannedWorkTab` form.
- Remove `pwUserId` state and related logic from `useTaskPlannedWork` hook.

**Files**:
- `web-client/src/api/types.ts`
- `web-client/src/components/taskDetail/TaskPlannedWorkTab.tsx`
- `web-client/src/hooks/useTaskPlannedWork.ts`

**Commit**: `feat: remove user selector from planned work form`

---

### Chunk 3 — Integration tests
**What**:
- Update existing planned work IT cases: remove `userId` from request body, confirm `userId` in response matches the authenticated test user.

**Files**:
- `task-service/src/test/java/com/demo/task/TaskPlannedWorkIT.java` (or wherever planned work tests live)

**Commit**: `test: update planned work tests for auth-based creator`
