# Task Manager — Android client

Kotlin / Jetpack Compose mobile client for the Task Management platform.

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.51 |
| Networking | Retrofit 2 + OkHttp |
| Auth | AppAuth-Android (PKCE / Keycloak) |
| Push notifications | Firebase Cloud Messaging (FCM) |
| Build system | Gradle 8 / AGP 8.5 |

## Module map

```
android/
├── app/                  Entry point, navigation graph, Hilt component
├── core-network/         Retrofit setup, token store, auth manager, FCM
├── core-ui/              Shared composables, theme, design tokens
├── data/                 Repository implementations, API interfaces
├── domain/               Use-case interfaces and domain models
├── feature-tasks/        Task list, detail, create/edit screens
├── feature-projects/     Project list and detail screens
├── feature-users/        User list and role editor
├── feature-work/         Booked-work log screen
├── feature-attachments/  File attachment viewer/uploader
├── feature-reports/      Reporting screen
├── feature-search/       Cross-entity search screen
└── feature-config/       App settings and template editor
```

## Getting started (emulator — quickest path)

```bash
# 1. Start the backend stack
./scripts/start-dev.sh          # api-gateway :8080, Keycloak :8180

# 2. Copy and fill local.properties
cd android && cp local.properties.example local.properties
# sdk.dir is auto-filled by Android Studio on first open

# 3. Place google-services.json in android/app/
# (download from your Firebase project console)

# 4. Install on a running Pixel_7_API_34 emulator
./gradlew :app:installEmulatorDebug

# 5. Log in with admin / admin
```

For real-device and remote-tunnel setups, see [docs/development/android-dev.mdx](../docs/development/android-dev.mdx).
