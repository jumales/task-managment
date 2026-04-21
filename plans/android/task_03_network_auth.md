# task_03 — Core network + AppAuth PKCE + token store

## Goal
Produce a `:core-network` module that any feature module can inject. Login through Keycloak via AppAuth, store tokens securely, attach Bearer header on every request, refresh on 401 without duplicate refreshes.

## Changes

### New module `android/core-network/`
- `build.gradle.kts` — Retrofit 2.11, OkHttp 4.12 (+ logging-interceptor), kotlinx-serialization-converter, AppAuth 0.11, AndroidX Security Crypto, Hilt.
- `network/NetworkModule.kt` (Hilt `@Module`, `@InstallIn(SingletonComponent)`) — produces `OkHttpClient`, `Retrofit`, `Json`.
  - `BASE_URL` read from `BuildConfig.BASE_URL`.
  - OkHttp chain: `HttpLoggingInterceptor` (debug only), `AuthInterceptor`, `TokenRefreshAuthenticator`.
- `auth/TokenStore.kt` — wraps `EncryptedSharedPreferences`; fields `accessToken`, `refreshToken`, `idToken`, `expiresAt`.
- `auth/AuthInterceptor.kt` — reads access token and adds `Authorization: Bearer …`. Skips for requests already tagged `NoAuth` (none yet, but leave hook).
- `auth/TokenRefreshAuthenticator.kt` — OkHttp `Authenticator` on 401; single-flight `Mutex`; calls `AppAuth.performTokenRequest` to exchange the refresh token; retries with new bearer. If refresh fails → clear `TokenStore` and emit `AuthEvent.LoggedOut` on a `SharedFlow`.
- `auth/AuthManager.kt` — wraps AppAuth `AuthorizationService`:
  - `login(activity)` → build `AuthorizationRequest` (PKCE S256, client `mobile-app`, redirect `taskmanager://callback`) and launch Custom Tab.
  - `handleCallback(intent)` → exchange code; save tokens.
  - `logout(activity)` → end-session request; clear `TokenStore`.
  - `authState: StateFlow<AuthState>` with `Unauthenticated` / `Authenticated(userId, roles)`.
- `auth/AuthConfig.kt` — `issuer = BuildConfig.KEYCLOAK_ISSUER + "/realms/demo"`, `clientId = "mobile-app"`, redirects as above.

### App wiring
- `android/app/src/main/kotlin/com/demo/taskmanager/auth/LoginActivity.kt` — minimal Compose screen with "Login" button → `AuthManager.login(this)`; receives `RESULT_OK` and forwards to `AuthManager.handleCallback`.
- `AndroidManifest.xml` — register `RedirectUriReceiverActivity` intent-filter for scheme `taskmanager`.

## Tests
- Unit: `AuthInterceptor` attaches header when token present, omits when blank.
- Unit: `TokenRefreshAuthenticator` — two concurrent 401s trigger exactly one refresh call.
- Manual e2e: login against local Keycloak, then call `GET /api/v1/users/me` through Retrofit → 200.

## Acceptance
- `./gradlew :core-network:test` green.
- Manual login round-trip stores non-blank tokens in `TokenStore`; `users/me` call returns current user.
