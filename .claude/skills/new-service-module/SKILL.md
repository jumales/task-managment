---
name: new-service-module
description: Scaffold a new Spring Boot microservice module — Maven pom, application class, application.yml, OpenAPI config, docker-compose Postgres entry, service container entry, and api-gateway routes for all exposed controller paths.
---
Scaffold a new Spring Boot microservice module in the task-management multi-module Maven project.

## Required input
The user must provide:
- **Service name** (e.g. `notification-service`)
- **Port number** (e.g. `8084`)
- **Brief purpose** (used for comments and README)

## Steps

### 1. Maven module
Add a `<module>` entry to the root `pom.xml`.

Create `<service-name>/pom.xml` with:
- Parent: `com.demo:task-management:<version>`
- Standard dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql`, `lombok`, `springdoc-openapi-starter-webmvc-ui`, `spring-cloud-starter-netflix-eureka-client`, `common` module dependency
- Test dependencies: `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`

### 2. Application class
Create `src/main/java/com/demo/<name>/<Name>Application.java`:
```java
@SpringBootApplication(scanBasePackages = "com.demo")
public class <Name>Application {
    public static void main(String[] args) { SpringApplication.run(<Name>Application.class, args); }
}
```
`scanBasePackages = "com.demo"` ensures `GlobalExceptionHandler` from `common` is picked up.

### 3. application.yml
Create `src/main/resources/application.yml`:
```yaml
server:
  port: <port>

spring:
  application:
    name: <service-name>
  datasource:
    url: jdbc:postgresql://localhost:5432/<service_db>
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

### 4. OpenAPI config
Create `src/main/java/com/demo/<name>/config/OpenApiConfig.java` with `@OpenAPIDefinition` setting the title and description.

### 5. docker-compose entry
Add a Postgres service for the new module to `docker-compose.yml`:
```yaml
  <name>-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: <service_db>
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "<unused_port>:5432"
```

Add the service container itself **without** a `ports` mapping — it must only be reachable through the gateway, not directly from outside:
```yaml
  <service-name>:
    image: <service-name>
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://<name>-db:5432/<service_db>
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
    depends_on:
      - <name>-db
      - eureka-server
    # No "ports:" block — access only through the gateway on port 8080
```

### 6. Gateway routes
Open `api-gateway/src/main/resources/application.yml` and add one route entry per controller base path exposed by the new service. Every path the service owns **must** be listed here — any path not routed by the gateway is unreachable from outside.

```yaml
spring:
  cloud:
    gateway:
      routes:
        # ... existing routes ...

        - id: <service-name>-<resource1>
          uri: lb://<service-name>
          predicates:
            - Path=/api/<resource1>/**

        - id: <service-name>-<resource2>
          uri: lb://<service-name>
          predicates:
            - Path=/api/<resource2>/**
```

Rules:
- Use `lb://<service-name>` so Spring Cloud Gateway resolves the instance via Eureka.
- One route block per distinct path prefix (one per controller `@RequestMapping`).
- Route `id` values must be unique across the whole file — use `<service-name>-<resource>` convention.
- **Do not** add a wildcard catch-all route for the whole service — list paths explicitly so only intended endpoints are exposed.

After adding routes, verify the gateway `application.yml` has no duplicate `id` values and that every `@RequestMapping` base path in the new service is covered.

## Conventions checklist
- [ ] `scanBasePackages = "com.demo"` on `@SpringBootApplication`
- [ ] Eureka client enabled (disable only in tests with `eureka.client.enabled=false`)
- [ ] Port documented and not conflicting with existing services
- [ ] Separate Postgres database per service
- [ ] `common` module on classpath (shared DTOs, events, exceptions)
- [ ] OpenAPI definition present
- [ ] Service container in docker-compose has **no** `ports` mapping (gateway is the only entry point)
- [ ] One gateway route per controller `@RequestMapping` base path
- [ ] Gateway route IDs follow `<service-name>-<resource>` convention
