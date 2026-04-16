import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for browser-based e2e tests.
 *
 * Uses a setup-project-per-role pattern: each role has a dedicated setup spec
 * that logs in via the Keycloak form and saves the browser session state.
 * Functional test projects then restore that state, bypassing the login redirect.
 */

const ROLES = ['admin', 'developer', 'qa', 'devops', 'pm', 'supervisor'] as const;

export default defineConfig({
  testDir: './tests',

  /* Global timeout per test — 30s covers slow CI modal interactions */
  timeout: 30_000,

  /* Retry once on CI to reduce flakes from service startup timing */
  retries: process.env.CI ? 1 : 0,

  /* 2 parallel workers in CI (full stack is running concurrently) */
  workers: process.env.CI ? 2 : undefined,

  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list'],
  ],

  use: {
    baseURL: 'http://localhost:3000',

    /* Capture screenshot only when a test fails */
    screenshot: 'only-on-failure',

    /* Retain video recording only for failing tests */
    video: 'retain-on-failure',

    /* Capture full trace on the first retry so failures can be debugged */
    trace: 'on-first-retry',

    ...devices['Desktop Chrome'],
  },

  /* Separate output dir for per-test artifacts (screenshots, videos, traces) */
  outputDir: 'test-results',

  projects: [
    // ── Setup projects ─────────────────────────────────────────────────────
    // Each runs the matching auth.setup.<role>.ts spec and saves session state.
    ...ROLES.map((role) => ({
      name: `setup-${role}`,
      testDir: './setup',
      testMatch: new RegExp(`auth\\.setup\\.${role}\\.ts`),
    })),

    // ── Functional test projects ──────────────────────────────────────────
    // Each restores saved auth state so tests start already logged in.
    ...ROLES.map((role) => ({
      name: `tests-${role}`,
      dependencies: [`setup-${role}`],
      use: { storageState: `.auth/${role}.json` },
    })),
  ],
});
