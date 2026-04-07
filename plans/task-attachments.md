# Task Attachments

## Context
Users need to attach files to tasks (designs, specs, screenshots, etc.). The `task_attachments` table already exists (V17 migration merged to `feat/task_attachments`). The file-service already has an `attachments` bucket and `POST /api/v1/files/attachments` upload endpoint. This plan wires everything together: entity → service → controller → frontend tab.

**Delete is a real (hard) delete** — removes both the `task_attachments` record and the file from MinIO via file-service. Every change (add/delete) is traced via outbox events.

**Branch structure**:
- Integration branch: `feat/task_attachments` (Phase 1 / migration already merged as PR #72)
- Each phase: new branch off `feat/task_attachments`, PR targeting `feat/task_attachments`
- After all phases merged, `feat/task_attachments` → `main`

---

## Upload flow (2-step from frontend)
1. Frontend uploads file → `POST /api/v1/files/attachments` → gets `{ fileId, contentType }`
2. Frontend registers attachment on task → `POST /api/v1/tasks/{taskId}/attachments` with `{ fileId, fileName, contentType }`

Delete: `DELETE /api/v1/tasks/{taskId}/attachments/{attachmentId}` → task-service calls file-service to delete the MinIO object, then hard-deletes the `task_attachments` row.

---

## Phase 1 — Migration ✅ DONE
PR #72 merged to `feat/task_attachments`.
Table: `task_attachments(id, task_id, file_id, file_name, content_type, uploaded_by_user_id, uploaded_at)` — no `deleted_at` (hard delete).

---

## Phase 2 — Plan file + Event types
**Branch**: `feat/task_attachments_p2_events` → PR → `feat/task_attachments`

**What**:
- Commit this plan file to `plans/task-attachments.md`
- Add `ATTACHMENT_ADDED`, `ATTACHMENT_DELETED` to `TaskChangeType` enum
- Add fields `attachmentId UUID` and `fileName String` to `TaskChangedEvent`
- Add static factory methods:
  - `attachmentAdded(taskId, projectId, taskTitle, attachmentId, fileName, uploadedByUserId)`
  - `attachmentDeleted(taskId, projectId, taskTitle, attachmentId, fileName)`

**Files**:
- `plans/task-attachments.md` ← new
- `common/src/main/java/com/demo/common/event/TaskChangeType.java`
- `common/src/main/java/com/demo/common/event/TaskChangedEvent.java`

**Commit**: `feat: add ATTACHMENT_ADDED/DELETED event types to TaskChangedEvent`

---

## Phase 3 — Entity + Repository + DTOs
**Branch**: `feat/task_attachments_p3_entity_dto` → PR → `feat/task_attachments`

**What**:

`TaskAttachment.java` entity:
```java
@Entity @Table(name = "task_attachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskAttachment {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;
    @Column(name = "task_id", nullable = false)      private UUID taskId;
    @Column(name = "file_id", nullable = false)      private UUID fileId;
    @Column(name = "file_name", nullable = false)    private String fileName;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "uploaded_by_user_id", nullable = false) private UUID uploadedByUserId;
    private Instant uploadedAt;
}
```
Note: No `@SQLDelete` / `@SQLRestriction` — this is a hard-delete entity.

`TaskAttachmentRepository.java`:
```java
List<TaskAttachment> findByTaskIdOrderByUploadedAtAsc(UUID taskId);
```

DTOs in `common/src/main/java/com/demo/common/dto/`:

`TaskAttachmentRequest.java` — `@Data`: `fileId UUID`, `fileName String`, `contentType String`

`TaskAttachmentResponse.java` — `@Getter @AllArgsConstructor`:
`id UUID`, `fileId UUID`, `fileName String`, `contentType String`,
`uploadedByUserId UUID`, `uploadedByUserName String`, `uploadedAt Instant`

**Files**:
- `task-service/src/main/java/com/demo/task/model/TaskAttachment.java` ← new
- `task-service/src/main/java/com/demo/task/repository/TaskAttachmentRepository.java` ← new
- `common/src/main/java/com/demo/common/dto/TaskAttachmentRequest.java` ← new
- `common/src/main/java/com/demo/common/dto/TaskAttachmentResponse.java` ← new

**Commit**: `feat: add TaskAttachment entity, repository, and DTOs`

---

## Phase 4 — FileClient + Service
**Branch**: `feat/task_attachments_p4_service` → PR → `feat/task_attachments`

**What**:

`FileClient.java` Feign client (token-forwarding):
```java
@FeignClient(name = "file-service")
public interface FileClient {
    /** Deletes the file record and MinIO object. Caller must pass Bearer token matching the original uploader or an ADMIN. */
    @DeleteMapping("/api/v1/files/{fileId}")
    void deleteFile(@PathVariable UUID fileId, @RequestHeader("Authorization") String bearerToken);
}
```

`TaskAttachmentService.java`:
- `findByTaskId(UUID taskId)` — verify task exists, batch-load user names via `UserClientHelper.fetchUserNames()`, return `List<TaskAttachmentResponse>`
- `create(UUID taskId, TaskAttachmentRequest request, Authentication auth)` — verify task exists, resolve `uploadedByUserId` via `UserClientHelper.resolveUserId(auth)`, save, publish `ATTACHMENT_ADDED` event via `OutboxWriter`
- `delete(UUID taskId, UUID attachmentId, String bearerToken)` — verify task exists, get attachment or throw, call `fileClient.deleteFile(attachment.getFileId(), bearerToken)`, hard-delete record, publish `ATTACHMENT_DELETED` event

**Files**:
- `task-service/src/main/java/com/demo/task/client/FileClient.java` ← new
- `task-service/src/main/java/com/demo/task/service/TaskAttachmentService.java` ← new

**Commit**: `feat: add FileClient and TaskAttachmentService with outbox event publishing`

---

## Phase 5 — Controller + Postman
**Branch**: `feat/task_attachments_p5_controller` → PR → `feat/task_attachments`

**What**:

`TaskAttachmentController.java` at `/api/v1/tasks/{taskId}/attachments`:
- `GET /` → `findByTaskId` → `200 List<TaskAttachmentResponse>`
- `POST /` `@RequestBody TaskAttachmentRequest` → `create` → `201 TaskAttachmentResponse`
- `DELETE /{attachmentId}` — extract `"Bearer " + jwt.getTokenValue()`, call service → `204`

Update `postman/task-service.postman_collection.json`:
- Add folder "Task Attachments" with 3 requests (List / Add / Delete)
- Auto-save `attachmentId` from create response into collection variable

**Files**:
- `task-service/src/main/java/com/demo/task/controller/TaskAttachmentController.java` ← new
- `postman/task-service.postman_collection.json` ← update

**Commit**: `feat: add TaskAttachmentController and update Postman collection`

---

## Phase 6 — Integration tests
**Branch**: `feat/task_attachments_p6_tests` → PR → `feat/task_attachments`

**What**:
`TaskAttachmentControllerIT.java` following pattern in `TaskBookedWorkControllerIT.java`:
- Mock `UserClient` and `FileClient` with `@MockitoBean`
- `@BeforeEach`: create project, phase, task
- Tests: list empty, add attachment (201), list returns 1 item, delete (204), list empty again
- Test 404 on unknown task, 404 on unknown attachment
- Verify file-service delete is called when attachment is deleted

**Files**:
- `task-service/src/test/java/com/demo/task/TaskAttachmentControllerIT.java` ← new

**Commit**: `test: add integration tests for TaskAttachmentController`

---

## Phase 7 — Frontend
**Branch**: `feat/task_attachments_p7_frontend` → PR → `feat/task_attachments`

**What**:

1. `web-client/src/api/types.ts` — add:
   ```ts
   export interface TaskAttachmentResponse {
     id: string; fileId: string; fileName: string; contentType: string;
     uploadedByUserId: string; uploadedByUserName: string; uploadedAt: string;
   }
   // Add 'ATTACHMENT_ADDED' | 'ATTACHMENT_DELETED' to TaskChangeType
   ```

2. `web-client/src/api/taskApi.ts` — add:
   - `getAttachments(taskId)` → GET `/api/v1/tasks/{taskId}/attachments`
   - `addAttachment(taskId, req)` → POST `/api/v1/tasks/{taskId}/attachments`
   - `deleteAttachment(taskId, attachmentId)` → DELETE

3. `web-client/src/hooks/useTaskAttachments.ts` — new hook:
   - State: `attachments`, `uploading`, `error`
   - Handlers: `handleUpload(file: File)` (2-step: POST file-service then POST task-service), `handleDelete(attachmentId)`

4. `web-client/src/components/taskDetail/TaskAttachmentsTab.tsx` — new component:
   - Ant Design `List` of attachments with filename, uploader, date, download link (`/api/v1/files/{fileId}/download`)
   - `Popconfirm`-guarded delete button per row
   - Upload section: `<input type="file">` + Upload button (pattern from TaskCommentsTab)

5. `web-client/src/hooks/useTaskDetailData.ts` — add `getAttachments(taskId)` to `Promise.all()`, add `attachments` to returned data shape

6. `web-client/src/pages/TaskDetailPage.tsx` — add 6th tab: `{ key: 'attachments', label: t('tasks.attachments'), children: <TaskAttachmentsTab {...attachments} /> }`

7. i18n: add `tasks.attachments`, `tasks.uploadAttachment`, `tasks.deleteAttachment`, `tasks.confirmDeleteAttachment` keys

**Files**:
- `web-client/src/api/types.ts`
- `web-client/src/api/taskApi.ts`
- `web-client/src/hooks/useTaskAttachments.ts` ← new
- `web-client/src/components/taskDetail/TaskAttachmentsTab.tsx` ← new
- `web-client/src/hooks/useTaskDetailData.ts`
- `web-client/src/pages/TaskDetailPage.tsx`
- i18n files (check existing structure for locale file locations)

**Commit**: `feat: add attachments tab to task detail page`

---

## Verification
```bash
# Build
mvn clean install -DskipTests=true

# Tests (after Phase 6)
mvn -pl task-service test -Dtest=TaskAttachmentControllerIT

# Manual E2E
# 1. Upload a file via POST /api/v1/files/attachments
# 2. Register via POST /api/v1/tasks/{id}/attachments
# 3. Check GET /api/v1/tasks/{id}/attachments returns 1 item
# 4. Check audit events published to Kafka (outbox table)
# 5. Delete via DELETE /api/v1/tasks/{id}/attachments/{attachmentId}
# 6. Verify file-service record deleted
# 7. Frontend: upload file in Attachments tab, see it listed, delete it
```
