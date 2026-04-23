# task_04 — Data layer + API service interfaces from Postman

## Goal
Produce a `:data` module with one Retrofit interface per backend service and Kotlin DTOs/enums that mirror the contracts. Contract source of truth: the Postman collections + backend `common` DTOs.

## Changes

### New module `android/data/`
- `build.gradle.kts` — depends on `:core-network` + `:domain` + Retrofit + kotlinx-serialization.
- `api/TaskApi.kt` — tasks, projects, phases, comments, participants, planned-work, booked-work, timelines, notification-templates. Methods derived from `postman/task-service.postman_collection.json`.
- `api/UserApi.kt` — `/users`, `/users/me`, `/users/{id}/roles`, avatar/language endpoints (`postman/user-service.postman_collection.json`).
- `api/FileApi.kt` — `POST /files`, `GET /files/{id}/presigned-url`, `GET /files/{id}/download`, `GET/POST/DELETE /tasks/{taskId}/attachments`.
- `api/SearchApi.kt` — `/search/tasks`, `/search/users`.
- `api/ReportingApi.kt` — `/reports/my-tasks`, `/reports/hours/by-task`, `/reports/hours/by-project`, `/reports/hours/detailed`, `/reports/tasks/open-by-project`.
- `api/NotificationApi.kt` — `/notifications/tasks/{taskId}`. (Device-token endpoints added in task_17 together with FCM.)

### DTOs (kotlinx-serialization)
`data/src/main/kotlin/com/demo/taskmanager/data/dto/` — one file per aggregate:
- `TaskDto`, `TaskFullDto`, `TaskCreateRequest`, `TaskUpdateRequest`, `TaskStatus` enum
- `ProjectDto`, `PhaseDto`
- `CommentDto`, `CommentCreateRequest`
- `ParticipantDto`
- `PlannedWorkDto`, `BookedWorkDto`, `WorkCreateRequest`, `WorkType` enum
- `TimelineDto`
- `UserDto`, `UserRoleDto`
- `AttachmentDto`, `PresignedUrlDto`
- `SearchHitDto`
- `MyTaskReportDto`, `HoursByTaskDto`, `HoursByProjectDto`, `HoursDetailedDto`, `ProjectOpenTaskCountDto`
- `NotificationTemplateDto`

Shapes mirror `common/src/main/java/com/demo/common/dto/*.java` and service-local DTOs. Use `@SerialName` when field name differs; enforce nullability as on backend.

### Enums
Copy as Kotlin enum class from backend:
- `common/src/main/java/com/demo/common/event/TaskChangeType.java` → `TaskChangeType`
- `common/src/main/java/com/demo/common/dto/TaskStatus.java`
- `common/src/main/java/com/demo/common/dto/TaskCompletionStatus.java` (if exists per CLAUDE.md guidance)
- `common/src/main/java/com/demo/common/dto/WorkType.java`
- Role names — keep as `String` constants (roles are Keycloak-sourced).

### Shared error handling
- `common/ApiErrorResponse.kt` — shape `{ message, status, timestamp, path }`.
- `common/ApiException.kt`.
- Retrofit `CallAdapter` returning `Result<T>` (either wrap in suspend extension or use a `NetworkResult` sealed class).

### Repositories (empty stubs)
`data/src/main/kotlin/com/demo/taskmanager/data/repo/` — one class per API, just delegating. Each feature chunk fills in caching / mapping.

## Tests
- `:data:test` — smoke serialization tests: round-trip a known Postman response body → DTO → JSON and assert no field loss.

## Acceptance
- `./gradlew :data:assemble` green.
- `./gradlew :data:test` green.
- All 7 APIs compile; all DTO enums match backend values.
