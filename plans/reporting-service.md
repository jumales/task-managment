# Reporting Service

## Context
Users need a dedicated reporting view over existing task data:
- **My Tasks** (all / last 5 days / last 30 days) showing short code, start/end date, description, with a link to task detail.
- **Planned vs booked hours** report at task, project, and detailed (per user × work type) level.
- Dashboard widget visualising planned vs booked as a bar chart.
- Reporting service pushes updates to the frontend in near real time.

No reporting service exists today. Task data (`task_code`, start/end dates, description), planned hours (`task_planned_works`), and booked hours (`task_booked_works`) all live in task-service. We will create a new `reporting-service` that builds its own projection from existing Kafka events and exposes REST + WebSocket endpoints via the api-gateway. The word "jetstream" in the brief is interpreted as streaming + push; we reuse the existing Kafka cluster for ingestion and WebSocket/STOMP for push (no new broker).

## Approach

### 1. New microservice `reporting-service`
Standard module layout, mirroring `task-service` / `audit-service`:

```
reporting-service/
├── pom.xml
├── src/main/java/com/demo/reporting/
│   ├── ReportingServiceApplication.java
│   ├── config/WebSocketConfig.java               STOMP over WebSocket, /ws, /topic broker
│   ├── consumer/
│   │   ├── TaskEventConsumer.java                KafkaTopics.TASK_EVENTS  -> ReportTask projection
│   │   └── TaskChangedConsumer.java              KafkaTopics.TASK_CHANGED -> planned/booked work rows
│   ├── model/
│   │   ├── ReportTask.java                       id, taskCode, title, description, startDate, endDate,
│   │   │                                         projectId, assignedUserId, status, updatedAt
│   │   ├── ReportPlannedWork.java                taskId, userId, workType, plannedHours
│   │   └── ReportBookedWork.java                 taskId, userId, workType, bookedHours, bookedAt
│   ├── repository/                               Spring Data JPA, one per entity
│   ├── service/
│   │   ├── MyTasksService.java                   my-tasks / last 5d / last 30d queries
│   │   ├── HoursReportService.java               planned vs booked per task / project / detailed
│   │   └── ReportPushService.java                STOMP push on projection change
│   └── controller/ReportingController.java
├── src/main/resources/
│   ├── application.yml                           port 0, reporting-group, reporting_db,
│   │                                             org.apache.kafka: TRACE,
│   │                                             com.demo.reporting.consumer: DEBUG,
│   │                                             tracing.sampling 1.0
│   └── db/migration/V1__init.sql
├── src/test/resources/docker-java.properties     api.version=1.47
└── src/test/java/com/demo/reporting/
    ├── ReportingServiceApplicationIT.java        context smoke
    ├── ReportingControllerIT.java                Testcontainers Postgres + Kafka
    ├── MyTasksQueryIT.java
    ├── HoursReportIT.java
    └── TestSecurityConfig.java                   injects fixed test user
```

Entities use `@UuidGenerator(style = TIME)` and Lombok per CLAUDE.md. Reuse `com.demo.common` (SecurityConfig, JwtAuthConverter, MdcFilter, exception types, `KafkaTopics`, `TaskChangedEvent`, DTOs).

### 2. Data ingestion (Kafka projection, no Feign for reads)
- `TaskEventConsumer` listens to `KafkaTopics.TASK_EVENTS` (same topic search-service already consumes) and upserts/soft-deletes `report_tasks`.
- `TaskChangedConsumer` listens to `KafkaTopics.TASK_CHANGED` and routes `plannedWorkCreated`, `bookedWorkCreated/Updated/Deleted` into `report_planned_works` / `report_booked_works`. Pattern already used in `audit-service/.../consumer/TaskEventConsumer.java`.
- After each projection write, `ReportPushService.notifyUser(userId, payload)` sends a STOMP message to `/topic/reports/{userId}`.

No new Kafka topics. An optional admin backfill endpoint can reuse the existing `search-service` Feign `TaskServiceClient` pattern.

### 3. REST API (`/api/v1/reports`, JWT-secured via common `SecurityConfig`)
| Endpoint | Purpose |
|---|---|
| `GET /my-tasks` | All tasks assigned to the current user (code, title, description, start, end, status, id) |
| `GET /my-tasks?days=5` | Tasks updated in the last 5 days |
| `GET /my-tasks?days=30` | Tasks updated in the last 30 days |
| `GET /hours/by-task?projectId={}` | Per-task planned vs booked totals |
| `GET /hours/by-project` | Per-project planned vs booked totals |
| `GET /hours/detailed?taskId={}` | Per user × work type planned vs booked for a task |

Current user id comes from JWT `sub` (resolved by existing `JwtAuthConverter`). ControllerLoggingAspect auto-applied via `com.demo.common.web: DEBUG`.

### 4. Push to frontend (WebSocket + STOMP)
- `spring-boot-starter-websocket`.
- `WebSocketConfig` registers `/ws` (SockJS), enables simple broker on `/topic`, application prefix `/app`. JWT validated via a `ChannelInterceptor` using the existing `JwtDecoder` bean.
- `ReportPushService` uses `SimpMessagingTemplate.convertAndSend("/topic/reports/{userId}", payload)` from inside the Kafka consumer thread.
- Gateway route `Path=/ws/**` → `lb://reporting-service`.

### 5. api-gateway + Eureka
Add routes in `api-gateway/src/main/resources/application.yml`:
```yaml
- id: reporting-service-reports
  uri: lb://reporting-service
  predicates:
    - Path=/api/v1/reports/**
- id: reporting-service-ws
  uri: lb://reporting-service
  predicates:
    - Path=/ws/**
```
Eureka registration is automatic via `spring-cloud-starter-netflix-eureka-client`.

### 6. Infra & ops
- `docker-images/postgres/init.sql`: create `reporting_svc` user + `reporting_db`.
- `scripts/start-dev.sh`: add `reporting-service` to the loop / `--restart` case / banner.
- `postman/reporting-service.postman_collection.json`.

### 7. Frontend (`web-client`)
- `AppLayout.tsx`: add **Reports** sidebar item.
- `App.tsx`: `<Route path="/reports" element={<ReportsPage/>} />`.
- `pages/ReportsPage.tsx`: tabs "My Tasks" / "My Tasks (5d)" / "My Tasks (30d)" / "Hours"; AntD `Table` with row action → `navigate('/tasks/' + id)`.
- `pages/reports/HoursReport.tsx`: by-task, by-project, detailed sub-views.
- `api/reporting.ts`: typed axios calls via the existing Keycloak-aware `api/client.ts`.
- **Dashboard chart**: add `recharts` to `web-client/package.json`; `pages/DashboardPage.tsx` renders a `PlannedVsBookedChart` (BarChart) from `GET /api/v1/reports/hours/by-project`.
- **Real-time push**: add `@stomp/stompjs`, `sockjs-client`. `realtime/reportingSocket.ts` connects to `${VITE_API_URL}/ws`, subscribes to `/topic/reports/{userId}`, and triggers a re-fetch on ReportsPage and DashboardPage.
- `types.ts`: `MyTaskReport`, `TaskHoursRow`, `ProjectHoursRow`, `DetailedHoursRow`.

### 8. Tests
- `ReportingControllerIT` — full Testcontainers stack (Postgres + Kafka); produce `TaskChangedEvent` factories via `KafkaTemplate` and assert the REST endpoints return the aggregated numbers.
- `MyTasksQueryIT` — 5d / 30d window boundaries.
- `HoursReportIT` — by-task, by-project, detailed correctness including soft-deleted booked rows.
- WebSocket smoke test: STOMP client connects, subscribes, receives a message after producing a Kafka event.

## Delivery plan (step → PR → continue)

1. **Scaffold** (this PR): new `reporting-service` module with pom, Application, `application.yml`, Flyway `V1__init.sql`, context-load IT, postgres init entry, gateway routes, start-dev entry, Postman collection, root `pom.xml` module, and plan doc in `plans/`.
2. **Projection & my-tasks**: entities, Flyway `V2`, consumers for `TASK_EVENTS` + `TASK_CHANGED`, REST `GET /my-tasks[?days=]`, IT.
3. **Hours report**: REST `GET /hours/by-task|by-project|detailed`, IT.
4. **WebSocket push**: `WebSocketConfig`, `ReportPushService`, JWT STOMP interceptor, IT.
5. **Frontend**: Reports tab, ReportsPage, HoursReport, api client, types.
6. **Dashboard chart + real-time**: Recharts bar chart on dashboard; STOMP client wiring.

## Reused code
- `common/.../config/KafkaTopics.java` — `TASK_EVENTS`, `TASK_CHANGED`.
- `common/.../event/TaskChangedEvent.java` — existing factories.
- `common/.../config/SecurityConfig.java` + `JwtAuthConverter.java` — auto-discovered.
- `common/.../web/ControllerLoggingAspect.java` + `MdcFilter.java`.
- `search-service/.../consumer/TaskEventConsumer.java` — template for TASK_EVENTS projection.
- `audit-service/.../consumer/TaskEventConsumer.java` — template for routing `TaskChangedEvent` subtypes.
- `web-client/src/api/client.ts` — Keycloak-aware axios.

## Verification
1. `mvn clean install -DskipTests=true` — module builds.
2. `mvn -pl reporting-service verify` — IT suite green.
3. `scripts/start-dev.sh` — boots with `reporting-service` registered in Eureka.
4. Create a task + planned work + booked work in the UI → `/reports` lists it; Dashboard bar chart updates without manual refresh (WebSocket push).
5. Run `postman/reporting-service.postman_collection.json` — all requests assert 200.
