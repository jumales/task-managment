# Testcontainers + Docker Desktop Compatibility Issue

## Context

Spring Boot 3.2.5 microservices project using Testcontainers 1.20.4 for integration tests.
All integration tests were failing with "Could not find a valid Docker environment".

---

## Issue 1: Docker API Version Mismatch

### Symptom
All Testcontainers strategies fail with `BadRequestException (Status 400)` when connecting
to the Docker socket, even though Docker Desktop is running and the socket exists.

### Root Cause
Docker Desktop 29.0.1 enforces a **minimum API version of 1.44**. Requests to endpoints
below that version (e.g. `GET /v1.32/info`) are rejected with HTTP 400.

Testcontainers 1.20.4 uses a **shaded, bundled version** of docker-java. When the API
version is not explicitly configured, the shaded `DefaultDockerClientConfig` returns
`UNKNOWN_VERSION`. Testcontainers then falls back to hardcoded `VERSION_1_32` (API 1.32),
which is below the Docker Desktop minimum.

```
// Inside Testcontainers DockerClientProviderStrategy (bytecode-verified):
if (apiVersion == UNKNOWN_VERSION) {
    builder.withApiVersion(VERSION_1_32); // 1.32 < 1.44 minimum → HTTP 400
}
```

### Why `DOCKER_API_VERSION` env var did not help
The `DOCKER_API_VERSION` environment variable is recognized by the standard docker-java
library, but **not** by the shaded version bundled inside Testcontainers 1.20.4. The shaded
`DefaultDockerClientConfig` only reads: `DOCKER_HOST`, `DOCKER_TLS_VERIFY`, `DOCKER_CONFIG`,
`DOCKER_CERT_PATH`, `DOCKER_CONTEXT`. Setting `DOCKER_API_VERSION` in Surefire
`<environmentVariables>` had no effect.

### Fix
Create `src/test/resources/docker-java.properties` in each service module:

```properties
api.version=1.47
```

The shaded `DefaultDockerClientConfig` loads this file as a classpath resource and reads
the `api.version` property key (bytecode-verified). With version 1.47 set, the
`UNKNOWN_VERSION` fallback to 1.32 is bypassed, and Docker Desktop accepts the requests.

### Key Lesson
When debugging Testcontainers + Docker connectivity, do not assume that `DOCKER_API_VERSION`
as an environment variable works — Testcontainers bundles its own shaded docker-java with
different configuration keys. Use `docker-java.properties` on the classpath instead.
Also verify what API versions your Docker Desktop version supports:

```bash
docker version  # shows "minimum version: 1.44"
curl --unix-socket /var/run/docker.sock http://localhost/v1.41/info  # → 400
curl --unix-socket /var/run/docker.sock http://localhost/v1.44/info  # → 200
```

---

## Issue 2: Wrong Kafka Container Class for Spring Boot 3.2.5

### Symptom
```
No ConnectionDetails found for source '@ServiceConnection source for TaskStatusKafkaIT.kafka'
```

### Root Cause
Tests used `org.testcontainers.kafka.KafkaContainer` (the newer Apache-native unified
container introduced in Testcontainers 1.19+) with image `apache/kafka-native:latest`.
Spring Boot 3.2.5's `@ServiceConnection` auto-configuration only supports the older
`org.testcontainers.containers.KafkaContainer` (Confluent-based). Support for the new
unified container was added in Spring Boot 3.3.0.

### Fix
Switch to the Confluent-based container supported by Spring Boot 3.2.5:

```java
// Before (not supported by Spring Boot 3.2.5 @ServiceConnection):
import org.testcontainers.kafka.KafkaContainer;
static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:latest"));

// After:
import org.testcontainers.containers.KafkaContainer;
static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
```

### Key Lesson
`org.testcontainers.kafka.KafkaContainer` and `org.testcontainers.containers.KafkaContainer`
are two different classes. Spring Boot's `@ServiceConnection` support is version-sensitive —
always check the Spring Boot release notes for which Testcontainers container classes are
supported before upgrading either dependency.

---

## Issue 3: Lombok Boolean Field Serialization Bug

### Symptom
Tests asserting `response.getBody().isDefault()` always received `false`, even when the
server stored and returned `true`.

### Root Cause
A `boolean isDefault` field in a Lombok-annotated DTO causes a Jackson serialization
mismatch:

- Lombok generates getter `isDefault()` → Jackson strips the `is` prefix → JSON key: `"default"`
- `@AllArgsConstructor` constructor parameter is named `isDefault` → Jackson looks for JSON
  key `"isDefault"` → not found → field defaults to `false`

```
Server serializes:   isDefault = true  →  {"default": true}
Client deserializes: {"default": true} → looks for "isDefault" → not found → false
```

The other fields (`id`, `name`, etc.) worked correctly because their getter names matched
their constructor parameter names exactly.

### Fix
Add `@JsonProperty("isDefault")` to the field in both request and response DTOs:

```java
@JsonProperty("isDefault")
private boolean isDefault;
```

This forces Jackson to use `"isDefault"` as the JSON key in both serialization and
deserialization, making getter name, setter name, and constructor parameter all resolve
to the same property.

### Key Lesson
Avoid naming boolean fields with the `is` prefix in Lombok DTOs used for JSON serialization.
The `is` prefix causes divergence between the JSON property name (derived from the getter,
which strips `is`) and the constructor parameter name (which keeps `is`). Either:
- Rename the field to avoid the `is` prefix (e.g. `boolean defaultPhase`)
- Or annotate with `@JsonProperty("isDefault")` to pin the JSON key explicitly
