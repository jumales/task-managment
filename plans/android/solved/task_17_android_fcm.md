# task_17 — Android FCM integration + deep links

## Goal
Android registers an FCM token on login, surfaces pushes as system notifications, deep-links into task detail, and invalidates tokens on logout. Any open `TaskDetailScreen` auto-refreshes when it sees a push for that task.

## Changes

### Firebase project (developer step)
- Create Firebase project in console; add Android app with package `com.demo.taskmanager`.
- Download `google-services.json` → drop into `android/app/` (gitignored).
- Backend (task_16) uses the same project's service account JSON.

### Gradle
- `android/app/build.gradle.kts` — apply `com.google.gms.google-services` plugin, add `com.google.firebase:firebase-messaging-ktx`.
- `android/build.gradle.kts` — `com.google.gms:google-services` classpath.
- `android/app/src/main/kotlin/com/demo/taskmanager/push/AppFirebaseMessagingService.kt`:
  - `onNewToken(token)` — if logged in, delegate to `DeviceTokenRepository.rotate(oldToken, token)` → `PUT /api/v1/device-tokens/{oldToken}`. Otherwise cache in DataStore `pending_device_token`.
  - `onMessageReceived(message)`:
    - Parse `data.taskId`, `data.changeType`, `data.projectId`.
    - Build `NotificationCompat` with content intent `taskmanager://tasks/{taskId}` via `PendingIntent` + `TaskStackBuilder` (so back navigates to list).
    - Also emit to `PushEventBus.emit(TaskPushMessage(taskId, changeType))` — a `SharedFlow` injected via Hilt — so in-process `TaskDetailViewModel` can refetch.

### `DeviceTokenRepository` (in `:data`)
- `register()` — call `POST /api/v1/device-tokens` with current FCM token + `Build.VERSION_NAME`.
- `rotate(oldToken, newToken)` — `PUT /api/v1/device-tokens/{oldToken}`.
- `unregister(token)` — `DELETE /api/v1/device-tokens/{token}`.

### Hook into auth
- After `AuthManager.handleCallback` succeeds → `DeviceTokenRepository.register()` in a launched coroutine.
- On `AuthManager.logout` → `unregister()` BEFORE clearing local tokens, then `FirebaseMessaging.getInstance().deleteToken()`.

### Deep links
- `AndroidManifest.xml` intent filter on `MainActivity`:
  ```xml
  <intent-filter android:autoVerify="false">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="taskmanager" android:host="tasks"/>
  </intent-filter>
  ```
- NavGraph wires `taskmanager://tasks/{taskId}` → `tasks/{taskId}` route.

### Runtime permissions
- Android 13+: on first login, request `POST_NOTIFICATIONS` via `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission)`.

### Foreground vs background
- Data-only messages (sent by backend per task_16) always invoke `onMessageReceived` → we build the notification ourselves. Foreground updates also emit to `PushEventBus` so currently-open detail refreshes without showing a tray notification.

### Task detail integration
- In `:feature-tasks` `TaskDetailViewModel.init { … }` collect `PushEventBus.flow.filter { it.taskId == currentTaskId }.collect { reload() }`.

## Tests
- Unit: `DeviceTokenRepository.register` succeeds → stores token in DataStore; 500 keeps pending flag for retry on next app start.
- Unit: `AppFirebaseMessagingService.onMessageReceived` emits expected `TaskPushMessage`.
- Instrumented: simulated push (inject into `PushEventBus`) → open detail re-fetches.

## Acceptance
- End-to-end: create task in webapp → Android receives system notification → tap notification opens correct detail → detail auto-refreshes.
- Logout deletes token row server-side (visible via `GET /me` returning empty).
- Reinstall app → old token gets `UNREGISTERED` on next send → row soft-deleted automatically.
