# Plan: Decouple TaskParticipantService from Action Services

**Date:** 2026-04-15
**Status:** Accepted

## Goal

Move participant auto-registration for comment, attachment, and booked-work actions out of the backend services and into an explicit frontend `POST /join` call, eliminating the cross-service coupling that was flagged by TODO comments in `TaskAttachmentService` and `TaskBookedWorkService`.

## Background / Context

`TaskParticipantService.addIfNotPresent()` is currently called synchronously inside `TaskService.addComment()`, `TaskAttachmentService.create()`, and `TaskBookedWorkService.create()`. Both attachment and booked-work services carry TODO comments requesting this coupling be removed. The agreed fix is to expose a new `POST /join` endpoint and have the frontend fire it explicitly after each of those actions succeeds. Coupling for task creation (`setCreator`, `setAssignee`) is intentional and remains unchanged.

## Scope

- `task-service/src/main/java/com/demo/task/service/TaskParticipantService.java`
- `task-service/src/main/java/com/demo/task/service/TaskService.java`
- `task-service/src/main/java/com/demo/task/service/TaskAttachmentService.java`
- `task-service/src/main/java/com/demo/task/service/TaskBookedWorkService.java`
- `task-service/src/main/java/com/demo/task/controller/TaskParticipantController.java`
- `postman/task-service.postman_collection.json`
- `web-client/src/api/taskApi.ts`
- `web-client/src/hooks/useTaskComments.ts`
- `web-client/src/hooks/useTaskAttachments.ts`
- `web-client/src/hooks/useTaskBookedWork.ts`
- Integration test class(es) for `TaskParticipantController`

## Implementation Steps

1. Create branch `decouple_participant_service` from `main`.

2. **`TaskParticipantService`** — Add a public `join(UUID taskId, UUID userId)` method that mirrors `watch()` but assigns the `CONTRIBUTOR` role. If an active participant entry already exists for the user, skip the save and return the existing entry. If the user is new, save with `CONTRIBUTOR` role and publish `TaskChangedEvent.participantAdded(taskId, userId)`. Return `TaskParticipantResponse`. Remove the existing `addIfNotPresent()` method entirely once all call sites have been removed (steps 3–5 below).

3. **`TaskParticipantController`** — Add `POST /api/v1/tasks/{taskId}/participants/join` endpoint:
   - `@ResponseStatus(HttpStatus.CREATED)`, `@PreAuthorize("isAuthenticated()")`
   - Resolve `userId` via `userClientHelper.resolveUserId(authentication)`
   - Delegate to `service.join(taskId, userId)`
   - Add `@Operation(summary = "Join a task as CONTRIBUTOR")` OpenAPI annotation
   - Update class-level Javadoc to remove the phrase "added automatically when users interact"

4. **`TaskService`** (~line 436–439) — Delete the `if (authorId != null) { participantService.addIfNotPresent(...) }` block and its associated comment from `addComment()`. The `participantService` field stays in place (still used for `setCreator`, `setAssignee`, `findByTaskId`, `findByTaskIds`). Remove `TaskParticipantRole` import if it is no longer referenced elsewhere in the file.

5. **`TaskAttachmentService`** (~line 82–84) — Delete the `participantService.addIfNotPresent(...)` call and its TODO comment from `create()`. Remove the `participantService` constructor parameter and field (no other uses remain). Remove the `TaskParticipantRole` import.

6. **`TaskBookedWorkService`** (~line 96–98) — Delete the `participantService.addIfNotPresent(...)` call and its TODO comment from `create()`. Remove the `participantService` constructor parameter and field (no other uses remain). Remove the `TaskParticipantRole` import.

7. **`taskApi.ts`** — Add the `joinTask(taskId: string)` function after the existing participant API functions:
   ```typescript
   export function joinTask(taskId: string) {
     return apiClient.post<TaskParticipantResponse>(`${TASKS_URL}/${taskId}/participants/join`).then((r) => r.data);
   }
   ```

8. **`useTaskComments.ts`** — After `addComment()` resolves successfully, fire `joinTask(taskId)` as fire-and-forget.

9. **`useTaskAttachments.ts`** — After `addAttachment()` resolves, fire `joinTask(taskId)` as fire-and-forget.

10. **`useTaskBookedWork.ts`** — After booked work create succeeds, fire `joinTask(taskId)` as fire-and-forget.

11. **Postman** — Add `POST {{baseUrl}}/api/v1/tasks/{{taskId}}/participants/join` to the Task Participants folder in `postman/task-service.postman_collection.json` with a status-code assertion of 201.

12. **Integration tests — new endpoint** — Add IT cases for `POST /join`:
    - Authenticated user calling `POST /join` returns 201 with `CONTRIBUTOR` role.
    - Calling `POST /join` twice is idempotent: second call returns 201 with the existing entry, no duplicate row.
    - User already registered as `CREATOR` or `ASSIGNEE`: join is skipped, existing entry is returned.

13. **Integration tests — remove stale assertions** — Remove any IT assertions that verified auto-participant registration triggered by comment, attachment, or booked-work creation.

14. Run `mvn clean install -DskipTests=true` from the project root and confirm it passes.

15. Push branch `decouple_participant_service` and open a pull request against `main`.

## Testing Plan

- Add IT cases to `TaskParticipantControllerIT` (or equivalent) covering the three scenarios listed in step 12.
- Remove stale auto-registration assertions from `TaskCommentIT`, `TaskAttachmentIT`, and `TaskBookedWorkIT` as described in step 13.
- Do not run integration tests automatically; run only when explicitly requested.

## Postman / API Changes

- New endpoint: `POST /api/v1/tasks/{taskId}/participants/join` — returns 201 with `TaskParticipantResponse`.
- Add request to the Task Participants folder in `postman/task-service.postman_collection.json`.

## Risks / Considerations

- **Race / missed join**: If the frontend call to `POST /join` fails (network error, token expiry), the user will not appear as a participant even though their action was recorded. This is an accepted trade-off of the decoupled design.
- **Idempotency is critical**: The `join()` method must never create duplicate participant rows; the existing-entry check must be applied before any insert.
- **`addIfNotPresent()` removal**: The method can only be deleted once all three call sites (steps 3–5) are removed in the same PR to avoid a compilation error.
- **Frontend fire-and-forget**: `joinTask()` failures are intentionally not surfaced to the user; the join is best-effort.

## Out of Scope

- Auto-registration via task creation (`setCreator`, `setAssignee`) — remains in the backend unchanged.
- Any changes to the `watch()` endpoint or watcher logic.
- Retroactive back-fill of participant records for historical comments, attachments, or booked work.
