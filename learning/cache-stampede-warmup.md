# Cache Stampede & Startup Warmup

## Problem: Cache Stampede

Without warmup, the first wave of 100+ concurrent requests all miss the cache simultaneously.
Each spawns a Feign call → user-service → Keycloak Admin API. This hammers downstream services
on the very first wave of traffic after a deploy.

Warmup serializes that into one batch call at startup, before traffic arrives.

## Solution: `UserCacheWarmup`

**File:** `task-service/src/main/java/com/demo/task/client/UserCacheWarmup.java`

Fires on `ApplicationReadyEvent` (after Spring context is fully initialized, before accepting traffic):

1. Fetches up to 500 users in a single Feign page call
2. For each user, calls `fetchUser()` and `resolveUserName()` through `UserClientHelper` proxy
3. Both `USER_DTOS` and `USER_NAMES` Caffeine caches are populated before live traffic hits

Failures are swallowed as warnings — startup is never blocked; caches warm lazily under live load instead.

## Key Gotcha: Spring AOP Proxy

`@Cacheable` is AOP-based — it only intercepts calls made **through the Spring proxy**.

```java
// WRONG — calls userClient directly, bypasses @Cacheable proxy, nothing gets cached
userClient.findById(id);

// CORRECT — goes through UserClientHelper proxy, @Cacheable intercepts and stores in Caffeine
userClientHelper.fetchUser(id);
```

This is why `UserCacheWarmup` injects `UserClientHelper` separately instead of calling `UserClient` directly.

## Partial Warmup

Page size of 500 is a one-shot cap, not pagination. If >500 users exist, only page 1 is warmed.
The log line surfaces this:

```
User cache warmed: 312 entries cached (page 1 of 3, 312 total users)
```

Monitor this line after deploys to know if warmup was complete or partial.

## When to Use This Pattern

- Any service with a Caffeine (or Redis) cache backed by a slow downstream (Keycloak, external API)
- High-concurrency write endpoints that all need the same lookup data (e.g. task creation needing user info)
- After cache TTL expiry in production: consider scheduled re-warmup if stampede risk is high
