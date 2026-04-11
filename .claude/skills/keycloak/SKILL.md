---
name: keycloak
description: Reference guide and how-to for managing Keycloak in this project — adding roles, service accounts, users, debugging auth errors, and applying demo-realm.json changes. Use when working on Keycloak config or troubleshooting JWT/auth issues.
user-invocable: false
---
Reference guide for managing Keycloak configuration in this project. Use when adding roles, adding service accounts, modifying user assignments, debugging auth errors, or troubleshooting Keycloak Admin API access from user-service.

---

## How Keycloak is set up

- Runs as `ms-keycloak` in Docker (`docker-compose.yml`), started with `start-dev --import-realm`
- The realm is defined in `docker-images/keycloak/demo-realm.json`
- **`--import-realm` only runs on first boot** — if the realm already exists in Keycloak's embedded H2, the file is skipped on subsequent container starts
- To apply any change to `demo-realm.json`, you must recreate the container (see below)

---

## Applying changes to demo-realm.json

Any edit to `demo-realm.json` (roles, users, service accounts, permissions) requires recreating the Keycloak container so it reimports from scratch:

```bash
docker compose stop keycloak && docker compose rm -f keycloak && docker compose up -d keycloak
```

Then wait for Keycloak to be ready:

```bash
until curl -sf -o /dev/null "http://localhost:8180/realms/demo"; do sleep 3; done && echo "Keycloak ready"
```

Restart any service that authenticates with Keycloak afterward (usually user-service).

---

## Access control model

All requests to any microservice require the `WEB_APP` realm role — this is enforced by `SecurityConfig` in `common`:

```java
.anyRequest().hasRole("WEB_APP")
```

**Who needs the WEB_APP role:**
- All human users (`admin-user`, `dev-user`, and any new users created via user-service)
- Service accounts that call other microservices (e.g. `service-account-notification-service`)
- **Exception**: `service-account-user-service` does NOT need `WEB_APP` — it only calls the Keycloak Admin API, never a microservice endpoint

**Roles in the realm:**

| Role | Purpose | Write access |
|---|---|---|
| `WEB_APP` | Required for all API access (human users + internal services) | — |
| `ADMIN` | Full access to all resources (manage users, projects, etc.) | Yes |
| `DEVELOPER` | Read and write tasks | Yes |
| `QA` | Quality assurance — read and write tasks | Yes |
| `DEVOPS` | DevOps — read and write tasks | Yes |
| `PM` | Project manager — read and write tasks | Yes |
| `SUPERVISOR` | Read-only across all services; POST/PUT/PATCH/DELETE blocked at SecurityConfig | No |

Write access is enforced in `SecurityConfig` via `WRITE_ROLES = {ADMIN, DEVELOPER, QA, DEVOPS, PM}` — any role not in that list is implicitly read-only. Adding a new write-capable role requires updating that constant.

---

## Service accounts and Admin API permissions

`user-service` calls the Keycloak Admin REST API using the `service-account-user-service` client-credentials account. The account needs specific `realm-management` client roles for each operation:

| Operation | Keycloak endpoint | Required realm-management roles |
|---|---|---|
| List users, find by ID/username | `GET /users`, `GET /users/{id}` | `view-users` |
| Create/update/disable users | `POST /users`, `PUT /users/{id}` | `manage-users` |
| List users with a role | `GET /roles/{role}/users` | `view-realm`, `query-users` |
| Fetch role by name | `GET /roles/{role}` | `view-realm` |
| Assign realm role to user | `POST /users/{id}/role-mappings/realm` | `manage-users` |

Current configuration in `demo-realm.json` (must include all four for the `findAll` and `create` flows to work):

```json
{
  "username": "service-account-user-service",
  "serviceAccountClientId": "user-service",
  "clientRoles": {
    "realm-management": ["manage-users", "view-users", "view-realm", "query-users"]
  }
}
```

**Symptom of missing permissions:** user-service returns HTTP 500 with no exception in the log. The Keycloak Admin API is returning 403, which `WebClientResponseException` propagates as an unhandled error.

Verify the service account token's access manually:

```bash
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/demo/protocol/openid-connect/token" \
  -d "client_id=user-service&client_secret=user-service-secret&grant_type=client_credentials" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s -w "\nHTTP %{http_code}" -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8180/admin/realms/demo/roles/WEB_APP/users?max=5"
```

---

## Adding a new realm role

In `demo-realm.json`, add to the `roles.realm` array:

```json
{
  "name": "NEW_ROLE",
  "description": "What this role grants"
}
```

Then assign it to relevant users/service accounts (see below) and recreate Keycloak.

---

## Adding a new service account (new microservice)

When a new microservice needs to call other microservices via JWT, add its service account:

```json
{
  "username": "service-account-<service-name>",
  "enabled": true,
  "serviceAccountClientId": "<service-name>",
  "realmRoles": ["WEB_APP"]
}
```

Also ensure the client entry has `"serviceAccountsEnabled": true`:

```json
{
  "clientId": "<service-name>",
  "serviceAccountsEnabled": true,
  "clientAuthenticatorType": "client-secret",
  "secret": "<service-name>-secret",
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": false
}
```

The `WEB_APP` role is required so the service account's token passes `anyRequest().hasRole("WEB_APP")` when calling any other microservice.

---

## Adding a new human user (dev/test seed user)

```json
{
  "username": "new-user",
  "email": "new@demo.com",
  "firstName": "New",
  "lastName": "User",
  "enabled": true,
  "emailVerified": true,
  "credentials": [
    { "type": "password", "value": "password123", "temporary": false }
  ],
  "realmRoles": ["DEVELOPER", "WEB_APP"]
}
```

---

## Troubleshooting

### user-service returns 500, no exception in logs
The Keycloak Admin API is returning an error that isn't caught. Common causes:
1. **Missing service account permission** — verify with the `curl` command above; if you get 403, add the missing `realm-management` role and recreate Keycloak
2. **Role does not exist** — `findAll()` calls `/roles/WEB_APP/users`; if the role was just added to `demo-realm.json` but the container wasn't recreated, Keycloak still uses the old realm (returns 404 → uncaught → 500)

### 403 on API endpoints (Spring Security)
The JWT does not carry the `WEB_APP` realm role. Either:
- The user was created before the `WEB_APP` role existed and wasn't assigned it
- The service account is missing `"realmRoles": ["WEB_APP"]` in `demo-realm.json`
- Keycloak was not recreated after the realm change

### Token endpoint returns error
Check client secret matches between `demo-realm.json` and `application.yml`. For user-service the secret is `user-service-secret` in both places.
