# New Entity

Scaffold a new JPA entity in the specified service module following project conventions.

## Required input
The user must provide:
- **Entity name** (e.g. `TaskLabel`)
- **Target service module** (e.g. `task-service`)
- **Fields** — name and type for each field

## Steps

### 1. Entity class
Create `<module>/src/main/java/com/demo/<service>/model/<EntityName>.java`:
- `@Entity`, `@Table(name = "<snake_case_plural>")`
- `@SQLDelete(sql = "UPDATE <table> SET deleted_at = NOW() WHERE id = ?")` — soft delete
- `@SQLRestriction("deleted_at IS NULL")` — filter deleted rows from all queries
- `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;`
- All requested fields; use `@Enumerated(EnumType.STRING)` for enums, `@Column(columnDefinition = "TEXT")` for long strings
- `private Instant deletedAt;` — soft-delete timestamp
- Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
- Javadoc comment explaining the entity's purpose

**Indexes — think before you add:**

Before writing the `@Table` annotation, reason about every query the repository will run and decide whether an index helps. Add indexes inside `@Table(name = "...", indexes = { ... })` and write a comment above each one explaining the decision. Use this checklist:

| Candidate | Add index? | Reasoning |
|---|---|---|
| FK columns queried with `findBy<FK>` or `existsBy<FK>` | **Yes** — foreign key lookups without an index cause full table scans as the table grows | `@Index(name = "idx_<table>_<fk>", columnList = "<fk_column>")` |
| Columns used in `WHERE` filters on list endpoints (e.g. `status`, `projectId`) | **Yes** — repeated filtered queries benefit from an index | add with comment explaining the filter |
| `deleted_at` alone | **No** — `@SQLRestriction` generates `WHERE deleted_at IS NULL`; a partial index would help at scale, but standard JPA `@Index` cannot express `WHERE deleted_at IS NULL`; leave a `// NOTE:` comment if the table is expected to grow large |
| `created_at` / `updated_at` used only for ordering | **No** unless pagination queries sort by this column on a large table |
| Unique business keys (e.g. `name` on a lookup table) | **Yes** — add both `@Column(unique = true)` and a `@UniqueConstraint` in `@Table` |
| `id` (primary key) | **Never** — already indexed by the database automatically |

Example showing the pattern:

```java
@Entity
@Table(
    name = "task_labels",
    indexes = {
        // task_id is the main FK: findByTaskId and existsByTaskId run on every request
        @Index(name = "idx_task_labels_task_id", columnList = "task_id"),
        // status is used in the list-by-status filter endpoint; without this index
        // the query scans all non-deleted rows on every call
        @Index(name = "idx_task_labels_status", columnList = "status")
    }
)
// NOTE: deleted_at is not indexed here; add a partial index manually in a
// DB migration if this table exceeds ~100k rows and soft-delete scans become slow.
@SQLDelete(sql = "UPDATE task_labels SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class TaskLabel { ... }
```

If no index is warranted, add a comment in the class explaining why:
```java
// No additional indexes: this table is only ever looked up by primary key (id).
```

### 2. Repository
Create `<module>/src/main/java/com/demo/<service>/repository/<EntityName>Repository.java`:
- Extends `JpaRepository<EntityName, UUID>`
- Add `boolean existsBy<RelatedField>(...)` methods for any FK fields (used for delete guards)
- If the entity has a bulk soft-delete need, add a `@Modifying @Query("UPDATE <EntityName> ...")` method instead of a derived `deleteBy...` method

### 3. DTOs (if used by 2+ modules, place in common)
Create in `common/src/main/java/com/demo/common/dto/`:
- `<EntityName>Request.java` — `@Data`, all mutable fields
- `<EntityName>Response.java` — `@Getter @AllArgsConstructor`, all fields including `id`

If the entity is only used within one module, place DTOs in that module instead.

### 4. Service
Create `<module>/src/main/java/com/demo/<service>/service/<EntityName>Service.java`:
- `findAll()`, `findById(UUID)`, `create(Request)`, `update(UUID, Request)`, `delete(UUID)`
- `delete`: call `getOrThrow(id)`, then check `existsBy<FK>(id)` for each relationship — throw `RelatedEntityActiveException("<EntityName>", "<related>")` if active relations exist
- Package-private `getOrThrow(UUID)` and `toResponse(Entity)` helpers for use by other services

### 5. Controller
Create `<module>/src/main/java/com/demo/<service>/controller/<EntityName>Controller.java`:
- `@Tag(name = "...", description = "...")`, `@RestController`, `@RequestMapping("/api/<plural>")`
- `GET /`, `GET /{id}`, `POST /` (`@ResponseStatus(CREATED)`), `PUT /{id}`, `DELETE /{id}` (`@ResponseStatus(NO_CONTENT)`)
- OpenAPI: `@Operation`, `@ApiResponse`(s), `@Parameter` on path/query variables
- Delegates entirely to service — no business logic in controller
- Update proper postman file

### 6. Integration test
Create `<module>/src/test/java/com/demo/<service>/<EntityName>ControllerIT.java`:
- `@Testcontainers`, `@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "eureka.client.enabled=false")`
- `@Container @ServiceConnection static PostgreSQLContainer<?>`
- `@BeforeEach` clears the repository
- Tests for each endpoint: happy path, 404 on unknown id, 409 when delete is blocked by related entity, soft-delete visibility

## Conventions checklist
- [ ] UUID primary key
- [ ] Soft delete (`@SQLDelete` + `@SQLRestriction` + `deletedAt` field)
- [ ] Indexes reasoned about and documented with comments; every FK column that appears in a `findBy`/`existsBy` query has an index
- [ ] No hard `DELETE` in services — `deleteById` is intercepted by `@SQLDelete`
- [ ] Bulk deletes use JPQL `UPDATE ... SET deletedAt = ...` (not derived `deleteBy...`)
- [ ] `RelatedEntityActiveException` on delete if active relations exist
- [ ] DTOs in `common` if used by 2+ modules
- [ ] Enums instead of magic strings
- [ ] OpenAPI on all controller methods
- [ ] Integration test for every endpoint
