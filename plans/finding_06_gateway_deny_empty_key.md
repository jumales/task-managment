# Finding #6 — Set `deny-empty-key: true` in API Gateway rate limiter

## Status
UNRESOLVED

## Severity
HIGH — security posture gap (unauthenticated requests bypass rate limiting)

## Context
The API Gateway uses Redis-backed `RequestRateLimiter` filter with a `PrincipalNameKeyResolver`.
When the JWT principal is null (unauthenticated request), the key resolver returns an empty string.
With `deny-empty-key: false`, such requests bypass the rate limiter entirely.

Although the gateway's `SecurityFilterChain` rejects unauthenticated requests with 401, the
ordering of filters means the rate limiter may be evaluated before the JWT check in edge cases.
Setting `deny-empty-key: true` closes this gap defensively.

## Root Cause
`api-gateway/src/main/resources/application.yml` — line 25:
```yaml
deny-empty-key: false   # ← should be true
```

## Files to Modify

### `api-gateway/src/main/resources/application.yml`
Single line change:
```yaml
# Before:
deny-empty-key: false

# After:
deny-empty-key: true
```

## Verification
1. Send a request to a rate-limited route without an Authorization header
2. Expected: HTTP 401 (from JWT filter) — behaviour unchanged for normal flows
3. Expected: With `deny-empty-key: true`, any request that reaches the rate limiter with an empty key receives HTTP 429 (Too Many Requests) instead of being passed through
4. Check gateway logs to confirm no impact on authenticated request flows

## Notes
- No functional impact on authenticated traffic
- No tests required — pure security posture improvement
- One-line change, safe to bundle with other gateway changes
