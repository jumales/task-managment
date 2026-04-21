# task_16 — **Backend only:** notification-service FCM publisher + device-token endpoints

## Goal
Teach notification-service to deliver FCM pushes alongside existing STOMP pushes. Store device tokens per user. Fan out on every Kafka task change.

**No Android code in this PR.** Android FCM client comes in task_17.

## Changes

### New entity + migration
- `notification-service/src/main/java/com/demo/notification/model/DeviceToken.java`:
  - Fields: `id UUID` (`@UuidGenerator(style = TIME)`), `userId UUID`, `token String`, `platform enum { ANDROID, IOS }`, `appVersion String`, `createdAt Instant`, `lastSeenAt Instant`, `deletedAt Instant` (nullable).
- `notification-service/src/main/resources/db/migration/V{next}__add_device_tokens.sql`:
  - Table `device_tokens` with columns above.
  - Partial unique index per CLAUDE.md convention:
    `CREATE UNIQUE INDEX uidx_device_tokens_user_token ON device_tokens(user_id, token) WHERE deleted_at IS NULL;`
- `DeviceTokenRepository extends JpaRepository<DeviceToken, UUID>`:
  - `findByUserIdAndDeletedAtIsNull(UUID userId)`
  - `findByTokenAndDeletedAtIsNull(String token)`
  - `existsByUserIdAndTokenAndDeletedAtIsNull(UUID, String)` (uniqueness helper per code-style rules).

### REST controller
- `notification-service/src/main/java/com/demo/notification/controller/DeviceTokenController.java`:
  - `POST /api/v1/device-tokens` — `{ token, platform, appVersion }` → creates or revives soft-deleted row for `(userId, token)`. 201 Created.
  - `PUT /api/v1/device-tokens/{oldToken}` — `{ newToken }` → rotate.
  - `DELETE /api/v1/device-tokens/{token}` — soft-delete for current user.
  - `GET /api/v1/device-tokens/me` — list caller's active devices.
  - Auth: caller must be authenticated; endpoints operate on `@AuthenticationPrincipal Jwt.sub` → `userId`.
- `DeviceTokenService` — business logic; reuse `getOrThrow` / `DuplicateResourceException` patterns from CLAUDE.md.
- `DeviceTokenRequest`, `DeviceTokenResponse` DTOs in `common/src/main/java/com/demo/common/dto/` (since `:data` Android chunk will mirror them in task_17).

### FCM publisher
- `notification-service/src/main/java/com/demo/notification/service/FcmPushService.java`:
  - Depends on Firebase Admin SDK (`com.google.firebase:firebase-admin:9.3.0`).
  - `@PostConstruct` initializes `FirebaseApp` from JSON in `FIREBASE_CREDENTIALS_JSON` env var (or base64-decoded `FIREBASE_CREDENTIALS_B64`).
  - `notifyUsers(Set<UUID> userIds, TaskChangeType changeType, UUID taskId, UUID projectId)`:
    - Look up active tokens per user; build data-only `Message` with `{taskId, changeType, projectId}`.
    - Send via `MulticastMessage` batched at 500 (FCM limit).
    - On `UNREGISTERED` / `INVALID_ARGUMENT` → soft-delete the token row.
  - Runs in its own thread pool (`@Async` or executor) so Kafka consumer isn't blocked.

### Kafka consumer extension
- `notification-service/src/main/java/com/demo/notification/consumer/TaskEventNotificationConsumer.java`:
  - After current STOMP `taskPushService.send(...)` call, compute affected user IDs (assignee + participants + watchers — reuse existing `UserClientHelper`).
  - Call `fcmPushService.notifyUsers(affectedUserIds, event.changeType(), event.taskId(), event.projectId())`.
  - Dedup with existing `ProcessedEventService` pattern — already in place after the outbox idempotency fix.

### Config
- `notification-service/src/main/resources/application.yml` or `config-repo/notification-service.yml`:
  ```yaml
  fcm:
    enabled: ${FCM_ENABLED:false}
    credentials-json: ${FIREBASE_CREDENTIALS_JSON:}
    dry-run: ${FCM_DRY_RUN:false}
  ```
  - When `enabled=false`, `FcmPushService` is a no-op bean; service still boots without Firebase credentials.

### api-gateway
- `api-gateway/src/main/resources/application.yml` — add route for `/api/v1/device-tokens/**` → `lb://notification-service`.
- JWT scope check: `MOBILE_APP` OR `WEB_APP` (both human clients can register tokens; webapp can use this path if we ever add web push).

### Tests
- `notification-service/src/test/java/com/demo/notification/DeviceTokenControllerIT.java` — full CRUD with Testcontainers Postgres.
- `FcmPushServiceTest` — mock `FirebaseMessaging` via Mockito; assert batching and soft-delete on `UNREGISTERED`.
- `TaskEventNotificationConsumerIT` — extend existing test so a consumed event triggers a call to the mocked `FcmPushService` with expected `userIds`.

### Postman + docs
- `postman/notification-service.postman_collection.json` — add requests for 4 new endpoints with test scripts per CLAUDE.md "Postman Collections" rule.
- `docs/services/notification-service.mdx` — add section "FCM pushes to mobile clients" explaining the fan-out + soft-delete on invalid-token.

## Acceptance
- `mvn -pl notification-service verify` green.
- `POST /api/v1/device-tokens` via Postman creates row; `GET /me` returns it.
- Trigger a task update (via task-service in dev env) → `FcmPushService` log line shows send attempt to correct tokens with correct payload (dry-run mode = true during testing).
- Rotating token via `PUT /{oldToken}` soft-deletes old row and inserts new — `active row count per (userId, token)` stays at 1.
- Submitting an `UNREGISTERED` mock response to FCM → row is soft-deleted.
