import { test, expect, request as playwrightRequest } from '@playwright/test';
import { USERS } from '../fixtures/roles';
import { getToken } from '../auth/get-token';

/**
 * Task creation tests.
 *
 * Write roles (ADMIN/DEVELOPER/QA/DEVOPS/PM):
 *   - Can open the 3-step create wizard and interact with step 1.
 *
 * SUPERVISOR:
 *   - "New Task" button is absent from the UI.
 *   - A direct API POST returns HTTP 403.
 *
 * Full wizard completion (steps 2 & 3) requires a project to exist in the
 * database. A shared API request context creates one in beforeAll using the
 * admin credentials; it is cleaned up in afterAll.
 */

const API_BASE = 'http://localhost:8080/api/v1';

let projectId: string;
let adminToken: string;

test.beforeAll(async () => {
  // Obtain an admin token via Keycloak's password grant (e2e-client).
  const ctx = await playwrightRequest.newContext();
  adminToken = await getToken(ctx, USERS.admin.username, USERS.admin.password);

  // Create a project that write-role tests can select in the wizard.
  const res = await ctx.post(`${API_BASE}/projects`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { name: `E2E-Project-${Date.now()}`, description: 'Created by e2e setup' },
  });
  expect(res.ok(), `Failed to create seed project: ${await res.text()}`).toBeTruthy();
  const project = await res.json() as { id: string };
  projectId = project.id;
  await ctx.dispose();
});

test.afterAll(async () => {
  // Soft-delete the seed project to leave the DB clean.
  const ctx = await playwrightRequest.newContext();
  await ctx.delete(`${API_BASE}/projects/${projectId}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  await ctx.dispose();
});

// ── Write roles ──────────────────────────────────────────────────────────────

test('write role — wizard opens and step 1 is interactive', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tests-supervisor', 'Supervisor cannot create tasks');

  await page.goto('/tasks');
  await expect(page.locator('.ant-table-wrapper')).toBeVisible({ timeout: 15_000 });
  await page.getByRole('button', { name: 'New Task' }).click();

  // Modal opens with the wizard.
  const modal = page.locator('.ant-modal-content');
  await expect(modal).toBeVisible();

  // Step 1 "Basics" fields are visible and editable.
  await expect(page.getByLabel('Title')).toBeVisible();
  await page.getByLabel('Title').fill('E2E smoke task');
  await expect(page.getByLabel('Title')).toHaveValue('E2E smoke task');

  // Close the modal without submitting.
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(modal).not.toBeVisible();
});

test('write role — full wizard creates a task', async ({ page }, testInfo) => {
  // Run only for DEVELOPER to avoid creating 5 duplicate tasks.
  test.skip(testInfo.project.name !== 'tests-developer', 'Full wizard run only for developer role');

  const taskTitle = `E2E-Task-${Date.now()}`;

  await page.goto('/tasks');
  await expect(page.locator('.ant-table-wrapper')).toBeVisible({ timeout: 15_000 });
  await page.getByRole('button', { name: 'New Task' }).click();

  const modal = page.locator('.ant-modal-content');
  await expect(modal).toBeVisible();

  // ── Step 1: Basics ─────────────────────────────────────────────────────────
  await page.getByLabel('Title').fill(taskTitle);
  await page.getByRole('button', { name: 'Next' }).click();

  // ── Step 2: Type & Project ─────────────────────────────────────────────────
  // Use keyboard navigation for Ant Design Selects — more reliable than clicking
  // portalled dropdown items whose DOM position depends on render timing.
  const projectSelect = page.locator('.ant-form-item').filter({ hasText: 'Project' }).locator('.ant-select-selector');
  await projectSelect.click();
  // Wait for dropdown to appear, then pick first option via keyboard.
  await page.locator('.ant-select-dropdown').first().waitFor({ state: 'visible' });
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');

  const typeSelect = page.locator('.ant-form-item').filter({ hasText: 'Type' }).locator('.ant-select-selector');
  await typeSelect.click();
  await page.locator('.ant-select-dropdown').first().waitFor({ state: 'visible' });
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');

  await page.getByRole('button', { name: 'Next' }).click();

  // ── Step 3: Assignee & Dates ───────────────────────────────────────────────
  const userSelect = page.locator('.ant-form-item').filter({ hasText: 'Assigned to' }).locator('.ant-select-selector');
  await userSelect.click();
  await page.locator('.ant-select-dropdown').first().waitFor({ state: 'visible' });
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');

  // Fill DatePicker inputs: triple-click selects existing text, then type replaces it.
  // Ant Design DatePicker expects YYYY-MM-DD; Tab closes the calendar without submitting.
  const startInput = page.locator('.ant-form-item').filter({ hasText: 'Planned Start' }).locator('input');
  await startInput.click({ clickCount: 3 });
  await page.keyboard.type('2027-06-01');
  await page.keyboard.press('Tab');

  const endInput = page.locator('.ant-form-item').filter({ hasText: 'Planned End' }).locator('input');
  await endInput.click({ clickCount: 3 });
  await page.keyboard.type('2027-08-01');
  await page.keyboard.press('Tab');

  await page.getByRole('button', { name: 'Create' }).click();

  // Modal closes and the new task appears in the table.
  await expect(modal).not.toBeVisible({ timeout: 10_000 });
  await expect(page.locator('.ant-table-row').filter({ hasText: taskTitle })).toBeVisible({ timeout: 10_000 });
});

// ── SUPERVISOR (read-only) ────────────────────────────────────────────────────

test('supervisor — "New Task" button is absent', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'tests-supervisor', 'Only runs for supervisor role');

  await page.goto('/tasks');
  await expect(page.locator('.ant-table-wrapper')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: 'New Task' })).not.toBeVisible();
});

test('supervisor — direct API POST to create task returns 403', async ({ request }, testInfo) => {
  test.skip(testInfo.project.name !== 'tests-supervisor', 'Only runs for supervisor role');

  const token = await getToken(request, USERS.supervisor.username, USERS.supervisor.password);

  const response = await request.post(`${API_BASE}/tasks`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      title: 'Supervisor should be blocked',
      projectId,
      type: 'FEATURE',
      status: 'TODO',
    },
  });

  expect(response.status()).toBe(403);
});
