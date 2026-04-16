import { test, expect } from '@playwright/test';
import { USERS, RoleName } from '../fixtures/roles';

/**
 * Smoke test: every role can reach the application shell.
 *
 * This spec runs once per role project (tests-admin, tests-developer, etc.).
 * Each project restores a saved Keycloak session state so the browser is already
 * authenticated. The test derives which role it is from the Playwright project
 * name and verifies the matching user's display name in the header.
 */
test('authenticated user reaches the app shell', async ({ page }, testInfo) => {
  // 'tests-admin' → 'admin', 'tests-supervisor' → 'supervisor', etc.
  const role = testInfo.project.name.replace('tests-', '') as RoleName;
  const user = USERS[role];

  await page.goto('/');

  // AppLayout's <Layout.Sider> is the stable post-login landmark element.
  await expect(page.locator('.ant-layout-sider')).toBeVisible({ timeout: 15_000 });

  // The root route redirects to /dashboard.
  await expect(page).toHaveURL(/\/dashboard/);

  // Header displays the logged-in user's name via <Typography.Text>{name}</Typography.Text>.
  // Convert 'admin-user' → 'admin', 'dev-user' → 'dev' for a partial match.
  const namePart = user.username.split('-')[0];
  await expect(page.getByText(namePart, { exact: false })).toBeVisible();
});
