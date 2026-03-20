# Getting a Bearer Token from Keycloak for Postman

## Context

The `e2e-client` in the `demo` realm is configured with Direct Access Grants (Resource Owner
Password flow), which allows fetching a token directly without a browser redirect.
Use this for Postman testing and end-to-end tests.

---

## Option 1 — Terminal

```bash
curl -s -X POST http://localhost:8180/realms/demo/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=e2e-client" \
  -d "client_secret=e2e-secret" \
  -d "username=admin-user" \
  -d "password=admin123" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4
```

Copy the printed value and paste it into Postman's `bearerToken` environment variable.

---

## Option 2 — Postman request

Create a POST request:

- **URL:** `http://localhost:8180/realms/demo/protocol/openid-connect/token`
- **Body:** `x-www-form-urlencoded`

| Key             | Value       |
|-----------------|-------------|
| `grant_type`    | `password`  |
| `client_id`     | `e2e-client`|
| `client_secret` | `e2e-secret`|
| `username`      | `admin-user`|
| `password`      | `admin123`  |

Copy `access_token` from the response into the `bearerToken` environment variable.

---

## Available test users

| Username     | Password   | Role        |
|--------------|------------|-------------|
| `admin-user` | `admin123` | `ADMIN`     |
| `dev-user`   | `dev123`   | `DEVELOPER` |

---

## Key lesson

Tokens expire after 5 minutes (`accessTokenLifespan: 300` in demo-realm.json).
Re-run the curl command or Postman request to get a fresh token when requests start returning 401.
