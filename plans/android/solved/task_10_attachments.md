# task_10 — Attachments (upload + download via presigned URL)

## Goal
Reusable uploader / list composable used by task attachments AND user avatars (task_12).

## Changes

### New module `android/feature-attachments/`
- `build.gradle.kts` — depends on `:core-ui`, `:data`, OkHttp, Coil, Hilt. Adds `androidx.activity:activity-compose` for `ActivityResultContracts.GetContent`.
- `upload/FileUploader.kt`:
  - Input: Android `Uri` + bucket name (`attachments` or `avatars` — values in `file-service/src/main/resources/application.yml`).
  - Reads size, validates against bucket's `max-size-bytes` and `allowed-types` (config fetched once per session).
  - Multipart `POST /api/v1/files/{bucket}` (file-service) → returns `fileId`.
  - Emits `Flow<UploadProgress>` using OkHttp `RequestBody` wrapper.
- `upload/AttachmentList.kt` — generic Composable that takes a list of `AttachmentDto`, delete callback, download callback.
- `download/Downloader.kt`:
  - `GET /api/v1/files/{id}/presigned-url` → string URL.
  - Hand to `DownloadManager` with `Request.setDestinationInExternalFilesDir` → notifies on complete; opens via `Intent.ACTION_VIEW`.
- `ui/UploadProgressDialog.kt`.

### Task attachments integration
- In `:feature-tasks` `TaskDetailScreen` — Attachments tab uses `AttachmentList`:
  - `GET /api/v1/tasks/{taskId}/attachments` for list.
  - `POST /api/v1/tasks/{taskId}/attachments` wires `fileId` to task after upload.
  - `DELETE /api/v1/tasks/{taskId}/attachments/{attachmentId}` on remove.

### Permissions
- Android 13+ `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` prompt as needed. Use `ActivityResultContracts.PickVisualMedia` (photo picker) to avoid runtime perms where possible.
- Downloads go to `Environment.DIRECTORY_DOWNLOADS` via `MediaStore.Downloads` on Q+ to avoid WRITE_EXTERNAL_STORAGE.

## Tests
- Unit: `FileUploader` rejects oversize file (uses test config with `max-size-bytes=10`).
- Instrumented: upload a 500KB PNG → progress dialog reaches 100%; row appears in list.

## Acceptance
- Upload 5MB PDF → visible in webapp.
- Download round-trip opens in system viewer.
- Avatar upload from profile (task_12) reuses `FileUploader` with `avatars` bucket.
