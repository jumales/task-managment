# Plan: Spring Cloud Config Server — Centralized Configuration

## Context

Each of the 8 microservices carries its own `application.yml` with large blocks of identical
properties (Kafka bootstrap, Eureka URL, Keycloak issuer-URI, JPA dialect, management endpoints,
tracing probability, Feign timeouts, log levels). A single infrastructure address change (e.g. Kafka
host) currently requires editing and redeploying 6 separate services. The goal is a Spring Cloud
Config Server that all services query at startup, so shared config lives in one place.

**After this change:** adding Zipkin = 1 line in `config-repo/application.yml` + 1 docker-compose
entry. No service files touched.

---

## Architecture Decisions

- **New Maven module `config-server`** — mirrors `eureka-server` pom/Dockerfile pattern exactly.
- **`native` filesystem backend** — `config-repo/` directory inside the monorepo. No remote Git
  required for local dev. Switch to `git` backend for production.
- **`spring.config.import` (not `bootstrap.yml`)** — Spring Boot 3.x / Spring Cloud 2024.x pattern.
- **`optional:` prefix** — services still start if Config Server is unreachable (useful for isolated
  dev runs).
- **Eureka server is NOT a config client** — it must be up before services start; circular dependency
  risk. Its tiny `application.yml` stays local.

---

## Files to Create

### `config-server/pom.xml`
Minimal, inherits from root `com.demo:microservice-demo`:
```xml
<dependencies>
  spring-cloud-config-server
  spring-boot-starter-actuator
</dependencies>
```
No version — `spring-cloud-dependencies 2024.0.1` BOM already in root pom covers it.

### `config-server/src/main/java/com/demo/config/ConfigServerApplication.java`
```java
@SpringBootApplication @EnableConfigServer
public class ConfigServerApplication { ... }
```

### `config-server/src/main/resources/application.yml`
```yaml
server:
  port: 8888
spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: file:${CONFIG_REPO_PATH:${user.dir}/config-repo}
```

### `config-server/Dockerfile`
Two-stage build; copy `config-repo/` alongside JAR; expose 8888.
Model exactly after `eureka-server/Dockerfile`.

### `config-repo/application.yml` — shared config for ALL services
Properties to move here (currently duplicated across 6-8 files):
- `eureka.instance.instance-id` + `eureka.client.service-url.defaultZone`
- `spring.kafka.bootstrap-servers`
- `spring.security.oauth2.resourceserver.jwt.issuer-uri`
- `spring.jpa.hibernate.ddl-auto: validate` + `dialect: PostgreSQLDialect`
- `management.endpoints`, `management.endpoint.health`, `management.tracing.sampling.probability`
- `feign.client.config.default` timeouts
- Kafka/Spring log suppressions (`org.apache.kafka: WARN`, etc.)
- `logging.level.com.demo.common.web: DEBUG`

### `config-repo/{service-name}.yml` — one file per service (8 files)
Contains only what is unique: datasource, redis, kafka serializers/consumer groups, resilience4j,
minio, mail, CORS, Keycloak client credentials, gateway routes.

Services: `task-service`, `user-service`, `audit-service`, `notification-service`,
`reporting-service`, `search-service`, `file-service`, `api-gateway`.

---

## Files to Modify

### `pom.xml` (root)
Add `<module>config-server</module>` before `eureka-server`.

### Each service `application.yml` (8 files)
Strip all shared properties; keep only:
```yaml
server:
  port: 0   # (or fixed port for gateway)
spring:
  application:
    name: <service-name>          # MUST stay local — Config Server uses it for lookup
  config:
    import: "optional:configserver:http://${CONFIG_SERVER_URL:localhost:8888}"
```
Plus any service-specific properties not yet moved to `config-repo/{service}.yml`.

### Each service `pom.xml` (8 service modules, NOT common)
Add:
```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

### `docker-compose.yml`
Add config-server service:
```yaml
config-server:
  build: { context: ., dockerfile: config-server/Dockerfile }
  container_name: ms-config-server
  ports: ["8888:8888"]
  volumes: [./config-repo:/config-repo:ro]
  environment: { CONFIG_REPO_PATH: /config-repo }
  healthcheck:
    test: ["CMD-SHELL", "curl -sf http://localhost:8888/actuator/health || exit 1"]
    interval: 10s  retries: 8
  networks: [ms-network]
```
Add to all microservice definitions (not eureka-server, not infra):
```yaml
depends_on:
  config-server:
    condition: service_healthy
```

### `scripts/start-dev.sh`
1. Add `config-server` to `INFRA_SERVICES` (line 157) — runs as Docker container, not Terminal window.
2. Add port 8888 to the port-kill loop (line 140): `for port in 8761 8762 8080 8081 8082 8083 8084 8085 8086 8888 3000`.
3. Add `wait_for_http` after infra starts, **before** Eureka terminal opens:
   ```bash
   wait_for_http "Config Server" "http://localhost:8888/actuator/health" 60
   ```
4. Update `--docker-only` banner to include: `Config Server  http://localhost:8888`.
5. Add `config-server` note to `--restart` usage comment (infra-managed, not restartable via script).

### `scripts/stop-dev.sh`
No logic change — `docker compose down` stops everything. Add Config Server line to banner only.

---

## Property Migration Map (what moves where)

| Property | From (N files) | To |
|---|---|---|
| `eureka.*` | All 8 services | `config-repo/application.yml` |
| `spring.kafka.bootstrap-servers` | 6 services | `config-repo/application.yml` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | 7 services | `config-repo/application.yml` |
| `spring.jpa.hibernate.ddl-auto` + dialect | 5 services | `config-repo/application.yml` |
| `management.*` (endpoints, health, tracing) | All 8 services | `config-repo/application.yml` |
| `feign.client.config.default` | 3 services | `config-repo/application.yml` |
| Kafka/Spring log suppressions | 6+ services | `config-repo/application.yml` |
| `spring.datasource.*` | Per-service | `config-repo/{service}.yml` |
| `spring.kafka.producer/consumer.*` | Per-service | `config-repo/{service}.yml` |
| `resilience4j.*` | task/search/notification | `config-repo/{service}.yml` |
| Gateway routes | api-gateway | `config-repo/api-gateway.yml` |
| MinIO config | file-service | `config-repo/file-service.yml` |
| Mail config | notification-service | `config-repo/notification-service.yml` |

---

## Startup Order

```
Docker infra (postgres, kafka, keycloak, minio, redis, ...) 
  → config-server (Docker, health-checked)
  → wait_for_http "Config Server"
  → eureka-server (Terminal) 
  → all other services (Terminal)
```

---

## Verification

1. `mvn clean install -DskipTests=true` from project root — all modules compile including config-server.
2. `./scripts/start-dev.sh --docker-only` — Config Server starts, `curl http://localhost:8888/task-service/default` returns merged config JSON.
3. Start task-service: confirm it logs fetching config from `http://localhost:8888` on startup.
4. Verify `GET /actuator/env` on task-service shows `configserver:` as a property source.
5. Change `KAFKA_BOOTSTRAP_SERVERS` in `config-repo/application.yml`, restart one service — picks up new value with zero service code changes.

---

## Critical Files

- `/Users/admin/projects/cc/task-managment/pom.xml` — add module
- `/Users/admin/projects/cc/task-managment/docker-compose.yml` — add service
- `/Users/admin/projects/cc/task-managment/scripts/start-dev.sh` — INFRA_SERVICES, port list, wait block, banner
- `/Users/admin/projects/cc/task-managment/eureka-server/Dockerfile` — reference for config-server Dockerfile pattern
- `/Users/admin/projects/cc/task-managment/task-service/src/main/resources/application.yml` — reference for which properties to extract
- All 8 service `application.yml` files + `pom.xml` files
