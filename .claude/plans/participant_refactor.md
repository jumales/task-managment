# Plan: Participant Role Refactor + Auto-Add + Watch Button

## Context

The participant system needs a behavioral overhaul:
- Roles are being renamed (`REVIEWER` → `CONTRIBUTOR`, `VIEWER` → `WATCHER`) to better reflect intent
- Participants should be added automatically when a user interacts with the task (comment, attachment, booked work), not manually
- Removal is restricted to self-removal only, and blocked if the participant is the assignee
- A "Watch" button on task detail lets any authenticated user subscribe as a WATCHER
- All existing participant data is cleared via migration (old roles are invalid)

---

## Step 1 — Flyway Migration

**File:** `task-service/src/main/resources/db/migration/V{next}__refactor_participant_roles.sql`

```sql
-- Clear all existing participant data; old roles are no longer valid
DELETE FROM task_participants;
```

Find next migration number from existing files in `db/migration/`.

---

## Step 2 — Rename Roles in Common Enum

**File:** `common/src/main/java/com/demo/common/dto/TaskParticipantRole.java`

```java
public enum TaskParticipantRole {
    CREATOR,      // auto at task creation
    ASSIGNEE,     // auto via task assignee field; only changeable on task edit
    CONTRIBUTOR,  // auto when commenting, uploading, or booking (was REVIEWER)
    WATCHER       // via Watch button (was VIEWER)
}
```

Search for all usages of `REVIEWER` and `VIEWER` across the codebase and update them.

---

## Step 3 — Update `TaskParticipantService`

**File:** `task-service/src/main/java/com/demo/task/service/TaskParticipantService.java`

### 3a — Add `addIfNotPresent` method

Auto-add a user as CONTRIBUTOR or WATCHER only if they have **no existing active participant record** for the task (prevents double-registering CREATOR/ASSIGNEE as CONTRIBUTOR).

```java
void addIfNotPresent(UUID taskId, UUID userId, TaskParticipantRole role) {
    if (repository.existsByTaskIdAndUserId(taskId, userId)) return;
    repository.save(TaskParticipant.builder()
            .taskId(taskId)
            .userId(userId)
            .role(role)
            .build());
}
```

Add `existsByTaskIdAndUserId(UUID taskId, UUID userId)` to `TaskParticipantRepository`.

### 3b — Update `remove` to enforce WATCHER-only self-removal

Only a WATCHER can remove themselves. CREATOR, ASSIGNEE, and CONTRIBUTOR entries cannot be removed.

```java
public void remove(UUID participantId, UUID requestingUserId) {
    TaskParticipant participant = getOrThrow(participantId);
    if (participant.getRole() != TaskParticipantRole.WATCHER) {
        throw new BusinessLogicException("Only WATCHER participants can be removed.");
    }
    if (!participant.getUserId().equals(requestingUserId)) {
        throw new BusinessLogicException("You can only remove your own WATCHER entry.");
    }
    participant.setDeletedAt(Instant.now());
    repository.save(participant);
}
```

---

## Step 4 — Update Controller to Pass Authenticated User

**File:** `task-service/src/main/java/com/demo/task/controller/TaskParticipantController.java`

- `DELETE /api/v1/tasks/{taskId}/participants/{participantId}`: extract `userId` from JWT and pass to `service.remove(participantId, requestingUserId)`
- Remove `POST /api/v1/tasks/{taskId}/participants` endpoint entirely (no manual adds; handled automatically)

---

## Step 5 — Auto-Add on Comment, Attachment, Booked Work

Inject `TaskParticipantService` into each service and call `addIfNotPresent` after saving.

### 5a — Comment
**File:** `task-service/src/main/java/com/demo/task/service/TaskService.java`

In `addComment(UUID taskId, TaskCommentRequest request, UUID authorId)`, after `commentRepository.save(...)`:
```java
participantService.addIfNotPresent(taskId, authorId, TaskParticipantRole.CONTRIBUTOR);
```

### 5b — Attachment
**File:** `task-service/src/main/java/com/demo/task/service/TaskAttachmentService.java`

In `create(UUID taskId, TaskAttachmentRequest request, Authentication auth)`, after `repository.save(...)`:
```java
participantService.addIfNotPresent(taskId, uploadedByUserId, TaskParticipantRole.CONTRIBUTOR);
```

### 5c — Booked Work
**File:** `task-service/src/main/java/com/demo/task/service/TaskBookedWorkService.java`

In `create(UUID taskId, UUID userId, TaskBookedWorkRequest request)`, after `repository.save(...)`:
```java
participantService.addIfNotPresent(taskId, userId, TaskParticipantRole.CONTRIBUTOR);
```

---

## Step 6 — Watch Endpoint

**File:** `task-service/src/main/java/com/demo/task/controller/TaskParticipantController.java`

Add dedicated watch endpoint:
```
POST /api/v1/tasks/{taskId}/participants/watch
```
- Extracts userId from JWT
- Calls `participantService.watch(taskId, userId)` (adds WATCHER if user has no entry)
- Returns the participant response

---

## Step 7 — Frontend

### 7a — AuthProvider
**File:** `web-client/src/auth/AuthProvider.tsx`

Add `userId: string` (from `keycloak.tokenParsed?.sub`) to `AuthContextValue`.

### 7b — Types
**File:** `web-client/src/api/types.ts`

```typescript
export type TaskParticipantRole = 'CREATOR' | 'ASSIGNEE' | 'CONTRIBUTOR' | 'WATCHER';
```

### 7c — Hook `useTaskParticipants.ts`
**File:** `web-client/src/hooks/useTaskParticipants.ts`

- Remove `newPRole`, `newPUserId`, `handleAddParticipant` state/handlers (no manual add form)
- Add `handleWatch()` — calls `POST /tasks/{taskId}/participants/watch`
- Keep `handleRemoveParticipant(participantId)` — now only shown for own WATCHER entries

### 7d — `TaskParticipantsTab.tsx`
**File:** `web-client/src/components/taskDetail/TaskParticipantsTab.tsx`

- **Remove** the add-participant form entirely
- **Remove** button: only render for entries where role is `WATCHER` AND `userId` matches `currentUserId`
- **Watch button**: render in the tab header — "Watch" if user has no WATCHER entry, "Unwatch" if they do

### 7e — API helper
**File:** `web-client/src/api/taskApi.ts`

- Remove `addParticipant()`
- Add `watchTask(taskId: string)` calling `POST /tasks/{taskId}/participants/watch`

---

## Step 8 — Postman Update

**File:** `postman/task-service.postman_collection.json`

- Remove "Add Participant" request
- Add "Watch Task" request (`POST /tasks/{{taskId}}/participants/watch`, no body)
- Update delete participant request description to note WATCHER-only restriction

---

## Step 9 — Integration Tests

**File:** `task-service/src/test/java/com/demo/task/TaskParticipantControllerIT.java`

- Add test: commenting auto-adds user as CONTRIBUTOR
- Add test: uploading attachment auto-adds user as CONTRIBUTOR
- Add test: booking work auto-adds user as CONTRIBUTOR
- Add test: watch endpoint adds WATCHER
- Add test: WATCHER self-remove succeeds
- Add test: remove CONTRIBUTOR entry → 422 (not a WATCHER)
- Add test: remove ASSIGNEE entry → 422 (not a WATCHER)
- Add test: remove another user's WATCHER entry → 422 (not own entry)
- Add test: manual POST to add participant → 404 (endpoint removed)

---

## Verification

1. `mvn clean install -DskipTests=true` passes
2. Start dev stack, create a task, add a comment → verify CONTRIBUTOR participant appears
3. Upload attachment → verify CONTRIBUTOR participant appears (no duplicate if already CONTRIBUTOR)
4. Book hours → verify CONTRIBUTOR participant appears
5. Click Watch → verify WATCHER participant appears
6. Click Unwatch → participant removed
7. Try to remove another user's participant → blocked
8. Try to remove own ASSIGNEE → blocked
9. Change assignee via task edit → ASSIGNEE participant updates correctly
10. Run IT tests: `mvn test -pl task-service`
