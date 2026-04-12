# Finding #14 — Fix unbounded Keycloak user fetch used for pagination count

## Status
UNRESOLVED

## Severity
MEDIUM — `GET /api/v1/users` fetches ALL Keycloak users into memory to compute the total count

## Context
`KeycloakUserClient.findAll(Pageable)` makes two WebClient calls to Keycloak:
1. A paginated call to fetch the requested page of users (`first`, `max`, role filter)
2. A second call with `max: Integer.MAX_VALUE` to fetch ALL users, then `stream().count()` in Java

The second call loads the entire user population to count them. For large organizations this
returns a massive JSON payload. Keycloak has a dedicated count endpoint that should be used instead.

## Root Cause
`user-service/src/main/java/com/demo/user/keycloak/KeycloakUserClient.java` lines 93–100:
```java
List<Map<String, Object>> allForCount = webClient.get()
        .uri(u -> u.path("/roles/" + ROLE_WEB_APP + "/users")
                .queryParam("max", Integer.MAX_VALUE)
                .build())
        .retrieve()
        .bodyToFlux(MAP_TYPE)
        .collectList()
        .block();
long totalCount = allForCount.stream()
        .filter(u -> Boolean.TRUE.equals(u.get("enabled")))
        .count();
```

## Files to Modify

### `user-service/src/main/java/com/demo/user/keycloak/KeycloakUserClient.java`
Replace the count fetch with Keycloak's count endpoint:

```java
// Use: GET /admin/realms/{realm}/roles/{roleName}/users/count
// Available in Keycloak 21+ (project uses Keycloak via docker-compose)

Long totalCount = webClient.get()
        .uri(u -> u.path("/roles/" + ROLE_WEB_APP + "/users/count").build())
        .retrieve()
        .bodyToMono(Long.class)
        .block();
```

Then pass to the `PageImpl`:
```java
return new PageImpl<>(users, pageable, totalCount != null ? totalCount : 0L);
```

Remove the old `allForCount` list and the `stream().filter().count()` chain entirely.

## Verification
1. Call `GET /api/v1/users?page=0&size=10`
2. Check HTTP traffic to Keycloak — should see exactly TWO calls: one paginated users fetch, one count fetch
3. The count call URL should be `/roles/WEB_APP/users/count` (not `/roles/WEB_APP/users?max=2147483647`)
4. Pagination metadata (`totalElements`, `totalPages`) in the response must be correct

## Alternative (if `/roles/{name}/users/count` is unavailable)
Use `GET /users/count?enabled=true` with role-based filter:
```java
Long totalCount = webClient.get()
        .uri(u -> u.path("/users/count")
                .queryParam("enabled", true)
                .build())
        .retrieve()
        .bodyToMono(Long.class)
        .block();
```
Note: this counts ALL enabled users, not just WEB_APP role holders — verify it matches business
requirements.

## Notes
- Verify Keycloak version running in docker-compose supports `/roles/{name}/users/count`
- If the count endpoint returns `null` or 404, fall back gracefully with `totalElements = users.size()` for the current page (acceptable degradation)
- The `findByIds(Collection<UUID>)` batch fetch (parallel Flux) is unaffected — only `findAll(Pageable)` changes
