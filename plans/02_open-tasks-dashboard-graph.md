# Plan: Add "My Open Tasks vs Total Open Tasks per Project" Dashboard Graph

**Date:** 2026-04-13
**Status:** Accepted

## Goal

Add a second grouped bar chart to the Dashboard page showing, per project, how many open tasks are assigned to the current user versus the total number of open tasks across all users.

## Background / Context

The Dashboard currently shows only a "Planned vs Booked Hours by Project" bar chart. The user wants a second graph — "My Open Tasks vs Total Open Tasks per Project" — to give a quick visual comparison of personal workload versus project-level workload. "Open" is defined as tasks whose `phaseName IS NULL OR phaseName NOT IN ('RELEASED','REJECTED')`, consistent with `MyTasksService.FINISHED_PHASE_NAMES`. The seed script also needs updating so per-project user-assignment distribution is non-uniform, making the chart visually interesting (different "mine vs total" ratios per project).

## Scope

- `scripts/seed_task_data.py`
- `reporting-service` — new projection interface, new response DTO, updated repository, updated service, updated controller
- `web-client/src/api/types.ts`
- `web-client/src/api/reportingApi.ts`
- `web-client/src/pages/DashboardPage.tsx`
- `reporting-service/src/test/java/com/demo/reporting/ReportingControllerIT.java`
- `postman/reporting-service.postman_collection.json`

## Implementation Steps

1. Create branch `add_open_tasks_dashboard_graph` from `main`.

2. Run the seed script before implementing anything:
   ```bash
   python scripts/seed_task_data.py
   ```

3. **Seed script — `scripts/seed_task_data.py`:** Add `PROJECT_USER_WEIGHTS` constant after `PROJECT_NAMES` to assign admin-user to tasks at different rates per project:
   ```python
   PROJECT_USER_WEIGHTS = {
       "Seed Project Alpha":   [5, 1, 1, 1, 1, 1],  # admin ~50 %
       "Seed Project Beta":    [1, 4, 3, 2, 2, 2],  # admin ~7 %
       "Seed Project Gamma":   [2, 2, 2, 2, 2, 2],  # even ~17 %
       "Seed Project Delta":   [3, 1, 1, 2, 1, 1],  # admin ~33 %
       "Seed Project Epsilon": [1, 1, 1, 1, 3, 2],  # pm-heavy, admin ~11 %
   }
   ```
   Update `make_task()` to accept a `project_name: str` parameter and replace `random.choice(USER_IDS)` with `random.choices(USER_IDS, weights=..., k=1)[0]`. Update the call site in `seed()` to pass `project["name"]`.

4. **New projection interface — `reporting-service/src/main/java/com/demo/reporting/repository/ProjectTaskCountProjection.java`:** Define `getProjectId()`, `getProjectName()`, `getTotalOpenCount()`, `getMyOpenCount()`.

5. **New response DTO — `reporting-service/src/main/java/com/demo/reporting/dto/ProjectTaskCountResponse.java`:** Lombok `@Getter @AllArgsConstructor` with fields `projectId`, `projectName`, `myOpenCount`, `totalOpenCount`.

6. **Update `ReportTaskRepository`:** Add `countOpenByProject(@Param("userId") UUID userId, @Param("finishedPhaseNames") Collection<String> finishedPhaseNames)` with a JPQL query that groups by `projectId`/`projectName`, counts total and uses `SUM(CASE WHEN t.assignedUserId = :userId THEN 1 ELSE 0 END)` for the "mine" count, filtering out finished phases.

7. **Update `MyTasksService`:** Add `countOpenByProject(UUID userId)` public method that calls the repository and maps the projection to `ProjectTaskCountResponse`, defaulting null `myOpenCount` to `0L`.

8. **Update `ReportingController`:** Add `GET /tasks/open-by-project` endpoint annotated with `@PreAuthorize("isAuthenticated()")` and OpenAPI `@Operation` documentation; delegates to `myTasksService.countOpenByProject(resolveUserId(authentication))`.

9. **Frontend types — `web-client/src/api/types.ts`:** Add `ProjectTaskCountRow` interface with `projectId`, `projectName`, `myOpenCount`, `totalOpenCount`.

10. **Frontend API — `web-client/src/api/reportingApi.ts`:** Add `getOpenTasksByProject()` function returning `Promise<ProjectTaskCountRow[]>` via `GET /api/v1/reports/tasks/open-by-project`.

11. **Frontend page — `web-client/src/pages/DashboardPage.tsx`:** Add `counts / countsLoading / countsError` state; fire both fetches in parallel within the same `useEffect` (no `await` between them); add a second `<BarChart>` below the existing one with title "My Open Tasks vs Total Open Tasks — by Project", bar "Mine" in `#722ed1` (purple), bar "Total" in `#13c2c2` (teal), `ResponsiveContainer` height 320, no unit suffix on YAxis.

12. **Integration test — `ReportingControllerIT.java`:** Add `openTasksByProject_returnsCounts()` — save 3 tasks in project A (2 assigned to `TEST_USER_ID`, 1 to another user) and 1 task in project B (assigned to `TEST_USER_ID`); `GET /api/v1/reports/tasks/open-by-project`; assert project A has `totalOpenCount=3` and `myOpenCount=2`; assert project B has `totalOpenCount=1` and `myOpenCount=1`. Extend `buildTask` with a helper that sets `projectId` and `projectName`.

13. **Postman — `postman/reporting-service.postman_collection.json`:** Add request "Open task counts by project" under the "My Tasks" folder: `GET {{baseUrl}}/api/v1/reports/tasks/open-by-project`, Bearer `{{bearerToken}}`, test asserting status 200 and array response.

14. Run clean build from project root:
    ```bash
    mvn clean install -DskipTests=true
    ```

15. Push branch and open a PR:
    ```bash
    git push origin add_open_tasks_dashboard_graph
    ```

## Testing Plan

- `ReportingControllerIT.openTasksByProject_returnsCounts()` — covers the new endpoint end-to-end with real Testcontainers Postgres.
- Run with `mvn test -pl reporting-service`.
- Manual verification: start dev stack (`scripts/start-dev.sh`), run seed script, open dashboard — both charts must render; admin-user "Mine" bars should be tallest for Alpha and shortest for Beta.

## Postman / API Changes

- New endpoint: `GET /api/v1/reports/tasks/open-by-project`
- Response: array of `{ projectId, projectName, myOpenCount, totalOpenCount }`
- Add to `postman/reporting-service.postman_collection.json` under "My Tasks" folder.

## Risks / Considerations

- `SUM(CASE WHEN ...)` returns `null` when no rows match the userId condition; the service layer defaults this to `0L`.
- GitHub Actions CI check via `gh run watch` cannot be run in this session (no GitHub token available) — verify CI manually after push.

## Out of Scope

- Pagination or filtering on the new endpoint.
- Changes to any service other than `reporting-service`.
- Modifications to `scripts/start-dev.sh` or `scripts/stop-dev.sh` (no new Docker images or services added).
