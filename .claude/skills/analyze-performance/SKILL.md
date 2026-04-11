---
name: analyze-performance
description: Analyze the Spring Boot microservices backend for performance and scalability bottlenecks — N+1 queries, missing indexes, pagination gaps, connection pool sizing, Kafka consumer concurrency, cache eviction scope, and Feign timeouts.
---
Analyze this codebase for performance and scalability bottlenecks under high load. This is a Spring Boot microservices backend. Use the context below to focus your analysis.

## Architecture context

- **Services**: user-service, task-service, audit-service, file-service (all Spring Boot + JPA + PostgreSQL)
- **Messaging**: Kafka outbox pattern in task-service → consumed by audit-service
- **Caching**: Spring Cache (Caffeine) in user-service for roles/rights
- **Service discovery**: Eureka + Feign for inter-service HTTP calls

## What to check

### 1 — N+1 Queries (highest impact)
- Every `service.findAll()` method: does it call a repository or another service *inside a stream/loop*?
- Any `toDto(entity)` / `toResponse(entity)` called per-item in a list method without batch loading
- Feign client calls inside a loop (e.g. `userClient.getUserById()` called per task)
- Fix pattern: collect IDs → batch fetch → build `Map<UUID, T>` → use in stream

### 2 — Missing Database Indexes
- For every `WHERE` clause used in JPA queries (derived method names): is the column indexed?
- Check: FK columns (project_id, user_id, role_id, task_id), status/enum filter columns
- Check: ordering columns used in `ORDER BY`
- Audit tables: indexed by `task_id`?
- Outbox table: index on `published = FALSE` (partial index)

### 3 — Pagination
- Every controller `GET` list endpoint: does it return the full table (`findAll()`) or a `Page<T>`?
- Large tables without pagination will OOM under load
- Expected fix: `Pageable` parameter + `Page<T>` return + `PageResponse<T>` wrapper DTO

### 4 — Connection Pool Sizing
- Check `application.yml` for `hikari.maximum-pool-size` (default: 10, too small for production)
- Rule of thumb: `pool_size = (2 × CPU_cores) + effective_spindle_count` for PostgreSQL
- Recommended: `maximum-pool-size: 20, minimum-idle: 5`

### 5 — Kafka Consumer Throughput
- `@KafkaListener` in audit-service: does it have `concurrency` set?
- Default concurrency = 1 (single-threaded consumer)
- Recommended: `concurrency = "3"` for a 3-partition topic

### 6 — Outbox Publisher Batch Save
- `OutboxPublisher.publishPending()`: does it call `save()` per event or `saveAll()` for the batch?
- N individual UPDATEs vs one batch UPDATE

### 7 — Cache Eviction Scope
- Per CLAUDE.md: `allEntries = true` is correct when cache holds both list and per-ID entries
- Check: are `grantRight`/`revokeRight` evicting the whole roles cache or just the modified role?
- Check: is `RightService.delete()` evicting both the rights cache AND the roles cache?

### 8 — Feign Client Timeouts
- Are Feign clients configured with explicit connect/read timeouts?
- A slow user-service call with no timeout will block task-service threads indefinitely

### 9 — Flyway + JPA Dialect
- Is `ddl-auto: validate` on all services (safe for production)?
- Are migrations idempotent (partial indexes for soft-delete tables)?

## Instructions

1. Read all relevant service and configuration files — do not guess.
2. For each category, report:
   - **Status**: PASS / FAIL / WARN
   - **Finding**: what was found (or confirmed safe)
   - **File**: exact file path and line numbers
   - **Recommendation**: specific fix if FAIL or WARN
3. At the end, produce a **risk-ranked action list** ordered: FAIL → WARN → PASS.
4. For every FAIL, provide the exact code or config change needed.
