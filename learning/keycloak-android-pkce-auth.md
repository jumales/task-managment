# Keycloak ↔ Android Authentication — How It Works

## Overview

The Android app uses the **AppAuth library** to perform an **OAuth2 PKCE authorization code flow**
against Keycloak. OkHttp handles transparent token refresh on 401. The backend (Spring Boot)
validates JWTs via Keycloak's public JWKS endpoint.

---

## Full Flow

```
Android App                    Keycloak (8180)             Backend (Spring Boot)
     │                               │                              │
     │── 1. Build PKCE request ──────▶                              │
     │   authorizationEndpoint:                                     │
     │   http://10.0.2.2:8180/...                                   │
     │                                                              │
     │◀─ 2. Login page (browser) ────│                              │
     │── 3. User submits credentials ▶                              │
     │◀─ 4. Redirect to taskmanager://callback?code=xxx             │
     │                               │                              │
     │── 5. Exchange code for tokens ▶                              │
     │   (tokenEndpoint via AppAuth)  │                              │
     │◀─ 6. access_token + refresh_token + id_token                 │
     │                               │                              │
     │── 7. GET /tasks  ─────────────────────────────────────────▶  │
     │      Authorization: Bearer <access_token>                    │
     │                               │                              │
     │                               │◀─ 8. Fetch JWKS ────────────│
     │                               │── 9. Return public keys ────▶│
     │                               │   (cached; re-fetched on     │
     │                               │    unknown kid)              │
     │◀─ 10. 200 OK ─────────────────────────────────────────────── │
```

---

## Key Components

### Android Side

| Class | Responsibility |
|---|---|
| `AuthConfig` | Holds `issuerUri`, `clientId`, redirect URIs |
| `AuthManager` | Builds PKCE login/logout intents, handles code exchange callback |
| `TokenStore` | Persists tokens in `EncryptedSharedPreferences` (Android Keystore) |
| `AuthInterceptor` | OkHttp interceptor — attaches `Authorization: Bearer` to every request |
| `TokenRefreshAuthenticator` | OkHttp authenticator — called on 401; refreshes token via `TokenRefresher` |
| `AppAuthTokenRefresher` | Calls Keycloak token endpoint with the refresh token |
| `HttpAllowedConnectionBuilder` | AppAuth `ConnectionBuilder` that permits plain HTTP (dev only) |

### Backend Side

| Component | Responsibility |
|---|---|
| `SecurityConfig` (common) | Configures Spring Security as a JWT resource server |
| `JwtAuthConverter` (common) | Extracts `realm_access.roles` and `rights` claims as `GrantedAuthority` |
| `jwk-set-uri` config | Points Spring Security to Keycloak's JWKS endpoint for signature validation |

---

## PKCE Flow Detail

**Why PKCE?** The `mobile-app` Keycloak client is `publicClient: true` — it has no client secret
(secrets can't be kept secure in a mobile app). PKCE replaces the client secret:

1. App generates a random `code_verifier`
2. App sends `code_challenge = SHA256(code_verifier)` in the authorization request
3. Keycloak stores the challenge
4. On code exchange, app sends the original `code_verifier`
5. Keycloak verifies `SHA256(code_verifier) == stored challenge` — proves the exchanger is the same app that started the flow

---

## Token Refresh (Transparent)

OkHttp's `Authenticator` interface is called automatically on every `401` response:

```
Request → 401
  └─ TokenRefreshAuthenticator.authenticate()
       ├─ Guard: priorResponse?.code == 401 → give up (return null → logged out)
       ├─ No refresh token → give up
       └─ Mutex (prevents concurrent refreshes)
            ├─ Token already refreshed by another thread → retry with new token
            └─ Call AppAuthTokenRefresher.refresh()
                 ├─ Success → store new tokens → retry original request
                 └─ Failure → clear TokenStore → emit AuthEvent.LoggedOut
```

**The retry guard is critical.** Without `if (response.priorResponse?.code == 401) return null`,
OkHttp calls `authenticate()` on every retry indefinitely (up to its 20-follow-up limit) and then
throws `ProtocolException: Too many follow-up requests`.

---

## JWT Validation on the Backend

Spring Security is configured with `jwk-set-uri` (not `issuer-uri`). This distinction matters:

| Config | What it validates |
|---|---|
| `issuer-uri` | Fetches OIDC discovery; validates JWT signature **and** `iss` claim |
| `jwk-set-uri` | Fetches JWKS directly; validates JWT signature only (no `iss` check) |

**Why `jwk-set-uri` for dev?** The `iss` claim in a token equals the URL Keycloak was reached
through when it issued the token. The Android emulator reaches Keycloak via `10.0.2.2:8180`;
the backend reaches it via `localhost:8180`. These produce different `iss` values but the same
signing keys — so signature validation passes but `iss` validation fails.

Tokens are still cryptographically validated. The `exp`, `nbf`, and `sub` claims are also
validated by Spring Security regardless of `issuer-uri` vs `jwk-set-uri`.

---

## Keycloak Client Configuration (`demo-realm.json`)

```json
{
  "clientId": "mobile-app",
  "publicClient": true,
  "standardFlowEnabled": true,
  "redirectUris": ["taskmanager://callback"],
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "taskmanager://logout"
  }
}
```

The `taskmanager://` scheme is a custom deep link scheme registered in `AndroidManifest.xml`.
AppAuth intercepts the redirect after login/logout via `RedirectUriReceiverActivity`.

---

## Hostname Problem in Dev (Emulator)

**Problem:** Keycloak dev mode derives the `iss` claim from the request's `Host` header.
- Android emulator → `10.0.2.2:8180` → token `iss = http://10.0.2.2:8180/realms/demo`
- Backend → `localhost:8180` → expects `iss = http://localhost:8180/realms/demo`
- Mismatch → **401 on every request**

**What doesn't work:**
- `KC_HOSTNAME=http://localhost:8180` — Keycloak 24 double-prefixes `http://` → malformed issuer
- `KC_HOSTNAME=localhost` + `KC_HOSTNAME_PORT=8180` — fixes `iss` but Keycloak rewrites login
  page form actions to `localhost:8180`, which the emulator browser can't reach

**What works:** Switch backend from `issuer-uri` → `jwk-set-uri`. Signature is validated;
`iss` is not checked. Works for all client flavors (emulator, physical device, tunnel).

---

## Device Flavors

| Flavor | `KEYCLOAK_ISSUER` (Android) | Reaches Keycloak via |
|---|---|---|
| `emulator` | `http://10.0.2.2:8180/realms/demo` | Host loopback alias |
| `device` | `http://<LAN_IP>:8180/realms/demo` | LAN (from `local.properties`) |
| `tunnel` | `https://<tunnel>/realms/demo` | ngrok / Cloudflare tunnel |

All flavors use the same backend config. The `jwk-set-uri` fix makes them all work without
per-flavor backend configuration.

---

## AppAuth + HTTP (plain HTTP in dev)

AppAuth's `DefaultConnectionBuilder` rejects `http://` URLs at the Java level — it is
hard-coded to only allow `https://`. This check happens **before** Android's
`network_security_config.xml`.

Fix: `HttpAllowedConnectionBuilder` — a custom `ConnectionBuilder` that opens a plain
`HttpURLConnection`. It is injected into `AppAuthConfiguration` (for `AuthorizationService`)
and passed explicitly to `AuthorizationServiceConfiguration.fetchFromIssuer` when needed.

`network_security_config.xml` still applies to OkHttp calls. The debug overlay at
`src/debug/res/xml/network_security_config.xml` permits cleartext for all hosts.
Production (`src/main`) allows HTTPS only.
