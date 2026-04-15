import { test as setup } from '@playwright/test';
import { loginAs } from '../auth/keycloak-login';
import { USERS } from '../fixtures/roles';

/** Authenticates as the PM user and saves the browser session state. */
setup('authenticate as pm', async ({ page }) => {
  const { username, password, stateFile } = USERS.pm;
  await loginAs(page, username, password);
  await page.context().storageState({ path: stateFile });
});
