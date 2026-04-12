# Finding #11 — Add pagination to GET /api/v1/projects

## Status
UNRESOLVED

## Severity
MEDIUM — unbounded list endpoint grows with project count; no pagination

## Context
`GET /api/v1/projects` returns all projects as a flat list. `TaskProjectService.findAll()` calls
`repository.findAll()` with no `Pageable` — the entire `task_projects` table is loaded into memory
and serialized. All other list endpoints in the codebase (tasks, users, etc.) are paginated.

## Root Cause
- `task-service/src/main/java/com/demo/task/controller/TaskProjectController.java:35` — `findAll()` with no Pageable
- `task-service/src/main/java/com/demo/task/service/TaskProjectService.java:36` — `repository.findAll()` (unbounded)

## Files to Modify

### 1. `task-service/src/main/java/com/demo/task/service/TaskProjectService.java`
```java
// Before:
public List<TaskProjectResponse> findAll() {
    return repository.findAll().stream().map(this::toResponse).toList();
}

// After:
/** Returns a page of projects ordered by name. */
public Page<TaskProjectResponse> findAll(Pageable pageable) {
    return repository.findAll(pageable).map(this::toResponse);
}
```
`JpaRepository` already extends `PagingAndSortingRepository` — `findAll(Pageable)` is available
without any new repository method.

### 2. `task-service/src/main/java/com/demo/task/controller/TaskProjectController.java`
```java
// Before:
@GetMapping
@PreAuthorize("isAuthenticated()")
@Operation(summary = "List all projects")
public ResponseEntity<List<TaskProjectResponse>> findAll() {
    return ResponseEntity.ok(projectService.findAll());
}

// After:
@GetMapping
@PreAuthorize("isAuthenticated()")
@Operation(summary = "List projects (paginated)")
public ResponseEntity<Page<TaskProjectResponse>> findAll(
        @PageableDefault(size = 50, sort = "name") Pageable pageable) {
    return ResponseEntity.ok(projectService.findAll(pageable));
}
```

### 3. `postman/task-service.postman_collection.json`
Update the "List Projects" request:
- Add query params: `page=0`, `size=20`
- Update test assertion to match the `PageResponse` shape (`content[]`, `totalElements`, etc.)

## Verification
1. `GET /api/v1/projects?page=0&size=10` — returns first 10 projects in `Page<>` wrapper
2. `GET /api/v1/projects?page=1&size=10` — returns next 10
3. `GET /api/v1/projects?sort=name,asc` — sorted by name ascending
4. Existing IT tests for project endpoints must still pass

## Notes
- Default sort `name` requires the `task_projects.name` column to be indexed if the table is large — verify with `explain analyze`
- The frontend currently calls this endpoint as a flat list; update `web-client/src/api/` accordingly after this backend change (ask if frontend update should be included)
- `@PageableDefault` prevents callers from accidentally requesting `size=Integer.MAX_VALUE`
