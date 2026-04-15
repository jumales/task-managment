import { test, expect } from '@playwright/test';

/**
 * Verifies the Tasks page for all roles:
 * - All roles see the task table.
 * - Write roles (ADMIN/DEVELOPER/QA/DEVOPS/PM) see the "New Task" button.
 * - SUPERVISOR does not see the "New Task" button (UI guard in TasksPage.tsx:
 *   `{!isSupervisor && <Button type="primary" ...>New Task</Button>}`).
 */

test('task list table is visible', async ({ page }) => {
  await page.goto('/tasks');
  await expect(page.locator('.ant-table-wrapper')).toBeVisible({ timeout: 15_000 });
});

test('"New Task" button visibility matches role permissions', async ({ page }, testInfo) => {
  const isSupervisor = testInfo.project.name === 'tests-supervisor';

  await page.goto('/tasks');
  await expect(page.locator('.ant-table-wrapper')).toBeVisible({ timeout: 15_000 });

  const newTaskBtn = page.getByRole('button', { name: 'New Task' });

  if (isSupervisor) {
    // Supervisor is read-only; the button must be absent from the DOM.
    await expect(newTaskBtn).not.toBeVisible();
  } else {
    await expect(newTaskBtn).toBeVisible();
  }
});
