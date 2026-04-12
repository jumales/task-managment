# Finding #17 — Delete unused @Deprecated resolveUserIdByUsername from UserClientHelper

## Status
UNRESOLVED

## Severity
LOW — dead code; annotated @Deprecated with zero callers confirmed

## Context
`UserClientHelper.resolveUserIdByUsername(String username)` is annotated `@Deprecated` with
a Javadoc note stating: "No longer called from within this service — Keycloak UUIDs are resolved
directly from the JWT `sub` claim via `resolveUserId`."

Codebase-wide grep confirms zero callers outside of `UserClientHelper` itself (the method +
its fallback are the only occurrences of `resolveUserIdByUsername`).

Per CLAUDE.md: avoid backwards-compatibility hacks. Dead deprecated code adds cognitive overhead
and creates a stale cache registration in `USER_NAMES` keyed as `'username:' + #username`.

## Root Cause
`task-service/src/main/java/com/demo/task/client/UserClientHelper.java` — lines 63–78:
```java
/**
 * @deprecated No longer called from within this service...
 */
@Deprecated
@Cacheable(value = CacheConfig.USER_NAMES, key = "'username:' + #username", ...)
@CircuitBreaker(name = "userService", fallbackMethod = "resolveUserIdByUsernameFallback")
public UUID resolveUserIdByUsername(String username) {
    if (username == null) return null;
    return userClient.getUserByUsername(username).getId();
}

private UUID resolveUserIdByUsernameFallback(String username, Throwable t) {
    log.warn("...");
    return null;
}
```

## Files to Modify

### `task-service/src/main/java/com/demo/task/client/UserClientHelper.java`
Delete:
1. The Javadoc block for `resolveUserIdByUsername` (lines ~63–66)
2. The `@Deprecated`, `@Cacheable`, `@CircuitBreaker` annotations (lines ~67–69)
3. The method body of `resolveUserIdByUsername` (lines ~70–73)
4. The fallback method `resolveUserIdByUsernameFallback` (lines ~75–78)

The `USER_NAMES` cache constant remains — it is still used by `resolveUserName(UUID)`.

## Pre-Deletion Verification
Run before making the change:
```bash
grep -r "resolveUserIdByUsername" /Users/admin/projects/cc/task-managment --include="*.java"
```
Expected: only the definition in `UserClientHelper.java` — no callers.

## Post-Deletion Verification
1. `mvn clean install -pl task-service` must compile cleanly
2. Run `TaskControllerIT` — all tests must pass
3. Confirm the `USER_NAMES` cache is still populated by `resolveUserName(UUID)` (unchanged)

## Notes
- The `getUserByUsername` method on `UserClient` (Feign interface) may also become unused after this deletion — check and remove if so
- No Flyway migration needed — this is pure Java code deletion
