/**
 * k6 Load Test — 100 Concurrent Users
 *
 * Simulates 100 virtual users (VUs) running realistic workflows against the
 * full microservices stack through the API Gateway.
 *
 * Prerequisites:
 *   - Full stack running: ./scripts/start-dev.sh
 *   - Seed data present: python scripts/seed_task_data.py   (optional but recommended)
 *   - k6 installed: brew install k6
 *
 * Usage:
 *   k6 run scripts/k6-load-test.js
 *   k6 run --vus 50 --duration 1m scripts/k6-load-test.js   # quick smoke test
 *   k6 run scripts/k6-load-test.js --out json=results.json   # export raw results
 *
 * User roles and workflows:
 *   Write-capable roles (ADMIN, DEVELOPER, QA, DEVOPS, PM) — full workflow:
 *     1. Authenticate with Keycloak (token cached, refreshed before expiry)
 *     2. List tasks for a random project (paginated read)
 *     3. Get a single task by ID (point read)
 *     4. Create a new task (write — exercises DB, Kafka outbox, audit)
 *     5. Add a comment on the new task (write)
 *     6. Log booked work on an existing task (write — 50% of iterations)
 *
 *   Read-only role (SUPERVISOR) — read-only workflow:
 *     1. Authenticate with Keycloak
 *     2. List tasks for a random project (paginated read)
 *     3. Get a single task by ID (point read)
 *     Skips all write operations — SUPERVISOR is intentionally blocked from
 *     POST/PUT/PATCH/DELETE by the shared SecurityConfig.
 *
 * Thresholds (fail the run if violated):
 *   - 95th-percentile response time < 2 000 ms (overall)
 *   - 95th-percentile list-tasks time  < 1 000 ms
 *   - 95th-percentile task-create time < 2 000 ms
 *   - Error rate < 5 %
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ── Endpoints ─────────────────────────────────────────────────────────────────

const BASE_URL = 'http://localhost:8080';
const TOKEN_URL = 'http://localhost:8180/realms/demo/protocol/openid-connect/token';
const CLIENT_ID = 'e2e-client';
const CLIENT_SECRET = 'e2e-secret';

// ── Test users (from demo-realm.json) ────────────────────────────────────────

// Write-capable roles: ADMIN, DEVELOPER, QA, DEVOPS, PM
// These users can POST/PUT/PATCH/DELETE (enforced by common SecurityConfig).
const WRITE_USERS = [
  { username: 'admin-user',  password: 'admin123',  readOnly: false },
  { username: 'dev-user',    password: 'dev123',    readOnly: false },
  { username: 'qa-user',     password: 'qa123',     readOnly: false },
  { username: 'devops-user', password: 'devops123', readOnly: false },
  { username: 'pm-user',     password: 'pm123',     readOnly: false },
];

// Read-only role: SUPERVISOR — has WEB_APP (reads allowed) but no write role.
// Write operations (POST/PUT/PATCH/DELETE) return 403 — excluded from write workflows.
const READ_USERS = [
  { username: 'supervisor-user', password: 'supervisor123', readOnly: true },
];

// All users interleaved so VUs are distributed across both write and read roles.
// Pattern: 5 write users followed by 1 read-only user (matches the demo realm setup).
const USERS = [...WRITE_USERS, ...READ_USERS];

const TASK_TYPES   = ['FEATURE', 'BUG_FIXING', 'TESTING', 'DOCUMENTATION', 'OTHER'];
const WORK_TYPES   = ['DEVELOPMENT', 'TESTING', 'CODE_REVIEW', 'DESIGN', 'DOCUMENTATION'];

// ── Custom metrics ────────────────────────────────────────────────────────────

const authFailures      = new Rate('auth_failures');
const taskListDuration  = new Trend('task_list_ms',    true);
const taskGetDuration   = new Trend('task_get_ms',     true);
const taskCreateDuration = new Trend('task_create_ms', true);
const commentDuration   = new Trend('comment_ms',      true);
const bookedWorkDuration = new Trend('booked_work_ms', true);

// ── Load profile ──────────────────────────────────────────────────────────────

export const options = {
  stages: [
    { duration: '30s', target: 100 },  // ramp up:  0 → 100 VUs
    { duration: '2m',  target: 100 },  // sustain:  100 VUs for 2 minutes
    { duration: '30s', target: 0   },  // ramp down: 100 → 0 VUs
  ],
  thresholds: {
    // Overall HTTP error rate must stay below 5%.
    // 429 (rate-limited) responses are excluded via http.setResponseCallback below —
    // the gateway intentionally limits comments to 30 req/min per user, so 429s are
    // expected under 100-VU load and should not count as failures.
    http_req_failed: ['rate<0.05'],
    // 95th percentile for every operation
    http_req_duration:  ['p(95)<2000'],
    task_list_ms:       ['p(95)<1000'],
    task_get_ms:        ['p(95)<500'],
    task_create_ms:     ['p(95)<2000'],
    comment_ms:         ['p(95)<1000'],
    booked_work_ms:     ['p(95)<1000'],
  },
};

// ── Per-VU token state (module-level — each VU gets its own JS context) ───────

let cachedToken  = null;
let tokenExpiry  = 0;   // epoch ms

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Pick a random element from an array. */
function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/** Build Authorization + Content-Type headers for a given token. */
function authHeaders(token) {
  return {
    Authorization:  `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

/**
 * Exchange username + password for a Keycloak access token.
 * Returns { accessToken, expiresAt } or null on failure.
 */
function login(user) {
  const res = http.post(TOKEN_URL, {
    grant_type:    'password',
    client_id:     CLIENT_ID,
    client_secret: CLIENT_SECRET,
    username:      user.username,
    password:      user.password,
  });

  const ok = check(res, {
    'auth: status 200':      (r) => r.status === 200,
    'auth: has access_token': (r) => {
      try { return !!r.json('access_token'); } catch { return false; }
    },
  });

  authFailures.add(!ok);

  if (!ok) {
    console.error(`[VU ${__VU}] Login failed for ${user.username}: HTTP ${res.status}`);
    return null;
  }

  const expiresIn = res.json('expires_in') || 300; // default 5 min
  return {
    accessToken: res.json('access_token'),
    expiresAt:   Date.now() + (expiresIn - 30) * 1000, // 30 s safety buffer
  };
}

// ── Per-VU role state ─────────────────────────────────────────────────────────

let vuReadOnly = null; // set on first getToken() call; stable for the VU's lifetime

/**
 * Return a valid token for this VU, refreshing automatically when near expiry.
 * Each VU is assigned a user by round-robin on __VU. Also sets the module-level
 * {@code vuReadOnly} flag on first call so the default() function knows whether
 * this VU may perform write operations.
 */
function getToken() {
  if (cachedToken && Date.now() < tokenExpiry) {
    return cachedToken;
  }
  const user = USERS[(__VU - 1) % USERS.length];
  const auth = login(user);
  if (!auth) return null;
  cachedToken = auth.accessToken;
  tokenExpiry = auth.expiresAt;
  vuReadOnly   = user.readOnly;
  return cachedToken;
}

// ── Known Keycloak user IDs (hardcoded from demo-realm.json) ─────────────────
// These are the sub claims used by task-service's userClient.getUserById().
const KNOWN_USER_IDS = [
  '11111111-1111-1111-1111-111111111101', // admin-user
  '11111111-1111-1111-1111-111111111102', // dev-user
  '11111111-1111-1111-1111-111111111103', // qa-user
  '11111111-1111-1111-1111-111111111104', // devops-user
  '11111111-1111-1111-1111-111111111105', // pm-user
  '11111111-1111-1111-1111-111111111106', // supervisor-user
];

// ── Mark 429 (rate-limited) as non-failed ────────────────────────────────────
// The API gateway limits comment POSTs to 30 req/min per user. Under 100 VUs sharing
// 6 accounts, each user generates ~360 req/min, so most comment requests are expected
// to receive 429. Excluding 429 from http_req_failed ensures the threshold measures
// real service errors (5xx, 4xx except rate-limits) rather than intentional throttling.
http.setResponseCallback(http.expectedStatuses(
  { min: 200, max: 299 }, // all 2xx — normal success
  422,                    // business-logic rejection (PLANNING/FINISHED phase guards)
  429,                    // rate-limited — expected under heavy load, not a service error
));

// ── Setup: run once before any VU starts ──────────────────────────────────────

/**
 * Authenticate as admin and fetch the list of project IDs.
 * The returned object is passed as `data` to every default() call.
 */
export function setup() {
  const auth = login(USERS[0]);
  if (!auth) {
    throw new Error('setup() login failed — is Keycloak running?');
  }

  const headers = authHeaders(auth.accessToken);

  // Fetch up to 50 projects so VUs can spread writes across them
  const res = http.get(`${BASE_URL}/api/v1/projects?page=0&size=50`, { headers });

  check(res, { 'setup: projects 200': (r) => r.status === 200 });

  // Handle both paginated { content: [...] } and plain-array responses
  let projects = [];
  try {
    const body = res.json();
    projects = Array.isArray(body) ? body : (body.content || []);
  } catch (e) {
    throw new Error(`setup() could not parse projects response: ${res.body}`);
  }

  if (projects.length === 0) {
    throw new Error(
      'setup() found 0 projects. Create at least one project or run: python scripts/seed_task_data.py'
    );
  }

  // Fetch user IDs from the user-service so task creation has valid assignedUserId values
  const usersRes = http.get(`${BASE_URL}/api/v1/users?page=0&size=50`, { headers });
  let userIds = KNOWN_USER_IDS; // fallback to hardcoded IDs if user-service unavailable
  try {
    const usersBody = usersRes.json();
    const users = Array.isArray(usersBody) ? usersBody : (usersBody.content || []);
    if (users.length > 0) {
      userIds = users.map((u) => u.id);
    }
  } catch (_) { /* keep fallback */ }

  console.log(`setup() found ${projects.length} project(s) and ${userIds.length} user(s) — test starting.`);
  return { projectIds: projects.map((p) => p.id), userIds };
}

// ── Default: the per-VU, per-iteration scenario ───────────────────────────────

export default function (data) {
  const token = getToken();
  if (!token) {
    // Auth failed — skip this iteration and back off
    sleep(2);
    return;
  }

  const headers    = authHeaders(token);
  const projectId  = pick(data.projectIds);
  const userId     = pick(data.userIds);

  // ── 1. List tasks (paginated read — most common real-world action) ──────────

  const t0     = Date.now();
  const listRes = http.get(
    `${BASE_URL}/api/v1/tasks?projectId=${projectId}&page=0&size=20`,
    { headers }
  );
  taskListDuration.add(Date.now() - t0);

  const listOk = check(listRes, {
    'list tasks: 200': (r) => r.status === 200,
  });

  // Extract a task ID from the list for subsequent reads/writes
  let existingTaskId = null;
  if (listOk) {
    try {
      const body  = listRes.json();
      const tasks = Array.isArray(body) ? body : (body.content || []);
      if (tasks.length > 0) {
        existingTaskId = pick(tasks).id;
      }
    } catch (_) { /* ignore parse errors */ }
  }

  sleep(0.2); // think time between actions

  // ── 2. Get single task (point read) ────────────────────────────────────────

  if (existingTaskId) {
    const t1   = Date.now();
    const getRes = http.get(`${BASE_URL}/api/v1/tasks/${existingTaskId}`, { headers });
    taskGetDuration.add(Date.now() - t1);

    check(getRes, { 'get task: 200': (r) => r.status === 200 });
  }

  sleep(0.3);

  // ── Read-only VUs (SUPERVISOR role) stop here ──────────────────────────────
  // SUPERVISOR has WEB_APP for reads but no write role. POST/PUT/PATCH/DELETE
  // return 403 from the shared SecurityConfig. Read-only VUs model real
  // supervisor behaviour: browse and review without modifying data.
  if (vuReadOnly) {
    sleep(0.5 + Math.random());
    return;
  }

  // ── 3. Create a new task (write — exercises Kafka outbox + audit) ───────────

  // planned window: today → +7 days (ISO strings, required by the API)
  const now          = new Date();
  const plannedStart = now.toISOString();
  const plannedEnd   = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();

  const t2        = Date.now();
  const createRes = http.post(
    `${BASE_URL}/api/v1/tasks`,
    JSON.stringify({
      title:          `[k6] VU-${__VU} iter-${__ITER} ${Date.now()}`,
      description:    'Automated load-test task — safe to delete.',
      status:         'TODO',
      type:           pick(TASK_TYPES),
      progress:       0,
      projectId:      projectId,
      assignedUserId: userId,
      plannedStart:   plannedStart,
      plannedEnd:     plannedEnd,
      // phaseId omitted → service assigns PLANNING phase automatically
    }),
    { headers }
  );
  taskCreateDuration.add(Date.now() - t2);

  const createOk   = check(createRes, { 'create task: 201': (r) => r.status === 201 });
  let   newTaskId  = null;
  if (createOk) {
    try { newTaskId = createRes.json('id'); } catch (_) { /* ignore */ }
  }

  sleep(0.3);

  // ── 4. Add a comment on the new task ───────────────────────────────────────

  if (newTaskId) {
    const t3        = Date.now();
    const commentRes = http.post(
      `${BASE_URL}/api/v1/tasks/${newTaskId}/comments`,
      JSON.stringify({ content: `Load test comment from VU ${__VU} at ${new Date().toISOString()}` }),
      { headers }
    );
    commentDuration.add(Date.now() - t3);

    check(commentRes, {
      // 429 = rate-limited by the gateway (30 req/min per user) — expected under 100-VU load
      'add comment: 201 or 429': (r) => r.status === 201 || r.status === 429,
    });
  }

  sleep(0.3);

  // ── 5. Log booked work on an existing task (50% of iterations) ─────────────
  //   Only do this half the time to keep the write ratio realistic.

  if (existingTaskId && Math.random() < 0.5) {
    const t4           = Date.now();
    const bookedRes    = http.post(
      `${BASE_URL}/api/v1/tasks/${existingTaskId}/booked-work`,
      JSON.stringify({
        workType:    pick(WORK_TYPES),
        bookedHours: Math.ceil(Math.random() * 4) + 1, // 2–5 hours
      }),
      { headers }
    );
    bookedWorkDuration.add(Date.now() - t4);

    check(bookedRes, {
      'booked work: 201 or 422': (r) => r.status === 201 || r.status === 422,
      // 422 is expected when the task is in a FINISHED phase (RELEASED/REJECTED)
    });
  }

  // ── Think time before the next iteration ───────────────────────────────────
  //   Simulates a user reading results before their next action (0.5–1.5 s).
  sleep(0.5 + Math.random());
}
