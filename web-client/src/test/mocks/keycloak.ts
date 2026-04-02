import { vi } from 'vitest';

/**
 * Minimal keycloak-js stub for the jsdom test environment.
 * Exported as the default export so `import Keycloak from 'keycloak-js'`
 * resolves to this class instead of the real implementation that tries
 * to perform browser redirects.
 */
export default class KeycloakMock {
  token = 'mock-token';
  tokenParsed = { preferred_username: 'test-user', realm_access: { roles: ['ADMIN'] } };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  constructor(_config?: unknown) {}

  init = vi.fn().mockResolvedValue(true);
  login = vi.fn().mockResolvedValue(undefined);
  logout = vi.fn().mockResolvedValue(undefined);
  updateToken = vi.fn().mockResolvedValue(true);
}
