import { Page } from '@playwright/test';

/**
 * Drives the Keycloak login form for a given user and waits until the React app
 * shell is fully rendered and authenticated.
 *
 * keycloak-js is configured with onLoad:'login-required' + pkceMethod:'S256', so
 * navigating to the React app immediately redirects to the Keycloak login page.
 * After a successful login, Keycloak redirects back and keycloak-js completes the
 * PKCE code exchange in memory. The session is then maintained via Keycloak's
 * KEYCLOAK_SESSION cookie on localhost:8180, which is captured when the caller
 * saves storageState — allowing subsequent test runs to skip the login form.
 *
 * Keycloak default theme field IDs (stable since Keycloak 17):
 *   #username, #password, #kc-login
 */
export async function loginAs(page: Page, username: string, password: string): Promise<void> {
  // Navigate to the React app — keycloak-js immediately redirects to Keycloak.
  await page.goto('http://localhost:3000/');

  // Wait for the Keycloak login page URL.
  await page.waitForURL('**/realms/demo/protocol/openid-connect/auth**', { timeout: 15_000 });

  // Wait for the login form to render before filling it.
  await page.waitForSelector('#username', { state: 'visible', timeout: 10_000 });

  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('#kc-login');

  // Keycloak redirects back to the React app with the auth code in the URL.
  await page.waitForURL('http://localhost:3000/**', { timeout: 15_000 });

  // Wait for AppLayout's <Layout.Sider> to render — this confirms that the React
  // component tree has mounted, AuthProvider has initialised keycloak-js, and the
  // token exchange has completed. Tokens are held in memory by keycloak-js (not
  // in localStorage), so checking localStorage is not reliable here.
  await page.waitForSelector('.ant-layout-sider', { state: 'visible', timeout: 15_000 });
}
