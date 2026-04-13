# Finding #18 — Configure Eureka in peer-aware HA mode with two nodes

## Status
RESOLVED

## Severity
LOW — architectural SPOF; services cache the registry locally so brief outages are tolerated

## Context
A single Eureka instance means any restart or crash stops new service registrations and route
updates. Spring Cloud services cache the Eureka registry locally, tolerating brief outages.
However, a prolonged outage (crash, OOM) prevents new instance registrations and causes the
gateway to route to stale instance lists. For production, Eureka should run in peer-aware mode
with at least two nodes that replicate registry data to each other.

## Current State
`eureka-server/src/main/resources/application.yml`:
```yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false    # ← single node; must change for HA
    fetch-registry: false
```

## Implementation Plan

### 1. `eureka-server/src/main/resources/application.yml`
Replace the single-node config with peer-aware Spring profiles:
```yaml
server:
  port: ${SERVER_PORT:8761}
spring:
  application:
    name: eureka-server

---
spring:
  config:
    activate:
      on-profile: peer1
eureka:
  instance:
    hostname: eureka-peer1
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://eureka-peer2:8762/eureka/

---
spring:
  config:
    activate:
      on-profile: peer2
server:
  port: 8762
eureka:
  instance:
    hostname: eureka-peer2
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://eureka-peer1:8761/eureka/
```

### 2. All 7 service `application.yml` files
Update Eureka client to point at both peers:
```yaml
eureka:
  instance:
    instance-id: ${spring.application.name}:${random.value}   # already in place
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/,http://localhost:8762/eureka/}
```

Files to update:
- `user-service/src/main/resources/application.yml`
- `task-service/src/main/resources/application.yml`
- `audit-service/src/main/resources/application.yml`
- `file-service/src/main/resources/application.yml`
- `search-service/src/main/resources/application.yml`
- `notification-service/src/main/resources/application.yml`
- `reporting-service/src/main/resources/application.yml`

### 3. `docker-compose.yml`
Add second Eureka node:
```yaml
eureka-peer1:
  image: openjdk:17-jre-slim
  command: ["java", "-jar", "/app/eureka-server.jar", "--spring.profiles.active=peer1"]
  ports:
    - "8761:8761"

eureka-peer2:
  image: openjdk:17-jre-slim
  command: ["java", "-jar", "/app/eureka-server.jar", "--spring.profiles.active=peer2"]
  ports:
    - "8762:8762"
```
(Adjust to use the actual JAR or multi-stage build approach.)

### 4. `scripts/start-dev.sh`
Note: HA Eureka is optional for local dev. Add a comment explaining how to start a second
instance manually:
```bash
# Optional: start second Eureka for HA testing
# mvn spring-boot:run -pl eureka-server -Dspring-boot.run.profiles=peer2 &
```

### 5. `scripts/start-dev.sh` and `api-gateway/application.yml`
Update `INFRA_SERVICES` and gateway Eureka URL to reference the env var.

## Verification
1. Start both Eureka nodes
2. Open `http://localhost:8761` — should show itself + `eureka-peer2` as a registered peer
3. Kill `eureka-peer1` — services should continue routing via `eureka-peer2`
4. Restart `eureka-peer1` — it should re-sync the registry from `eureka-peer2`

## Notes
- In Kubernetes: deploy as a `StatefulSet` with 2 replicas, headless `Service`, and use DNS-based peer discovery (`eureka-server-0.eureka-server.namespace.svc.cluster.local`)
- Eureka HA does NOT guarantee zero data loss during simultaneous peer failures — it provides best-effort replication
- The `instance-id: ${spring.application.name}:${random.value}` fix from Finding #4 is a prerequisite (already merged)
