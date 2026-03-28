import { describe, it, expect } from 'vitest';
describe('apiClient', () => {
    it('uses the VITE_API_URL environment variable as the baseURL', async () => {
        // Set the env variable before importing so the module picks it up.
        const expectedBase = 'http://localhost:8080';
        // Vitest exposes import.meta.env via the define mechanism; we patch it here
        // by directly mutating the object that Vite injects in the test environment.
        import.meta.env.VITE_API_URL =
            expectedBase;
        // Re-import the module so it reads the updated env value.
        const mod = await import('./client?t=' + Date.now());
        const apiClient = mod.default;
        expect(apiClient.defaults.baseURL).toBe(expectedBase);
    });
});
