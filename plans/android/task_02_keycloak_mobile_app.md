# task_02 — Keycloak `MOBILE_APP` role + `mobile-app` public client

## Goal
Let Android authenticate against the same realm as the webapp with its own client id, own redirect scheme, and a new realm role that services can check to distinguish mobile callers.

## Changes

### Keycloak realm
`docker-images/keycloak/demo-realm.json`
- Add realm role `MOBILE_APP` alongside `ADMIN` / `DEVELOPER` / `WEB_APP` / … (see lines around 11–35 for the pattern).
- Add client `mobile-app`:
  - `publicClient: true`
  - `standardFlowEnabled: true` (auth code)
  - `serviceAccountsEnabled: false`
  - `attributes.pkce.code.challenge.method = "S256"`
  - `redirectUris: ["taskmanager://callback"]`
  - `postLogoutRedirectUris: ["taskmanager://logout"]`
  - `webOrigins: ["+"]`
  - Default client-scopes same as `web-app`.
- Add `MOBILE_APP` to the default realm roles list (or default-role mapper) so every user gets it the same way `WEB_APP` is assigned today.

### Backend role mapping
- `common/src/main/java/com/demo/common/config/JwtAuthConverter.java` (or equivalent) — verify `MOBILE_APP` is not filtered out and is mapped to a Spring authority (`ROLE_MOBILE_APP`).
- `common/src/main/java/com/demo/common/config/SecurityConfig.java` — no change expected; grep for role whitelists and confirm.
- `api-gateway/src/main/resources/application.yml` — no new route yet, but confirm existing routes accept any human-role JWT (WEB_APP or MOBILE_APP).

### Docs
- Add a short note in `docs/keycloak.md` (or create it) explaining the `MOBILE_APP` role and the `mobile-app` client's PKCE/redirect settings.

## Re-import procedure
`docker compose down keycloak && docker compose up -d keycloak` — startup script imports the realm JSON from `docker-images/keycloak/`.

## Touches Android
None (Android chunks reference this client/role starting in task_03).

## Acceptance
- `docker compose up keycloak` → re-import completes without error.
- Browser flow:
  `https://<keycloak>/realms/demo/protocol/openid-connect/auth?client_id=mobile-app&response_type=code&code_challenge=…&code_challenge_method=S256&redirect_uri=taskmanager://callback` lands on login.
- After token exchange, decoded JWT contains `realm_access.roles: [..., "MOBILE_APP", ...]`.
- Existing webapp login still works (no regression).
