# Changelog

## [Unreleased] — Fix MinIO InputStream leak on client disconnect

### Fixed
- **`file-service` download endpoint** — replaced `InputStreamResource` with `StreamingResponseBody` + try-with-resources so the MinIO `InputStream` is always closed even when the HTTP client disconnects mid-transfer, preventing connection pool exhaustion under sustained partial-download traffic.

---

## [Unreleased] — Outbox consumer idempotency (Option A)

### Added
- **`reporting-service` dedup** — new `com.demo.reporting.dedup` package (`ProcessedKafkaEvent`, `ProcessedEventRepository`, `ProcessedEventService`) and Flyway migration `V5__add_processed_kafka_events.sql`. Wired into `TaskEventProjectionConsumer` and `TaskChangedProjectionConsumer` so duplicate outbox deliveries no longer trigger duplicate WebSocket pushes or repeated upserts.
- **`audit-service` `TaskLifecycleConsumer`** — idempotency guard added so replayed `TASK_ARCHIVED` events cannot re-run the archive routine.
- **Duplicate-delivery integration tests** — `ReportingControllerIT#duplicateTaskEvent_projectsOnce` and `HoursReportIT#duplicateBookedWorkEvent_upsertsOnce` assert single-sided effect under replayed events.

### Changed
- `ProcessedEventService.CONSUMER_GROUP` constant removed from both `audit-service` and `notification-service` — each `@KafkaListener` now owns its consumer-group constant to match the Kafka groupId it binds to. Callers of `markProcessed` pass the group explicitly.

### Why
`OutboxPublisher` uses `SELECT FOR UPDATE SKIP LOCKED`, which prevents concurrent pick-up but not crash recovery — if the publisher crashes between the Kafka ACK and the `published = true` commit, events are re-sent on the next poll. Consumers must be idempotent end-to-end. `search-service` and `file-service` consumers are naturally idempotent (Elasticsearch upsert by doc ID, `softDeleteById` is a no-op once the row is already soft-deleted) — explanatory Javadoc added instead of dedup tables.

---

## [PR #178] — Fix IT test suite across all services

### Fixed
- **`AuditConsumerIT`** — added `spring.kafka.producer.value-serializer=JsonSerializer` and matching consumer deserializer/trusted-packages/default-type properties inline; moved Kafka config to `audit-service/src/test/resources/application.yml` so the test can round-trip `TaskChangedEvent` without a config server.
- **`FileControllerIT` / `FileSizeValidationIT`** — added a `JwtDecoder` mock bean to `TestSecurityConfig` so `common` `SecurityConfig`'s `oauth2ResourceServer.jwt()` wires correctly in tests; added `file-service/src/test/resources/application.yml` with MinIO bucket config and multipart limits.
- **`NotificationConsumerIT`** — added `JwtDecoder` mock bean; added `notification-service/src/test/resources/application.yml` with Kafka deserializer config and a stub OAuth2 client registration so `FeignClientConfig` can wire an `AuthorizedClientManager` without a live Keycloak.
- **`SearchControllerIT`** — added `JwtDecoder` mock bean; added `search-service/src/test/resources/application.yml` disabling config server and setting `auto-offset-reset: earliest`.
- **`TaskProjectControllerIT`** — added Awaitility wait for async default-phase creation before asserting phase count, fixing a race condition where the `@Async` executor had not yet committed the phases.
- **`task-service` test `application.yml`** — enabled `com.demo: DEBUG` so `MdcFilterIT` and `ControllerLoggingAspectIT` receive their expected log events.
- **`notification-service` / `reporting-service` `WebSocketConfig`** — changed `@Value("${cors.allowed-origins}")` to `@Value("${cors.allowed-origins:*}")` so the service starts without a config server in tests.
- **`EmailService`** — `@Value("${notification.mail.from:noreply@demo.local}")` default prevents startup failure when config server is unavailable.
- **`NotificationService`** — `@Value("${app.frontend-url:http://localhost:3000}")` default prevents startup failure when config server is unavailable.
- Added `reporting-service/src/test/resources/application.yml` disabling config server and setting Kafka `auto-offset-reset: earliest`.

---

## [PR #177] — Local CI via act (self-hosted)

### Added
- **`.actrc`** — pins `act` to self-hosted runner (`-P ubuntu-latest=-self-hosted`) and `--artifact-server-path /tmp/act-artifacts`. Self-hosted mode executes workflow steps directly on the host so Testcontainers use the host Docker daemon natively — no nested Docker.
- **`scripts/ci-local.sh`** — wraps `act workflow_dispatch -W .github/workflows/ci.yml -j <job>`. Runs a single service job, every service job sequentially, or lists available jobs (`-l`). Exits non-zero with the failed-job list on failure.
- **`.claude/agents/act-ci-validator.md`** — new Claude Code agent that runs local CI via `act` instead of `git push` + `gh run watch`. Existing `ci-validator` and `custom-pr` agents left unchanged.

---

## [PR #176] — DLQ Monitoring: Consumer Lag, Health, Prometheus Gauge

### Added
- **`DltLagService`** — shared AdminClient computation of `end_offset − committed_offset` per DLT, so the signal drops to 0 once dead-lettered messages are processed or replayed.
- **`DltHealthIndicator`** — `/actuator/health/dlt` reports `DOWN` whenever any DLT has consumer lag > 0. Suitable as a Kubernetes readiness probe gate.
- **`DltMetricsPublisher`** — Micrometer gauge `kafka.dlt.consumer.lag` tagged by topic, refreshed every 30 s; scraped by Prometheus.
- **DLT consumer group constants in `KafkaTopics`** — `TASK_CHANGED_DLT_GROUP`, `TASK_EVENTS_DLT_GROUP`, `USER_EVENTS_DLT_GROUP`.

### Changed
- `DlqController` response field renamed `dltMessageCounts` → `dltConsumerLag` (end offset → lag semantics).
- `config-repo/audit-service.yml` — overrides `management.endpoint.health.show-details: always` so DLT lag values are visible on the health endpoint.

### Docs
- `docs/services/audit-service.mdx` — DLQ monitoring section (endpoint, health indicator, gauge, alert rule).
- `docs/operations/monitoring.mdx` — `/actuator/health/dlt` actuator row and cross-link.
- `postman/audit-service.postman_collection.json` — DLQ status test asserts new `dltConsumerLag` field.

---

## [PR #175] — MDC Async / Kafka Listener Propagation

### Added
- **`MdcTaskDecorator`** — propagates MDC context (including `requestId`, `userId`, `traceId`) into `@Async` threads via the shared `ThreadPoolTaskExecutor` in `common`.
- **Kafka listener `mdcRecordInterceptor`** — extracts `correlationId` header from the Kafka record and seeds MDC on listener threads; clears after each record.
- **`correlationId` Kafka header** — producers stamp the originating request's `requestId` on outbox-published records so the trace survives HTTP → Outbox → Kafka → Consumer.

### Fixed
- `MdcTaskDecoratorTest` isolates async-thread MDC cleanup from caller context (no cross-test leakage).

---

## [PR #174] — Production-Risk TODO Plans

### Added
- `plans/todo/fix_outbox_at_least_once.md` — consumer idempotency audit; explains why `SKIP LOCKED` alone is insufficient under crash/retry scenarios.
- `plans/todo/fix_mdc_async_propagation.md` — plan later executed by PR #175.
- `plans/todo/fix_dlq_monitoring.md` — plan later executed by PR #176.

Documentation-only; no code changes.

---

## [PR #173] — Claude Automation Docs

### Added
- `docs/development/claude-automation.mdx` — catalogues the 11 agents and 14 skills used in this project, their responsibilities, and the chains between them (feature dev, load test, planning flows).
- `docs.json` — registers the new page under the Development group.
- `README.md` — Documentation section with Mintlify install/preview instructions, folder structure table, and page-addition guide.

---

## [PR #172] — Test Security: Mock `JwtDecoder` Bean

### Fixed
- Every `TestSecurityConfig` now provides a mocked `JwtDecoder` bean. Without it, `SecurityConfig.oauth2ResourceServer().jwt()` (in `common`) requires a real `issuer-uri` at context startup, which no test environment provides — all 183 ITs in `task-service` and `user-service` failed with `No qualifying bean of type JwtDecoder`.
- Affected: `task-service`, `reporting-service`, `user-service/UserControllerIT` inner config.

---

## [PR #171] — TTL & Archiving System

### Added
- **Task archiving** — closed tasks (RELEASED/REJECTED) are automatically archived to `archive.tasks_YYYYMM` PostgreSQL tables after a configurable TTL window (`ttl.task.archive-after-closed-days`, default 90 days). Related data (comments, participants, timelines, planned/booked work, attachments) is archived in the same batch transaction.
- **`TASK_ARCHIVED` Kafka event** — published on `task-events` after each archived task; carries `archiveMonth` (YYYYMM derived from task creation date) and `archivedFileIds` for downstream cleanup.
- **Cross-service cleanup**:
  - `audit-service` — moves audit records to monthly archive tables on `TASK_ARCHIVED`; drops expired archive tables nightly.
  - `reporting-service` — hard-deletes `report_tasks` and `report_hours` projections on `TASK_ARCHIVED`.
  - `search-service` — deletes `TaskDocument` from Elasticsearch on `TASK_ARCHIVED`.
  - `file-service` — soft-deletes `file_metadata` on `TASK_ARCHIVED`; `FileCleanupScheduler` permanently removes MinIO objects after `ttl.file.deleted-object-retention-days` (default 30 days).
  - `notification-service` — `NotificationCleanupScheduler` hard-deletes old notification records after `ttl.notification.retention-days` (default 365 days).
- **DB queue cleanup** — nightly schedulers purge stale `outbox_events` (published rows) and `task_code_jobs` (processed rows) after configurable retention (default 30 days each).
- **Kafka topic retention** — `KafkaTopicConfig` sets `retention.ms` on `task-events` and `task-changed` topics via `NewTopic` beans on startup (default 7 days each).
- **Elasticsearch ILM policy** — `ElasticsearchIlmConfig` bootstraps a `logstash-ttl-policy` and index template on search-service startup, applying a delete phase to all `logstash-*` indices (default 30 days).
- **`closed_at` column on `tasks`** — stamped when a task transitions to RELEASED or REJECTED; used by the archive eligibility query.
- **`archive` schema** — created in `task_db` and `audit_db` via Flyway migrations; all archive tables live here with YYYYMM suffix.
- **`TtlProperties`** — `@ConfigurationProperties("ttl")` class binding all TTL thresholds from `config-repo/application.yml`.
- **Full `ttl.*` config block** in `config-repo/application.yml` — all thresholds configurable without code changes.

### Docs
- `docs/operations/configuration.mdx` — added full `ttl.*` properties reference with dev override tip.
- `docs/services/task-service.mdx` — archiving flow, archive schema table, cross-service cleanup table, queue cleanup, Kafka retention.
- `docs/services/audit-service.mdx` — archive tables, nightly drop scheduler, corrected "never deleted" note.
- `docs/services/file-service.mdx` — two-phase cleanup: soft-delete on TASK_ARCHIVED → physical MinIO purge by scheduler.
- `docs/services/notification-service.mdx` — `NotificationCleanupScheduler` section.
- `docs/services/reporting-service.mdx` — projection hard-delete on TASK_ARCHIVED.
- `docs/services/search-service.mdx` — TASK_ARCHIVED → delete document; Elasticsearch ILM policy section.
- `README.md` — added Key Features section; added Config Server to architecture diagram with startup-order annotation; updated services table Kafka topic columns.
- `learning/ttl-archiving-manual-testing.md` — manual testing guide: TTL override steps, SQL verification queries, MinIO cleanup verification, scheduler trigger approach.

---

## [PR #170] — Centralized Spring Cloud Config Server

### Added
- **`config-server` module** — Spring Cloud Config Server at port 8888 using a native filesystem backend (`config-repo/` directory).
- **`config-repo/application.yml`** — shared properties for all services: Eureka, Kafka bootstrap, Keycloak issuer-URI, JPA dialect, management endpoints, tracing, Feign timeouts, log levels.
- **`config-repo/{service-name}.yml`** — one file per service for service-specific properties (datasource, Redis, Kafka serializers, Resilience4j, MinIO, mail, CORS, gateway routes).

### Changed
- All 8 service `application.yml` files stripped of shared properties; each now contains only `spring.application.name` and `spring.config.import`.
- `docker-compose.yml` — added `config-server` service with health check; all application services gain `depends_on: config-server: condition: service_healthy`.
- `scripts/start-dev.sh` — config-server added to `INFRA_SERVICES`; health wait added before Eureka starts.

### Docs
- `docs/architecture/services.mdx` — added config-server to service inventory; added Config Server section (resolution model, startup order, verification commands, how to change config); fixed Kafka topic columns for all services.
- `docs/operations/configuration.mdx` — replaced brief mention with full Config Layers table (`application.yml` vs `{service}.yml`), `spring.config.import` example, and `optional:` prefix explanation.

---

## [PR #169] — Idempotent Kafka Consumers

### Added
- **`ProcessedEvent` deduplication table** in `audit-service` and `notification-service` — each incoming Kafka event is checked against this table before processing; duplicate events (redeliveries after a restart) are silently skipped.

---

## [PR #168] — Dead Letter Queue (DLQ) Principle

### Added
- **Bounded retry + DLQ** — Kafka consumers in all services retry failed events up to a configurable limit, then forward to a `{topic}.dlq` dead-letter topic instead of blocking the partition.
- `KafkaDlqConfig` — shared DLQ routing configuration in `common`.

### Fixed
- `@NoArgsConstructor` added to task response DTOs to fix Redis deserialization failures on cache reads.

---

## [PR #160–167] — Bug fixes & refactoring

- **#167** — `@NoArgsConstructor` on task response DTOs to fix Redis deserialization.
- **#166** — Added microservice architecture principles learning doc.
- **#165, #163** — `KeycloakUserClient.findById` guarded against null Keycloak response to prevent NPE.
- **#164** — `findFullById` async futures: added 10s `orTimeout` to prevent indefinite hangs.
- **#162** — Removed `e2e-tests` Maven module (tests moved to separate Playwright setup).
- **#161** — Documented two-layer WebSocket security model in `WebSocketHttpSecurityConfig`.
- **#160** — Propagated `SecurityContext` to async threads; added pessimistic lock on task update to prevent lost-update race conditions.
- **#159, #158, #157** — E2E test fixes: DatePicker commit, `destroyOnHidden` Modal, task list visibility race.
- **#156** — Frontend: extracted error utility, removed duplicate code, fixed i18n keys.

---

## [PR #154–155] — Documentation & E2E testing

### Added
- **#155** — Mintlify technical documentation site (`docs/`) covering all services, API, architecture, operations, and authentication.
- **#154** — Playwright browser tests for all user roles (ADMIN, MANAGER, DEVELOPER, SUPERVISOR) with CI workflow integration; fixed `AccessDeniedException` to return 403 instead of 500.

---

## [PR #147–153] — Features & fixes

- **#153** — UI: replaced table/list action buttons with icon-only buttons.
- **#152** — Reporting: propagate task code to `report_tasks` after async assignment.
- **#151** — Tasks: replaced completion status select with Active / My Tasks toggle switches.
- **#150** — Task code assignment decoupled into async background job (`TaskCodeJob` queue); prevents blocking the task create transaction.
- **#149** — Performance: fixed connection pool exhaustion from over-parallelized async calls under load.
- **#148** — Participants: decoupled auto-registration logic from action services (single responsibility).
- **#147** — Reporting: added `GET /reports/tasks/open-by-project` endpoint and frontend dashboard chart.

---

## [PR #139–146] — Resilience & observability fixes

- **#146** — Fixed duplicate queries in hours report; resolved NULL `phaseName` in open-tasks query.
- **#144** — Frontend: SUPERVISOR role sees read-only UI (all write controls hidden); user avatars displayed in UsersPage.
- **#143** — Fixed users endpoint pagination and tasks page 500 error.
- **#142** — Fixed API Gateway Prometheus auth (metrics endpoint accessible without JWT).
- **#141** — Fixed projects table rendering and tasks 500 on missing phase.
- **#140** — Deduplicated WebSocket security config (removed per-service copies, moved to `common`).
- **#139** — Eureka HA: configured peer-aware mode with two nodes (`eureka-peer1:8761`, `eureka-peer2:8762`).
- **#138** — Kafka consumers switched to manual acknowledgement to prevent message loss on processing failure.
- **#137** — Fixed distributed Redis cache: `@ServiceConnection` annotation on `GenericContainer`, fixed cache eviction scope.
- **#136** — Production readiness small fixes: gateway deny on empty key, outbox SKIP LOCKED, WebSocket origin whitelist, Logback config, aggregate type enum.
- **#135** — Fixed Eureka instance ID collision when services run on port 0 (random port).

---

## [PR #131–134] — Monitoring & load testing

- **#134** — Fixed concurrent task code assignment race; load test stability improvements.
- **#133** — Added Grafana dashboard provisioning config (datasources, dashboard JSON).
- **#132** — Added Prometheus + Grafana observability stack to `docker-compose.yml`; Micrometer metrics exposed on all services.
- **#131** — Added `scripts/seed_task_data.py` for populating realistic test data.

---

## [PR #130] — Real-time task updates

### Added
- WebSocket push from `notification-service` to browser clients on every `TaskChangedEvent`; React hooks re-fetch affected data on receive.
- Reporting service broadcasts `/topic/reports` update after each Kafka event.

### Fixed
- Re-fetch timelines on `PHASE_CHANGED` event.
- Permitted SockJS HTTP handshake paths without JWT (handshake is anonymous; STOMP subscription is authenticated).

---

## [PR #123–129] — Task completion states & active filter

### Added
- **Task completion states** — two-level completion model: `DEV_FINISHED` (DONE phase) and `FINISHED` (RELEASED/REJECTED phase); write rules enforced per state.
- **`GET /tasks?completionStatus=`** filter — `FINISHED` and `DEV_FINISHED` query params on task list.
- **Active filter** — toggle to show only open (non-finished) tasks; persisted in URL query params.

---

## [PR #48] — Task detail page refactor

- Full task detail redesign: tabbed layout for comments, work logs, timelines, attachments, participants; real-time updates wired in.

---

## [PR #40–47] — Task codes, timelines & architecture

- **#47** — Added `/plan` slash command.
- **#45** — Architecture violation fixes: DTOs moved to `common`, removed cross-service DB calls.
- **#44** — `TaskFullResponse` DTO aggregating task + related entities in a single response.
- **#43** — Phase name enum (`TaskPhaseName`) and mandatory phase on task creation.
- **#42** — Renamed work logs to `planned_work` / `booked_work` (clearer domain language).
- **#41** — Added `scripts/reset_data.sh` for local DB wipe.
- **#40** — Added task codes (`PROJ-42` format): auto-generated sequential code per project.
- **#39** — Fixed timeline phase set-by-name lookup.
- **#37, #36** — Task timelines: start/end date entries per task with overlap constraints.
- **#35** — Migrated all entity IDs to UUID v7 (`@UuidGenerator(style = TIME)`) for time-ordered indexing.
- **#34** — Architecture violation fixes: no direct DB access across service boundaries.

---

## [PR #27–33] — Notification service & optimizations

- **#33** — Performance: N+1 batch loading in all list endpoints; extract `publishOutboxEvents` from `update()`.
- **#32** — Frontend: added Croatian (hr) and English (en) i18n support.
- **#31** — Made `username` mandatory on user creation.
- **#30** — Fixed Logstash local profile: TCP appender only active when `logstash` Spring profile is set.
- **#29** — Email template placeholder system (`{taskUrl}`, `{assigneeName}`, `{taskTitle}`, `{changeType}`).
- **#28** — Per-project notification templates configurable via API.
- **#27** — Added `notification-service`: email via SMTP (MailHog) + WebSocket push on task change events.

---

## [PR #20–26] — Participants, work logs & CI

- **#26, #23** — GitHub Actions: parallelized CI jobs per service module.
- **#24** — Planned and booked work logs: estimate vs actual hours by user and work type.
- **#22** — Added `taskType` (FEATURE, BUG, etc.) and `progress` (0–100%) fields to tasks.
- **#21** — Task participants: assign team members with roles (LEAD, DEV, QA, OTHER).
- **#20** — Improved search UX: debounced input, paginated results, task/user search combined.

---

## [PR #11–19] — File uploads, security & performance

- **#18** — Performance improvements: Redis caching on user lookups, Resilience4j circuit breakers on Feign calls.
- **#17** — Initial E2E test suite (later replaced by Playwright in #154).
- **#16** — Consolidated parent POM: shared dependency management in root `pom.xml`.
- **#15** — Maintainability audit fixes: extracted shared helpers, removed boilerplate, consistent error handling.
- **#14** — OWASP Top 10 fixes: input validation, XSS headers, JWT scope enforcement, CORS policy.
- **#13** — Lazy-loaded task comments (on-demand fetch instead of eager join).
- **#12** — Added code quality rules to `CLAUDE.md` (N+1, complexity, naming, Javadoc).
- **#11** — User profile pictures: upload avatar to MinIO, display in UI.

---

## [PR #1–10] — Foundation

- **#10** — Extended frontend: task CRUD, project/phase management, comment threads.
- **#9** — Dev startup/stop scripts (`scripts/start-dev.sh`, `scripts/stop-dev.sh`) with health checks.
- **#8** — GitHub Actions CI: build + integration test workflow on every push.
- **#7** — Added `username` and `active` flag to user entity.
- **#6** — Added clean build rule to `CLAUDE.md`: `mvn clean install -DskipTests=true` before every push.
- **#5** — Structured logging: MDC enrichment (traceId, userId, requestId), ELK stack integration (Logstash → Kibana).
- **#4** — `ControllerLoggingAspect`: AOP-based controller parameter tracing at DEBUG level.
- **#3** — Fixed Flyway dependency (`flyway-database-postgresql` not available in Flyway 9.x).
- **#2** — Flyway migrations: initial schema for all services; migration naming rules in `CLAUDE.md`.
- **#1** — Versioning rules: branch per task, PR on completion, no direct pushes to `main`.
