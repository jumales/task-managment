# task_01 — Android scaffold + Gradle + Compose baseline

## Goal
Create the Android Gradle project at `android/` with all shared infra (build flavors, Hilt, Compose) so every later chunk can just add modules.

## Changes
- `android/settings.gradle.kts` — module `include` lines (app, core-network, core-ui, data, domain; feature modules added in later tasks).
- `android/build.gradle.kts` + `android/gradle/libs.versions.toml` — Kotlin 2.0, AGP 8.5+, Compose BoM, Hilt 2.52, Retrofit 2.11, OkHttp 4.12, kotlinx-serialization-json 1.7, kotlinx-serialization-converter 1.0, AppAuth 0.11, Firebase BoM (stubbed — no `google-services.json` in this chunk), Coil 2.7, Paging 3.3, AndroidX Security Crypto 1.1.0-alpha, JUnit5 5.11, MockK 1.13, Turbine 1.2.
- `android/app/build.gradle.kts` — three product flavors `emulator` / `device` / `tunnel` each exposing `BuildConfig.BASE_URL` + `BuildConfig.KEYCLOAK_ISSUER`.
- `android/app/src/main/res/xml/network_security_config.xml` — cleartext permitted for `10.0.2.2`, `192.168.0.0/16`, and `127.0.0.1`; debug-only.
- `android/app/src/main/AndroidManifest.xml` — `networkSecurityConfig`, `INTERNET`, single `MainActivity` hosting Compose.
- `android/app/src/main/kotlin/com/demo/taskmanager/MainActivity.kt` — `setContent { AppTheme { Text("Hello") } }`.
- `android/app/src/main/kotlin/com/demo/taskmanager/TaskManagerApp.kt` — `@HiltAndroidApp` class.
- `android/.gitignore` — `local.properties`, `build/`, `.gradle/`, `*.iml`, `app/google-services.json`.
- `android/local.properties.example` — template with `LAN_BASE_URL=` blank.

## Not in scope
No screens, no API clients, no Keycloak plumbing yet. Firebase dependency is declared but no plugin applied.

## Touches backend
None.

## Acceptance
- `cd android && ./gradlew :app:assembleEmulatorDebug` green from a clean checkout.
- App launches in emulator and shows "Hello".
- `./gradlew :app:lintEmulatorDebug` green.
