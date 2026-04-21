# Android App — Execution Plan

Native Jetpack Compose Android app mirroring the webapp (Dashboard excluded), living in `android/` at repo root. Realtime updates via FCM push fed by `notification-service`. Keycloak realm gains a `MOBILE_APP` role and a `mobile-app` public client.

Each `task_XX_*.md` file is one PR-sized chunk. Execute in numeric order — chunks are dependency-ordered. `task_16` is a **backend-only** chunk; all others touch the Android project (with some also touching backend for auth/route wiring).

## Stack

- Kotlin 2.0, Jetpack Compose (Material 3), min SDK 26, target SDK 34.
- Hilt · Retrofit + OkHttp + kotlinx-serialization · Paging 3 · Coil.
- AppAuth-android (Keycloak PKCE S256).
- Firebase Cloud Messaging.
- `vico` for report charts.
- Tests: JUnit5 + MockK + Turbine + kotlinx-coroutines-test; Compose UI Test + Gradle Managed Devices.

## Multi-module layout

```
android/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
  local.properties                 (gitignored — BASE_URL, KEYCLOAK_URL, FCM sender)
  app/                             entrypoint, DI wiring, NavHost, FCM service
  core-network/                    OkHttp, Retrofit, AppAuth, EncryptedSharedPrefs token store
  core-ui/                         theme, shared Composables, i18n (en/hr)
  data/                            Retrofit APIs, DTOs, repositories, mappers
  domain/                          pure Kotlin models + use-cases
  feature-tasks, feature-work, feature-projects, feature-users,
  feature-search, feature-reports, feature-config, feature-attachments
```

## API base URL strategy (three build flavors)

| Flavor     | BASE_URL                        | KEYCLOAK_URL                    | Use case                       |
|------------|---------------------------------|---------------------------------|--------------------------------|
| `emulator` | `http://10.0.2.2:8080`          | `http://10.0.2.2:8081`          | Android emulator on laptop     |
| `device`   | `http://<LAN_IP>:8080`          | `http://<LAN_IP>:8081`          | Real phone on same Wi-Fi       |
| `tunnel`   | `https://*.ngrok-free.app`      | `https://*.ngrok-free.app`      | Remote device, realistic HTTPS |

`network_security_config.xml` permits cleartext for `10.0.2.2` + `192.168.0.0/16` in debug flavors only.

## FCM token lifecycle

- **Register** after AppAuth login → `POST /api/v1/device-tokens { token, platform, appVersion }`.
- **Refresh** in `FirebaseMessagingService.onNewToken` → `PUT /api/v1/device-tokens/{oldToken}`.
- **Unregister** on logout → `DELETE /api/v1/device-tokens/{token}` + `FirebaseMessaging.deleteToken()`.
- Server soft-deletes on `NotRegistered` / `InvalidRegistration`. Uniqueness key `(user_id, token)`.

## Chunk index

| #   | File                                                        | Scope         |
|-----|-------------------------------------------------------------|---------------|
| 01  | [task_01_scaffold.md](task_01_scaffold.md)                   | Android       |
| 02  | [task_02_keycloak_mobile_app.md](task_02_keycloak_mobile_app.md) | Backend (Keycloak + gateway) |
| 03  | [task_03_network_auth.md](task_03_network_auth.md)           | Android       |
| 04  | [task_04_data_api.md](task_04_data_api.md)                   | Android       |
| 05  | [task_05_domain_navigation.md](task_05_domain_navigation.md) | Android       |
| 06  | [task_06_tasks_list.md](task_06_tasks_list.md)               | Android       |
| 07  | [task_07_task_detail_comments.md](task_07_task_detail_comments.md) | Android |
| 08  | [task_08_task_create_edit.md](task_08_task_create_edit.md)   | Android       |
| 09  | [task_09_work_logging.md](task_09_work_logging.md)           | Android       |
| 10  | [task_10_attachments.md](task_10_attachments.md)             | Android       |
| 11  | [task_11_projects_phases.md](task_11_projects_phases.md)     | Android       |
| 12  | [task_12_users_profile.md](task_12_users_profile.md)         | Android       |
| 13  | [task_13_search.md](task_13_search.md)                       | Android       |
| 14  | [task_14_reports.md](task_14_reports.md)                     | Android       |
| 15  | [task_15_notification_templates.md](task_15_notification_templates.md) | Android |
| 16  | [task_16_backend_fcm_publisher.md](task_16_backend_fcm_publisher.md) | **Backend only** |
| 17  | [task_17_android_fcm.md](task_17_android_fcm.md)             | Android       |
| 18  | [task_18_tests.md](task_18_tests.md)                         | Android + CI  |
| 19  | [task_19_dev_env_testing_docs.md](task_19_dev_env_testing_docs.md) | Docs   |

## End-to-end acceptance (after task_17)

1. `scripts/start-dev.sh` up.
2. Install Android on emulator, login as `admin/admin`.
3. In webapp: create a task.
4. Android receives FCM push.
5. Tap notification → task detail opens.
6. Add comment from Android → webapp's open detail receives STOMP push with that comment.
