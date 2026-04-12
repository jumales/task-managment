# Finding #10 — Replace unbounded findAll() in HoursReportService

## Status
UNRESOLVED

## Severity
MEDIUM — full table scan on `report_tasks` grows unboundedly with task count

## Context
`HoursReportService.byProject()` (line 80 in `HoursReportService.java`) calls
`taskRepository.findAll()` to build a `Map<UUID, String>` of project names. This fetches every
row in the `report_tasks` table — a Kafka-driven read model that grows with every task event.
The only needed data is the distinct `(projectId, projectName)` pairs for the projects referenced
by the already-fetched booked/planned work rows.

## Root Cause
`reporting-service/src/main/java/com/demo/reporting/service/HoursReportService.java:80`
```java
Map<UUID, String> projectNames = taskRepository.findAll().stream()   // full scan
        .collect(Collectors.toMap(ReportTask::getProjectId, ReportTask::getProjectName,
                (a, b) -> a));
```

## Files to Modify

### 1. `reporting-service/src/main/java/com/demo/reporting/repository/ReportTaskRepository.java`
Add a targeted query returning only project ID + name for a given set of project IDs:
```java
/**
 * Returns distinct (projectId, projectName) pairs for the given project IDs.
 * Used to enrich hours report rows without scanning the full report_tasks table.
 */
@Query("SELECT DISTINCT t.projectId, t.projectName FROM ReportTask t WHERE t.projectId IN :ids")
List<Object[]> findProjectNamesByIds(@Param("ids") Set<UUID> ids);
```

### 2. `reporting-service/src/main/java/com/demo/reporting/service/HoursReportService.java`
In `byProject()`, replace the `findAll()` call:

```java
// Step 1: collect distinct projectIds from already-fetched work rows
// (booked and planned work aggregations are done in earlier lines)
Set<UUID> projectIds = /* collect from aggregated rows */;

// Step 2: targeted query instead of findAll()
Map<UUID, String> projectNames = taskRepository.findProjectNamesByIds(projectIds)
        .stream()
        .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> (String) row[1],
                (a, b) -> a
        ));
```

If `projectIds` is already derived from the booked/planned work maps (lines 70–78), reuse those
key sets rather than introducing a separate pass.

## Verification
1. Call `GET /api/v1/reports/hours/by-project`
2. Check Hibernate SQL logs — must show one targeted query with `WHERE project_id IN (...)` instead of `SELECT * FROM report_tasks`
3. Response content must be identical to the previous implementation

## Notes
- `Object[]` return from native/JPQL projections: index 0 = `projectId` (UUID), index 1 = `projectName` (String)
- An alternative is to create a proper projection interface: `interface ProjectNameProjection { UUID getProjectId(); String getProjectName(); }` — cleaner but slightly more code
- If `projectIds` set is empty (no work logged), skip the query and return empty map to avoid `IN ()` SQL error
