import { vi } from 'vitest';
/**
 * Minimal keycloak-js stub for the jsdom test environment.
 * Exported as the default export so `import Keycloak from 'keycloak-js'`
 * resolves to this class instead of the real implementation that tries
 * to perform browser redirects.
 */
export default class KeycloakMock {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    constructor(_config) {
        this.token = 'mock-token';
        this.tokenParsed = { preferred_username: 'test-user', realm_access: { roles: ['ADMIN'] } };
        this.init = vi.fn().mockResolvedValue(true);
        this.login = vi.fn().mockResolvedValue(undefined);
        this.logout = vi.fn().mockResolvedValue(undefined);
        this.updateToken = vi.fn().mockResolvedValue(true);
    }
}
