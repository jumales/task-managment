# E2E Playwright Tests

Browser-based end-to-end tests for all 6 Keycloak roles using [Playwright](https://playwright.dev/).
Tests drive a real Chromium browser through the React app, log in via the Keycloak form, and verify
elementary behaviors — login, task list, task creation, and project creation RBAC.

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Node.js | 20+ | `node --version` |
| npm | 10+ | bundled with Node.js |
| Java | 17 | required only if starting services locally |
| Docker Desktop | any recent | must be running |

---

## Full-stack setup (required before running tests)

Tests run against the **live application stack** — they are not unit tests and cannot mock services.
The following must be up and accessible before `npm test`:

| Service | URL | How to start |
|---|---|---|
| Keycloak | http://localhost:8180 | Docker Compose (see below) |
| API Gateway | http://localhost:8080 | `mvn spring-boot:run` in `api-gateway/` |
| task-service | random port | `mvn spring-boot:run` in `task-service/` |
| user-service | random port | `mvn spring-boot:run` in `user-service/` |
| React app (Vite) | http://localhost:3000 | `npm run dev` in `web-client/` |

### 1. Start infrastructure (Docker)

From the **project root**:

```bash
./scripts/start-dev.sh --docker-only --no-elk
```

This starts Postgres, Kafka, Keycloak, MinIO, Redis, and MailHog. Skip `--no-elk` if you need
Elasticsearch/Kibana, but it is not required for these tests.

Alternatively, start infrastructure directly:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.override.yml \
  up -d postgres kafka keycloak minio minio-init redis mailhog
```

Wait until Keycloak is ready:

```bash
until curl -sf http://localhost:8180/realms/demo > /dev/null; do sleep 3; done && echo "Keycloak ready"
```

### 2. Start microservices

From the **project root**, start the services needed for e2e tests:

```bash
# Eureka (service discovery — must start first)
cd eureka-server && mvn spring-boot:run &

# Wait for Eureka
until curl -sf http://localhost:8761/actuator/health > /dev/null; do sleep 3; done

# Core services (order doesn't matter)
cd api-gateway      && mvn spring-boot:run &
cd user-service     && mvn spring-boot:run &
cd task-service     && mvn spring-boot:run &
```

Or use the full dev script (opens a Terminal window per service on macOS):

```bash
./scripts/start-dev.sh
```

### 3. Start the React dev server

```bash
cd web-client
npm ci          # first time only
npm run dev
```

App will be served at http://localhost:3000. Keep this terminal running.

---

## Install Playwright

Run once inside this directory (`e2e-playwright/`):

```bash
npm ci
npx playwright install chromium --with-deps
```

`--with-deps` installs OS-level libraries required by Chromium on Linux.
On macOS those libraries are already present — the flag is harmless.

---

## Running tests

All commands below must be run from the `e2e-playwright/` directory.

### Run the full suite

```bash
npm test
# or
npx playwright test
```

Playwright runs 6 setup specs first (one per role — logs in and saves session state to `.auth/`),
then fans out into 6 test projects that restore those sessions. Total: ~30 test executions.

### Run a single spec file

```bash
npx playwright test tests/login.spec.ts
npx playwright test tests/tasks-list.spec.ts
npx playwright test tests/task-create.spec.ts
npx playwright test tests/project-create.spec.ts
```

### Run tests for a specific role only

```bash
npx playwright test --project tests-admin
npx playwright test --project tests-supervisor
npx playwright test --project tests-developer
```

Role project names: `tests-admin`, `tests-developer`, `tests-qa`, `tests-devops`, `tests-pm`, `tests-supervisor`.

### Run in headed mode (watch the browser)

```bash
npx playwright test --headed
npx playwright test --headed --project tests-admin
```

### Debug a failing test interactively

```bash
npx playwright test --debug tests/project-create.spec.ts
```

Opens the Playwright Inspector. Step through each action, inspect selectors, and see the live DOM.

### Run with slow-motion (useful for demos)

```bash
PWDEBUG=1 npx playwright test --headed tests/login.spec.ts
```

---

## Viewing test results

### HTML report (after any test run)

```bash
npm run report
# or
npx playwright show-report playwright-report
```

Opens the interactive HTML report in your default browser. Failed tests show:
- Full stack trace
- Screenshot at the point of failure
- Step-by-step timeline
- Video replay (if the test was retried)

### Report location

| Output | Path |
|---|---|
| HTML report | `playwright-report/index.html` |
| Per-test screenshots | `test-results/<test-name>/` |
| Per-test videos | `test-results/<test-name>/` |
| Per-test traces | `test-results/<test-name>/` |

To view a trace file (full browser recording with network, console, DOM snapshots):

```bash
npx playwright show-trace test-results/<test-name>/trace.zip
```

---

## Test coverage

| Spec | What it checks |
|---|---|
| `login.spec.ts` | All 6 roles: Keycloak login → app shell visible → username in header |
| `tasks-list.spec.ts` | All roles see task table; only SUPERVISOR is missing "New Task" button |
| `task-create.spec.ts` | Write roles open wizard; DEVELOPER completes full 3-step creation; SUPERVISOR blocked at UI + HTTP 403 |
| `project-create.spec.ts` | ADMIN creates project via modal; all non-admin roles blocked (no button + HTTP 403) |

### Roles under test

| Role | Username | Can write tasks | Can create projects |
|---|---|---|---|
| ADMIN | `admin-user` | yes | yes |
| DEVELOPER | `dev-user` | yes | no |
| QA | `qa-user` | yes | no |
| DEVOPS | `devops-user` | yes | no |
| PM | `pm-user` | yes | no |
| SUPERVISOR | `supervisor-user` | no | no |

---

## Troubleshooting

**`Error: No tests found` or setup specs fail immediately**  
Keycloak is not reachable. Verify `curl http://localhost:8180/realms/demo` returns JSON.

**Login setup passes but functional tests fail with 502/503**  
A microservice has not registered with Eureka yet. Wait ~30 seconds and retry, or check
`http://localhost:8761` to see which services are registered.

**`page.waitForURL` timeout on Keycloak redirect**  
The React app cannot reach Keycloak. Confirm `VITE_KEYCLOAK_URL=http://localhost:8180`
is set in `web-client/.env` and the Vite dev server is running on port 3000.

**Ant Design selectors not found**  
The app may still be loading. Increase the `timeout` in `playwright.config.ts`
from `30_000` to `60_000` on slower machines.

**`getToken` fails with 401**  
The `e2e-client` Keycloak client or test users are missing. Re-import the realm:
```bash
docker compose restart keycloak
```

---

## CI/CD

The workflow `.github/workflows/e2e.yml` is triggered manually from GitHub Actions
(**Actions → E2E Tests → Run workflow**). It:

1. Builds all Maven JARs (`-DskipTests`)
2. Starts Docker infrastructure (no ELK)
3. Starts all microservices as background Java processes
4. Starts the Vite dev server
5. Installs Playwright and runs the full suite
6. Uploads `playwright-report/` + `test-results/` as a **30-day artifact** (always, even on failure)

Download the artifact from the workflow run summary page to view the HTML report and screenshots locally.
