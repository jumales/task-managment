import { test, expect } from '@playwright/test';
import { USERS } from '../fixtures/roles';
import { getToken } from '../auth/get-token';

/**
 * Project creation tests.
 *
 * ADMIN:
 *   - Sees the "New Project" button on the Projects page.
 *   - Can create a project through the modal form.
 *
 * Non-admin write roles (DEVELOPER/QA/DEVOPS/PM):
 *   - "New Project" button is absent (ProjectsPage.tsx: `{isAdmin && <Button>}`).
 *   - A direct API POST returns HTTP 403.
 *
 * SUPERVISOR:
 *   - "New Project" button is absent.
 *   - A direct API POST returns HTTP 403.
 */

const API_BASE = 'http://localhost:8080/api/v1';

// ── ADMIN ────────────────────────────────────────────────────────────────────

test('admin — "New Project" button is visible', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'tests-admin', 'Only runs for admin role');

  await page.goto('/projects');
  await expect(page.locator('.ant-table-wrapper')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: 'New Project' })).toBeVisible();
});

test('admin — can create a project via the modal form', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'tests-admin', 'Only runs for admin role');

  const projectName = `E2E-Project-UI-${Date.now()}`;

  await page.goto('/projects');
  await expect(page.locator('.ant-table-wrapper')).toBeVisible({ timeout: 15_000 });

  await page.getByRole('button', { name: 'New Project' }).click();

  const modal = page.locator('.ant-modal-content');
  await expect(modal).toBeVisible();

  // Fill the project name (Form.Item name="name").
  await page.getByLabel('Name').fill(projectName);

  // Click the modal's OK button (rendered as the primary action button).
  await page.getByRole('button', { name: 'Create' }).click();

  // Modal closes and the new project appears in the table.
  await expect(modal).not.toBeVisible({ timeout: 10_000 });
  await expect(page.locator('.ant-table-row').filter({ hasText: projectName })).toBeVisible({ timeout: 10_000 });
});

// ── Non-admin roles ───────────────────────────────────────────────────────────

test('non-admin — "New Project" button is absent', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tests-admin', 'Admin can see the button');

  await page.goto('/projects');
  await expect(page.locator('.ant-table-wrapper')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: 'New Project' })).not.toBeVisible();
});

test('non-admin — direct API POST to create project returns 403', async ({ request }, testInfo) => {
  test.skip(testInfo.project.name === 'tests-admin', 'Admin is allowed to create projects');

  // Determine the current role from the project name.
  const role = testInfo.project.name.replace('tests-', '') as keyof typeof USERS;
  const user = USERS[role];

  const token = await getToken(request, user.username, user.password);

  const response = await request.post(`${API_BASE}/projects`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: `Blocked-${Date.now()}`, description: 'Should be rejected' },
  });

  expect(response.status()).toBe(403);
});
