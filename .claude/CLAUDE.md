# Versioning Rules

- **Never push directly to `main` or `master`** — direct pushes to protected branches are not allowed
- **Every task gets its own branch** — before starting work, create a new branch from `main` (unless already on a feature branch that hasn't been merged/deleted)
- **Branch naming** — use a short descriptive name in snake_case, e.g. `add_user_endpoint`, `fix_feign_auth`
- **Open a PR when the task is done** — do not merge directly; create a pull request and wait for review
- **Clean build before every push** — run `mvn clean install -DskipTests=true` from the project root and confirm it passes before every `git push`; tests run automatically via GitHub Actions on every push
- **Check CI after pushing** — run `gh run watch` to follow the GitHub Actions run, or `gh run list` to see recent results; a failing CI run must be fixed before opening or merging a PR

# Database Schema Changes

- **Use Flyway for all schema changes** — never modify the database manually or rely on `spring.jpa.hibernate.ddl-auto: update`; production services use `validate`
- **Migration files are immutable** — once a migration is merged to `main`, never edit it; create a new versioned file instead
- **Naming convention** — `V{n}__{short_description}.sql`, e.g. `V2__add_due_date_to_tasks.sql`
- **Each service owns its schema** — no migration file may create or alter tables that belong to another service
- **Backward-compatible changes preferred** — add columns as nullable; never drop or rename a column in one step; use a multi-step deprecation (add → migrate data → drop in a later version)
- **Partial unique indexes for soft-delete tables** — use `CREATE UNIQUE INDEX ... WHERE deleted_at IS NULL` instead of `UNIQUE` constraints so soft-deleted rows do not block re-insertion

# File Upload Validation

- **All upload rules live in `application.yml`** under `minio.buckets.<bucket-name>` — never hardcode allowed types or size limits in Java
- **Adding a new bucket** requires only a new YAML entry with `allowed-types` and `max-size-bytes`; no Java changes needed
- **`allowed-types`** is a list of MIME type strings (e.g. `image/jpeg`); an empty list means all types are accepted
- **`max-size-bytes`** is the per-bucket limit; set `spring.servlet.multipart.max-file-size` generously (e.g. `50MB`) so Spring doesn't reject before our validation runs
- **Write two validation IT classes** when testing a new bucket type: one for content-type rejection and one for size rejection; use `@SpringBootTest(properties = "minio.buckets.<bucket>.max-size-bytes=10")` in the size test to avoid allocating large arrays

# Postman Collections

- **Always update Postman when adding or changing endpoints** — collections live in `postman/`; one file per service (e.g. `user-service.postman_collection.json`)
- **Update Postman immediately after any controller change** — every time you add, remove, or modify an endpoint (path, method, request body, response shape, status code), update the corresponding Postman collection in the same commit; never leave the collection out of sync with the controller
- **New service** — create `postman/<service-name>.postman_collection.json` following the existing structure: collection-level Bearer auth via `{{bearerToken}}`, collection variables for IDs auto-saved from create responses, and a test script on every request that asserts the expected status code
- **New endpoint on an existing service** — add the request to the correct folder inside the existing collection; auto-save any returned IDs into collection variables so dependent requests work without manual copy-paste
- **Modified response shape** — update or add test assertions to match the new fields
- **Environment file** (`postman/local.postman_environment.json`) — only needs changes if a new environment variable is introduced (e.g. a new base URL for a separate host)

# Startup / Stop Scripts

- **Always update `scripts/start-dev.sh` and `scripts/stop-dev.sh`** when adding a new Docker image or a new microservice
- **New Docker infrastructure** (e.g. a new container in `docker-compose.yml`) — add the service name to `INFRA_SERVICES` in `start-dev.sh`, add a healthcheck wait block if the service exposes one, and add it to the banner
- **New microservice** — add it to the `for service in ...` loop in `start-dev.sh`, add it to the valid names list in the `--restart` comment and the `case` block, and add it to the banner
- **`stop-dev.sh`** uses `docker compose down` which stops everything automatically — no changes needed unless the script is extended

# Creating a new service
- **Write integration tests** — every new service must have a `*IT.java` test class covering all controller endpoints; follow the pattern in `user-service/src/test` (Testcontainers Postgres + `@TestConfiguration` security bypass that permits all requests)
- **Fix Testcontainers Docker API version** — create `src/test/resources/docker-java.properties` in the new service with `api.version=1.47`; without it Testcontainers falls back to API 1.32 which Docker Desktop 29+ rejects with HTTP 400 (full details in `learning/testcontainers-issue.md`)
- **External dependencies in tests** — if the service depends on an external system (e.g. MinIO, Redis), spin it up with a `GenericContainer` in the IT class, use `@DynamicPropertySource` to inject the URL, and do any required initialization (e.g. bucket creation) in `@BeforeAll`
- **Enable controller logging** — add the following to the service's `application.yml` so all controller input parameters are traced via `ControllerLoggingAspect`:
  ```yaml
  logging:
    level:
      com.demo.common.web: DEBUG
  ```
- **Never use `show-sql: true`** — it always logs at INFO and produces unstructured output; use `logging.level.org.hibernate.SQL: DEBUG` instead so SQL is opt-in and JSON-structured
- **Set tracing sampling** — add `management.tracing.sampling.probability: 1.0` for dev; reduce to `0.05`–`0.1` in prod
- **Activate `logstash` profile in Docker** — add `spring.profiles.active=logstash` when running inside Docker so logs are shipped to Logstash via TCP in addition to stdout

# Cross-cutting components in `common`
- **Write integration tests for every new class in `common`** — classes like `MdcFilter` and `ControllerLoggingAspect` are shared by all services; test them in `task-service` using the standard `@SpringBootTest` + Testcontainers setup
- **Security is already configured in `common`** — `SecurityConfig` and `JwtAuthConverter` live in `com.demo.common.config`; do NOT create per-service copies. All services scan `com.demo.*` so these beans are auto-discovered
- **Kafka topic names belong in `KafkaTopics`** — add any new Kafka topic as a constant in `com.demo.common.config.KafkaTopics`; never use a string literal for a topic name in a producer or `@KafkaListener`

# Extending existing entity
- **Flyway migration** — add new columns as nullable or with a DEFAULT for backward compatibility; never edit existing migrations
- **Update entity, DTOs, and service** — entity fields, request/response DTOs in `common`, and all service create/update methods
- **Validate uniqueness at service boundary** — use `existsByField` and `existsByFieldAndIdNot` repository methods for unique fields; throw `DuplicateResourceException`
- **Update all constructor call sites** — DTOs using `@AllArgsConstructor` change their constructor signature; search for `new DtoName(` across the whole codebase and update every occurrence
- **Update web client** — add new fields to TypeScript interfaces (`types.ts`) and update relevant UI components
- **Write integration tests** — cover new field persistence, uniqueness constraint enforcement, and update behavior

# Adding new method
- comment
- write integration tests
- needs to be single responsible

# Adding new class
- if needs to be used from two or more modules, put into common
- comment
- If is entity, use UUID v7 for id — annotate with `@UuidGenerator(style = UuidGenerator.Style.TIME)` (no `@GeneratedValue` needed); import `org.hibernate.annotations.UuidGenerator`
- Use enumeration instead of hardcoded strings in code
- write integration tests

# Creating controller
- controller needs to use dto classes, not expose model class
- create OpenAPI documentation

# Delete
- always soft delete
- don't delete if exists related entities

# Task Completion State

Tasks have two levels of completion based on **phase** (not status):

| Phase | State | Label |
|---|---|---|
| `DONE` | Semi-final ("dev finished") | `DEV_FINISHED` |
| `RELEASED` or `REJECTED` | Fully finished | `FINISHED` |

## Write rules per state

| Operation | DONE | RELEASED / REJECTED |
|---|---|---|
| Edit task fields (`PUT /tasks/{id}`) | ❌ blocked | ❌ blocked |
| Change phase (`PATCH /tasks/{id}/phase`) | ✅ allowed | ❌ blocked |
| Add comments | ✅ allowed | ❌ blocked |
| Booked work (create / update / delete) | ✅ allowed | ❌ blocked |
| Planned dates / planned work | ❌ already blocked (PLANNING only) | ❌ already blocked |

## Implementation pattern
- Constants live on `TaskPhaseName`: `FINISHED_PHASES = {RELEASED, REJECTED}`, `FIELD_LOCKED_PHASES = {DONE, RELEASED, REJECTED}`
- Guards throw `BusinessLogicException` (HTTP 422) — consistent with the existing `validateNotPlanningPhase` pattern
- `validateNotFinished(task)` — used before phase change, add comment, booked work writes
- `validateFieldsEditable(task)` — used before task field update

## Filtering
- `GET /tasks?completionStatus=FINISHED` → tasks in RELEASED or REJECTED phase
- `GET /tasks?completionStatus=DEV_FINISHED` → tasks in DONE phase
- Implemented via `TaskCompletionStatus` enum (`FINISHED`, `DEV_FINISHED`) in common and a JPQL JOIN query on the repository

# Java Code Style

## Methods
- One method does one thing — if you need "and" to describe it, split it
- Keep methods short; extract private helpers rather than adding more lines
- Name methods after what they do: `findByProjectId`, `resolvePhaseId`, `toResponse`
- Prefer early returns to reduce nesting

```java
// bad
public TaskResponse findById(UUID id) {
    Optional<Task> task = repository.findById(id);
    if (task.isPresent()) {
        return toResponse(task.get());
    } else {
        throw new ResourceNotFoundException("Task", id);
    }
}

// good
public TaskResponse findById(UUID id) {
    return toResponse(getOrThrow(id));
}
```

## Variable names
- Name variables after what they hold, not their type: `projectId` not `uuid`, `task` not `t`
- Boolean names should read as a question: `isDefault`, `existsByTaskId`
- Avoid abbreviations unless universally obvious (`id`, `dto`, `url`)

```java
// bad
UUID u = request.getProjectId();
TaskProject p = projectRepo.findById(u).orElseThrow();

// good
UUID projectId = request.getProjectId();
TaskProject project = projectRepository.findById(projectId).orElseThrow();
```

## No hardcoded strings — use constants
- Never use string literals for values that have meaning: topic names, cache names, role names, claim keys, API paths, error messages
- Declare them as `static final` constants in the class that owns them, or in a dedicated constants class if shared across multiple classes
- Prefer enums over string constants when the set of values is fixed and finite

```java
// bad
outboxRepository.save(OutboxEvent.builder()
        .aggregateType("Task")
        .eventType("TASK_CHANGED")
        .topic("task-events")
        .build());

if (jwt.getClaim("realm_access") != null) { ... }

// good
// constants owned by the class that defines the concept
public class OutboxPublisher {
    public static final String TOPIC = "task-events";
}

public class OutboxEvent {
    public static final String AGGREGATE_TYPE_TASK = "Task";
}

// enum for a fixed set of values
public enum OutboxEventType {
    TASK_CHANGED
}

// usage
outboxRepository.save(OutboxEvent.builder()
        .aggregateType(OutboxEvent.AGGREGATE_TYPE_TASK)
        .eventType(OutboxEventType.TASK_CHANGED)
        .topic(OutboxPublisher.TOPIC)
        .build());

// JWT claim keys as constants
private static final String CLAIM_REALM_ACCESS = "realm_access";
private static final String CLAIM_RIGHTS       = "rights";

if (jwt.getClaim(CLAIM_REALM_ACCESS) != null) { ... }
```

## No duplicate code
- If the same logic appears twice, extract it — into a private method, a shared helper, or `common`
- `getOrThrow(UUID id)` and `toResponse(Entity e)` are package-private helpers for exactly this reason
- DTOs and events used by 2+ modules go in `common`

## No boilerplate
- Use Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` on entities; `@Data` on request DTOs; `@Getter @AllArgsConstructor` on response DTOs
- Use constructor injection — no `@Autowired` field injection
- Use `repository.findAll().stream().map(this::toResponse).toList()` — not manual for-loops
- Use `Optional.map(...).orElse(null)` and `Optional.orElseThrow(...)` — not `isPresent()` + `get()`

```java
// bad
List<TaskResponse> result = new ArrayList<>();
for (Task task : repository.findAll()) {
    result.add(toResponse(task));
}
return result;

// good
return repository.findAll().stream().map(this::toResponse).toList();
```

## Error handling
- Throw domain exceptions from `common` — never return `null` or swallow exceptions silently
- Use `ResourceNotFoundException` for missing entities, `DuplicateResourceException` for uniqueness violations, `RelatedEntityActiveException` when delete is blocked
- Only catch exceptions when you can meaningfully recover; log or rethrow otherwise
- Never catch `Exception` broadly unless it is a gateway/top-level handler

```java
// bad
public Task getOrThrow(UUID id) {
    Optional<Task> task = repository.findById(id);
    if (!task.isPresent()) return null; // caller has to null-check everywhere
    return task.get();
}

// good
Task getOrThrow(UUID id) {
    return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task", id));
}
```

## Code complexity

### Industry standard thresholds (SonarQube / Checkstyle / PMD defaults)

| Cyclomatic complexity | Risk | Action |
|---|---|---|
| 1–10 | Low | OK |
| 11–20 | Moderate | Consider refactoring |
| 21–50 | High | Refactor — hard to test |
| 50+ | Untestable | Must refactor |

**Target: keep every method at 10 or below.** Aim for 1–5 in most methods.

Cyclomatic complexity counts decision points — each `if`, `else if`, `for`, `while`, `case`, `catch`, `&&`, `||`, `?:` adds 1 (base is 1).

### Fix: replace if/else chains with polymorphism or a Map

The classic high-complexity pattern is a chain of `if/else if` that dispatches on a type or status. Replace it with a `Map` or strategy pattern:

```java
// bad — complexity 7 (one branch per status)
public String describeTransition(TaskStatus from, TaskStatus to) {
    if (from == TODO && to == IN_PROGRESS) {
        return "Work started";
    } else if (from == IN_PROGRESS && to == DONE) {
        return "Work completed";
    } else if (from == IN_PROGRESS && to == TODO) {
        return "Work paused";
    } else if (from == DONE && to == IN_PROGRESS) {
        return "Work reopened";
    } else if (from == TODO && to == DONE) {
        return "Skipped to done";
    } else if (from == DONE && to == TODO) {
        return "Reset to backlog";
    } else {
        return "Status changed";
    }
}

// good — complexity 2 (one lookup, one fallback)
private static final Map<String, String> TRANSITION_LABELS = Map.of(
    "TODO->IN_PROGRESS",  "Work started",
    "IN_PROGRESS->DONE",  "Work completed",
    "IN_PROGRESS->TODO",  "Work paused",
    "DONE->IN_PROGRESS",  "Work reopened",
    "TODO->DONE",         "Skipped to done",
    "DONE->TODO",         "Reset to backlog"
);

public String describeTransition(TaskStatus from, TaskStatus to) {
    return TRANSITION_LABELS.getOrDefault(from + "->" + to, "Status changed");
}
```

### Fix: replace nested conditions with early returns

```java
// bad — complexity 6 (deeply nested)
public void processTask(Task task) {
    if (task != null) {
        if (task.getStatus() != null) {
            if (task.getStatus() == IN_PROGRESS) {
                if (task.getAssignedUserId() != null) {
                    notifyUser(task.getAssignedUserId());
                }
            }
        }
    }
}

// good — complexity 3 (flat, early returns)
public void processTask(Task task) {
    if (task == null || task.getStatus() == null) return;
    if (task.getStatus() != IN_PROGRESS) return;
    if (task.getAssignedUserId() == null) return;
    notifyUser(task.getAssignedUserId());
}
```

## Documentation
- Every public and package-private method gets a Javadoc comment — one sentence is enough
- Describe *what* and *why*, not *how* — the code already shows how
- Use `@param` and `@return` only when the meaning is not obvious from the name
- Document non-obvious fields inline with `/** ... */`
- Add an inline comment on any logic that is not immediately obvious to a reader

```java
// bad — no docs, cryptic comment
public UUID resolvePhaseId(UUID phaseId, UUID projectId) {
    // do stuff
    if (phaseId != null) {
        phaseService.getOrThrow(phaseId);
        return phaseId;
    }
    return phaseService.findDefaultForProject(projectId).map(TaskPhase::getId).orElse(null);
}

// good
/**
 * Returns the explicit phaseId if provided, otherwise the project's default phase id (or null).
 * Validates the explicit phase exists before returning it.
 */
UUID resolvePhaseId(UUID phaseId, UUID projectId) {
    if (phaseId != null) {
        phaseService.getOrThrow(phaseId); // validate the requested phase belongs to a real phase
        return phaseId;
    }
    // Fall back to the project's default phase; null is valid when no default is configured
    return phaseService.findDefaultForProject(projectId).map(TaskPhase::getId).orElse(null);
}
```

- Class-level Javadoc explains the responsibility of the class, not its fields

```java
// bad
public class OutboxPublisher { }

// good
/**
 * Polls the outbox table on a fixed schedule and publishes unpublished events to Kafka.
 * Marks each event as published after successful delivery.
 */
public class OutboxPublisher { }
```

## Input validation
- Validate at the boundary (controller / service entry point) — not deep inside helpers
- Throw `IllegalArgumentException` for invalid input values (enum parsing, empty required strings)
- Guard deletes: call `existsBy<FK>` before removing, throw `RelatedEntityActiveException` if active relations exist
- Never trust that a required FK exists without verifying: call `getOrThrow` on referenced entities before using their IDs

```java
// bad
public TaskResponse create(TaskRequest request) {
    Task task = Task.builder()
            .projectId(request.getProjectId()) // what if project doesn't exist?
            .build();
    return toResponse(repository.save(task));
}

// good
public TaskResponse create(TaskRequest request) {
    projectService.getOrThrow(request.getProjectId()); // validates FK exists
    userClient.getUserById(request.getAssignedUserId()); // validates user exists
    Task task = Task.builder()
            .projectId(request.getProjectId())
            .assignedUserId(request.getAssignedUserId())
            .build();
    return toResponse(repository.save(task));
}
```

## N+1 queries — batch-load in list methods

Never call a repository or service per item inside a stream. In list-returning methods, batch-load all related data first, build a `Map<UUID, T>`, then map over the results.

Pattern: keep a single-item `toResponse(T)` for `findById`/`create`/`update`; add a `toResponseList(List<T>)` overload for all list methods that batch-loads related entities.

Required support:
- Add `findByXIn(Iterable<UUID> ids)` to repositories used in list enrichment
- Add package-private `findAllByIds(Iterable<UUID> ids)` to services whose entities are loaded per-item

```java
// bad — N+1: one comment query per task
public List<TaskResponse> findAll() {
    return repository.findAll().stream().map(this::toResponse).toList();
}
private TaskResponse toResponse(Task task) {
    var comments = commentRepository.findByTaskId(task.getId()); // called N times
    ...
}

// good — 1 query for all comments regardless of list size
public List<TaskResponse> findAll() {
    return toResponseList(repository.findAll());
}
private List<TaskResponse> toResponseList(List<Task> tasks) {
    if (tasks.isEmpty()) return List.of();
    Set<UUID> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
    Map<UUID, List<CommentResponse>> commentsByTask = commentRepository.findByTaskIdIn(taskIds)
            .stream()
            .collect(Collectors.groupingBy(TaskComment::getTaskId,
                    Collectors.mapping(c -> new CommentResponse(c.getId(), c.getContent()), Collectors.toList())));
    return tasks.stream()
            .map(t -> new TaskResponse(..., commentsByTask.getOrDefault(t.getId(), List.of())))
            .toList();
}
```

## Extract event publishing from update()

When `update()` both persists and publishes events, extract publishing into a private `publishOutboxEvents(...)` method. This keeps `update()` focused on persistence and keeps cyclomatic complexity below 10.

```java
// bad — update() does persistence + status detection + phase detection + 2 outbox writes
public TaskResponse update(UUID id, TaskRequest request) {
    ...
    if (!newStatus.equals(oldStatus)) { writeToOutbox(...); }
    if (!Objects.equals(oldPhaseId, newPhaseId)) { writeToOutbox(...); }
    return toResponse(saved);
}

// good — persistence and publishing are separate concerns
public TaskResponse update(UUID id, TaskRequest request) {
    ...
    Task saved = repository.save(task);
    publishOutboxEvents(saved, oldStatus, request.getStatus(), oldPhaseId, request.getPhaseId());
    return toResponse(saved);
}

private void publishOutboxEvents(Task saved, TaskStatus oldStatus, TaskStatus newStatus,
                                  UUID oldPhaseId, UUID newPhaseId) {
    if (newStatus != null && !newStatus.equals(oldStatus)) { writeToOutbox(...); }
    if (!Objects.equals(oldPhaseId, newPhaseId)) { writeToOutbox(...); }
}
```

## Cache eviction scope

`@CacheEvict(allEntries = true)` is the correct choice when a cache holds both a list entry (`findAll`) and individual entries (`findById`). Evicting by key alone leaves the list cache stale. Use key-based eviction only for large caches where preserving unrelated entries matters.

Do not mix `key = "#id"` and `allEntries = true` in the same `@Caching` — it is redundant.

# React Best Practices

When writing, reviewing, or refactoring React code, follow the Vercel React Best Practices guide stored at `.claude/react-best-practices.md`. Apply it whenever working on:
- React components or hooks
- Client-side data fetching
- Bundle optimization
- Re-render or rendering performance

Key priorities (in order):
1. **Eliminate waterfalls** — parallelize independent API calls with Promise.all
2. **Bundle size** — avoid barrel imports, use dynamic imports for heavy components
3. **Re-render optimization** — memoize non-primitive values, hoist static JSX, use functional setState
4. **JS performance** — use Map/Set for O(1) lookups, combine array iterations, early returns
