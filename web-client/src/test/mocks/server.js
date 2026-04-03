import { setupServer } from 'msw/node';
import { handlers } from './handlers';
/**
 * MSW Node server used in Vitest.
 * Import this in every test file and wire up:
 *   beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
 *   afterEach(() => server.resetHandlers())
 *   afterAll(() => server.close())
 */
export const server = setupServer(...handlers);
