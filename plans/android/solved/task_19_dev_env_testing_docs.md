# task_19 — Dev environment + testing docs

## Goal
A new developer can go from fresh clone to running the Android app against localhost within 30 minutes, on either emulator or real device.

## Changes

### `android/README.md`
- Stack summary (Kotlin / Compose / Hilt / Retrofit / AppAuth / Firebase).
- Module map diagram.
- "Getting started" with the quick path (emulator flavor).

### `docs/android-dev.md`

#### 1. Prerequisites
- Android Studio Hedgehog+ (2024.x).
- JDK 17 (matches `.sdkmanrc` of the backend services).
- Android SDK 34, build-tools 34.0.0.
- Kotlin 2.0 bundled with AGP 8.5.
- Google Firebase account; a Firebase project.

#### 2. One-time setup
- `cd android && cp local.properties.example local.properties` — fill in:
  - `sdk.dir=/Users/<you>/Library/Android/sdk`
  - `LAN_BASE_URL=http://192.168.x.y:8080` (only if using `device` flavor).
- Create Firebase project → add Android app with package `com.demo.taskmanager` → download `google-services.json` → place in `android/app/`.
- For FCM backend (task_16): export service-account JSON → `export FIREBASE_CREDENTIALS_JSON="$(cat ~/Downloads/firebase-adminsdk.json)"`.

#### 3. Running locally — emulator (preferred dev loop)
- `./scripts/start-dev.sh` (backend). Keycloak on 8081, api-gateway on 8080.
- Launch Android emulator `Pixel_7_API_34`.
- `./gradlew :app:installEmulatorDebug`.
- Login with `admin / admin`. Emulator reaches host via `10.0.2.2`.

#### 4. Running on a real device — same Wi-Fi
- Ensure laptop firewall allows inbound 8080 / 8081.
- Find laptop IP — `ipconfig getifaddr en0`.
- Set `LAN_BASE_URL=http://192.168.x.y:8080` in `android/local.properties`.
- Keycloak must be reachable at the same host — export `KC_HOSTNAME_URL=http://192.168.x.y:8081` in docker-compose Keycloak service and restart.
- Add the LAN URL to the `mobile-app` client's **Valid redirect URIs** and **Web origins** in the realm JSON.
- `./gradlew :app:installDeviceDebug` with phone USB-connected (USB debugging ON).
- Alternative: `adb reverse tcp:8081 tcp:8081` + `adb reverse tcp:8080 tcp:8080` lets the phone hit `10.0.2.2`-style localhost without touching firewall.

#### 5. Running on a real device — remote (ngrok / Cloudflare)
- Install ngrok, auth.
- Two tunnels: `ngrok http 8080` (API) + `ngrok http 8081` (Keycloak).
- Set the Keycloak client's valid redirect URI to include the ngrok HTTPS URL; set `KC_HOSTNAME_URL` to the Keycloak ngrok URL.
- Use `tunnel` flavor with those URLs wired into `BuildConfig`.
- Certificate = real HTTPS; avoids `ERR_CLEARTEXT_NOT_PERMITTED`.

#### 6. Testing plan
- **Unit tests:** `cd android && ./gradlew :data:test :domain:test :core-network:test`. Feature modules: `:feature-tasks:test`, etc.
- **Instrumented tests:** `./gradlew :app:pixel6api33EmulatorDebugAndroidTest` — uses Gradle Managed Devices (downloads image once, runs headless).
- **Manual smoke test checklist** per release:
  1. Login.
  2. Create task → appears in webapp.
  3. Add comment → visible in webapp.
  4. Edit task from webapp → Android push notification arrives → tap → detail refreshes.
  5. Logout → token removed server-side (`GET /api/v1/device-tokens/me` empty).

#### 7. Troubleshooting
- **401 loop:** check system clock skew; check `iss` claim matches `KEYCLOAK_ISSUER`; for LAN flavor verify `KC_HOSTNAME_URL` is set.
- **`ERR_CLEARTEXT_NOT_PERMITTED`:** only debug flavors allow cleartext; production APKs must use HTTPS.
- **FCM delivers nothing:** verify `FCM_ENABLED=true` on backend; `google-services.json` package id matches app; device token visible in `GET /api/v1/device-tokens/me`.
- **Push received but no notification:** Android 13+ missing `POST_NOTIFICATIONS` — revisit app settings → Notifications → allow.
- **Login spinning forever:** Keycloak redirect URI mismatch — inspect browser URL; add that URL to `mobile-app` client's Valid Redirect URIs.

#### 8. Keycloak reachability matrix

| Flavor   | Keycloak URL to use            | Redirect URI to whitelist          |
|----------|--------------------------------|------------------------------------|
| emulator | `http://10.0.2.2:8081`         | `taskmanager://callback`           |
| device   | `http://<LAN_IP>:8081`         | `taskmanager://callback`           |
| tunnel   | `https://<keycloak-ngrok>`     | `taskmanager://callback`           |

### `CHANGES.md`
- Add entry under `[Unreleased]`:
  - `Added: Android client with FCM push notifications and Keycloak MOBILE_APP role (see plans/android/).`

## Acceptance
- Fresh checkout + blank macOS → following doc end-to-end produces a running Android app against localhost in under 30 minutes.
- README + docs reviewed by pre-pr-docs-reviewer before PR merges.
