# Versioning Rules

- **Never push directly to `main` or `master`** — direct pushes to protected branches are not allowed
- **Every task gets its own branch** — before starting work, create a new branch from `main` (unless already on a feature branch that hasn't been merged/deleted)
- **Branch naming** — use a short descriptive name in snake_case, e.g. `add_user_endpoint`, `fix_feign_auth`
- **Open a PR when the task is done** — do not merge directly; create a pull request and wait for review
- **Clean build before opening a PR** — run `mvn clean install -DskipTests=false` from the project root and confirm it passes before creating the PR; a failing build must be fixed first

# Database Schema Changes

- **Use Flyway for all schema changes** — never modify the database manually or rely on `spring.jpa.hibernate.ddl-auto: update`; production services use `validate`
- **Migration files are immutable** — once a migration is merged to `main`, never edit it; create a new versioned file instead
- **Naming convention** — `V{n}__{short_description}.sql`, e.g. `V2__add_due_date_to_tasks.sql`
- **Each service owns its schema** — no migration file may create or alter tables that belong to another service
- **Backward-compatible changes preferred** — add columns as nullable; never drop or rename a column in one step; use a multi-step deprecation (add → migrate data → drop in a later version)
- **Partial unique indexes for soft-delete tables** — use `CREATE UNIQUE INDEX ... WHERE deleted_at IS NULL` instead of `UNIQUE` constraints so soft-deleted rows do not block re-insertion

# Creating a new service
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
- If is entity, use UUID for id
- Use enumeration instead of hardcoded strings in code
- write integration tests

# Creating controller
- controller needs to use dto classes, not expose model class
- create OpenAPI documentation

# Delete
- always soft delete
- don't delete if exists related entities

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
