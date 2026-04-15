# E2E Playwright Browser Tests + CI/CD

## Context

No browser-based e2e tests exist. Current `e2e-tests/` Maven module only tests Kafka→audit-service flows. Need browser tests that log in as all 6 Keycloak roles, confirm elementary UI/API behaviors, capture screenshots + stack traces on failure, and produce a downloadable artifact from a manually-triggered CI workflow.

---

## New Files

```
e2e-playwright/
  package.json
  playwright.config.ts
  tsconfig.json
  .gitignore
  fixtures/roles.ts
  auth/keycloak-login.ts
  setup/auth.setup.admin.ts          (×6 roles — all identical except user/path)
  setup/auth.setup.developer.ts
  setup/auth.setup.qa.ts
  setup/auth.setup.devops.ts
  setup/auth.setup.pm.ts
  setup/auth.setup.supervisor.ts
  tests/login.spec.ts
  tests/tasks-list.spec.ts
  tests/task-create.spec.ts
  tests/project-create.spec.ts
  .auth/                             (gitignored — runtime session JSON files)

.github/workflows/e2e.yml
```

---

## Architecture: Multi-Role Auth via StorageState

Playwright "setup projects" pattern:

1. **6 setup specs** run first — each calls `loginAs(page, user, pass)` then saves `page.context().storageState({ path: '.auth/<role>.json' })`
2. **6 functional test projects** each declare `use: { storageState: '.auth/<role>.json' }` and `dependencies: ['setup-<role>']`
3. Saved state includes the **Keycloak session cookie** on `localhost:8180` — when restored, `keycloak-js` silently re-authenticates without showing the login form

**Important**: `keycloak-js` stores tokens in memory, NOT localStorage. Do NOT check `localStorage.getItem('kc-token')`. Auth readiness = `.ant-layout-sider` visible.

---

## `fixtures/roles.ts`

```ts
export const USERS = {
  admin:      { username: 'admin-user',      password: 'admin123',      stateFile: '.auth/admin.json' },
  developer:  { username: 'dev-user',        password: 'dev123',        stateFile: '.auth/developer.json' },
  qa:         { username: 'qa-user',         password: 'qa123',         stateFile: '.auth/qa.json' },
  devops:     { username: 'devops-user',     password: 'devops123',     stateFile: '.auth/devops.json' },
  pm:         { username: 'pm-user',         password: 'pm123',         stateFile: '.auth/pm.json' },
  supervisor: { username: 'supervisor-user', password: 'supervisor123', stateFile: '.auth/supervisor.json' },
};
export const WRITE_ROLES = ['admin','developer','qa','devops','pm'] as const;
export const READ_ONLY_ROLES = ['supervisor'] as const;
```

---

## `auth/keycloak-login.ts` — Login Helper

```
1. page.goto('http://localhost:3000/')
2. page.waitForURL('**/realms/demo/protocol/openid-connect/auth**')
   — keycloak-js onLoad:'login-required' + pkceMethod:'S256' redirects here
3. page.waitForSelector('#username', { state: 'visible' })
   — Keycloak default theme; IDs stable since KC17
4. fill('#username', username), fill('#password', password), click('#kc-login')
5. page.waitForURL('http://localhost:3000/**')
   — KC redirects back; code exchange begins
6. page.waitForSelector('.ant-layout-sider', { state: 'visible', timeout: 15000 })
   — AppLayout.tsx <Layout.Sider> renders = React app fully loaded
```

---

## Test Specs

### `tests/login.spec.ts`
- 6 tests (one per role): navigate to `/dashboard`, assert `.ant-layout-sider` visible and user's display name in header
- Confirms: every role can authenticate and reach the app shell

### `tests/tasks-list.spec.ts`
- All 6 roles: navigate `/tasks`, assert `.ant-table-wrapper` visible
- SUPERVISOR: assert `Button:has-text("New Task")` NOT visible (TasksPage.tsx: `{!isSupervisor && <Button>}`)
- Write roles: assert "New Task" button IS visible

### `tests/task-create.spec.ts`
**DEVELOPER (representative write role) — allowed path:**
1. Navigate `/tasks` → click "New Task" button
2. Wait `.ant-modal-content` → 3-step wizard (Steps component, `wizardStep` state)
3. Step 0: fill `Form.Item[name="title"]` input, click Next
4. Step 1: select project from `Select` for projectId, select type, click Next
5. Step 2: select user from `Select` for assignedUserId, set dates via `.ant-picker-input input`, click Create
6. Assert modal closes, new task title appears in `.ant-table-row`

**Note on DatePicker**: Cannot use `page.fill()` — click picker, then type date into focused input + press Enter.

**SUPERVISOR — blocked path:**
1. Assert "New Task" button absent (UI guard)
2. Also make direct API call via `page.request.post('http://localhost:8080/api/v1/tasks', ...)` with Bearer token from `keycloak.token` (accessed via page.evaluate on the keycloak singleton) → assert HTTP 403

### `tests/project-create.spec.ts`
**ADMIN — allowed path:**
1. Navigate `/projects` → assert "New Project" button visible (`{isAdmin && <Button>}`)
2. Click → fill name with `E2E-Project-${Date.now()}` → click Create
3. Assert new project name in table

**Non-admin write roles (DEVELOPER, QA, DEVOPS, PM):**
1. Navigate `/projects` → assert "New Project" button NOT in DOM
2. Direct `page.request.post` → assert 403

**SUPERVISOR:**
1. Same as non-admin + button absent

---

## `playwright.config.ts` Key Settings

```ts
reporter: [['html', { outputFolder: 'playwright-report', open: 'never' }], ['list']],
use: {
  baseURL: 'http://localhost:3000',
  screenshot: 'only-on-failure',
  video: 'retain-on-failure',
  trace: 'on-first-retry',
  ...devices['Desktop Chrome'],
},
outputDir: 'test-results',
retries: process.env.CI ? 1 : 0,
workers: process.env.CI ? 2 : undefined,

projects: [
  // Setup (6): { name: 'setup-<role>', testMatch: /auth\.setup\.<role>\.ts/ }
  // Tests (6):  { name: 'tests-<role>', dependencies: ['setup-<role>'],
  //               use: { storageState: '.auth/<role>.json' } }
]
```

---

## `.github/workflows/e2e.yml`

**Trigger**: `workflow_dispatch` (manual only)  
**Runner**: `ubuntu-latest`, `timeout-minutes: 40`

### Steps in order:

1. **Checkout**

2. **Setup Java 17** (temurin, maven cache)

3. **Setup Node.js 20** (npm cache, path: `e2e-playwright/package-lock.json`)

4. **Build all JARs**
   ```bash
   mvn package -DskipTests --batch-mode -T 4
   ```

5. **Pre-pull Docker images** (postgres, kafka, keycloak, minio, redis, mailhog)

6. **Start infrastructure** — ELK excluded (saves ~2 GB RAM):
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.override.yml \
     up -d postgres kafka keycloak minio minio-init redis mailhog
   ```
   `docker-compose.override.yml` exposes Kafka on `localhost:9092` (required for host-side Java services)

7. **Wait for infra health** (docker inspect health status for postgres/kafka/redis/minio; curl for Keycloak `/realms/demo`)

8. **Start Eureka** (default profile = standalone, no flag needed):
   ```bash
   java -jar eureka-server/target/eureka-server-*.jar > /tmp/eureka.log 2>&1 &
   until curl -sf http://localhost:8761/actuator/health; do sleep 3; done
   ```

9. **Start all microservices in background**:
   ```bash
   java -jar api-gateway/target/api-gateway-*.jar --server.port=8080 > /tmp/api-gateway.log 2>&1 &
   java -jar user-service/target/user-service-*.jar          > /tmp/user-service.log 2>&1 &
   java -jar task-service/target/task-service-*.jar          > /tmp/task-service.log 2>&1 &
   java -jar audit-service/target/audit-service-*.jar        > /tmp/audit-service.log 2>&1 &
   java -jar file-service/target/file-service-*.jar          > /tmp/file-service.log 2>&1 &
   java -jar notification-service/target/notification-service-*.jar > /tmp/notification-service.log 2>&1 &
   java -jar reporting-service/target/reporting-service-*.jar       > /tmp/reporting-service.log 2>&1 &
   # search-service skipped (needs Elasticsearch)
   ```
   All services default to `localhost` for Kafka/Postgres/Keycloak/Eureka — no extra env vars needed.

10. **Wait for gateway + service registration**:
    ```bash
    until curl -sf http://localhost:8080/actuator/health; do sleep 3; done
    sleep 60   # grace period for Eureka registration propagation
    # poll until /api/v1/tasks returns non-503
    ```

11. **Start web-client Vite dev server**:
    ```bash
    cd web-client && npm ci && npm run dev &
    until curl -sf http://localhost:3000; do sleep 2; done
    ```
    `.env` already has correct URLs (`localhost:8180`, `localhost:8080`)

12. **Install Playwright**:
    ```bash
    cd e2e-playwright && npm ci
    npx playwright install chromium --with-deps
    ```

13. **Run tests** (`CI=true npx playwright test`)

14. **Upload artifact** (`if: always()`, 30-day retention):
    ```yaml
    path: |
      e2e-playwright/playwright-report/
      e2e-playwright/test-results/
    ```
    - `playwright-report/`: HTML report with all test results
    - `test-results/`: per-test screenshots, videos, traces for failures

---

## Critical Files to Read Before Implementing

| File | Why |
|---|---|
| `web-client/src/pages/TasksPage.tsx:260,310,325-432` | Task create wizard: step structure, form field names, button selectors |
| `web-client/src/pages/ProjectsPage.tsx:14,230` | Project create: `isAdmin` guard, modal structure |
| `web-client/src/main.tsx:10-26` | Keycloak init: `onLoad:'login-required'`, `pkceMethod:'S256'` |
| `web-client/src/components/AppLayout.tsx:79` | `.ant-layout-sider` — main auth-ready selector |
| `docker-compose.yml` + `docker-compose.override.yml` | Container names for health checks, Kafka port exposure |
| `.github/workflows/ci.yml` | Reference for Java setup, artifact upload syntax |

---

## Verification

After implementation, test locally:
1. Start full stack (`./scripts/start-dev.sh --docker-only`, then start services manually)
2. `cd e2e-playwright && npm ci && npx playwright install chromium`
3. `npx playwright test` — all 6 roles should pass login + task list specs
4. Intentionally break a selector to confirm screenshot is captured in `test-results/`
5. `npx playwright show-report playwright-report` — verify HTML report shows failure with screenshot
