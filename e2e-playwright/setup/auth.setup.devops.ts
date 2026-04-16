import { test as setup } from '@playwright/test';
import { loginAs } from '../auth/keycloak-login';
import { USERS } from '../fixtures/roles';

/** Authenticates as the DEVOPS user and saves the browser session state. */
setup('authenticate as devops', async ({ page }) => {
  const { username, password, stateFile } = USERS.devops;
  await loginAs(page, username, password);
  await page.context().storageState({ path: stateFile });
});
