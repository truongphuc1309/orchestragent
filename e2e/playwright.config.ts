import { defineConfig, devices } from '@playwright/test'
import { FRONTEND_URL } from './support/env'

/**
 * End-to-end tests: a real browser against the real backend, a real database and a real daemon with the mock
 * provider (no AI model is ever called). `support/global-setup.ts` starts everything; see the README.
 *
 * The browser is the Chrome (or Edge) already installed on the computer, so nothing is downloaded. Set
 * E2E_BROWSER=msedge to use Edge, or E2E_BROWSER=chromium after "playwright install chromium".
 */
export default defineConfig({
  testDir: './tests',
  globalSetup: './support/global-setup.ts',
  // One workspace, one daemon: the tests share state in order.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    ...devices['Desktop Chrome'],
    channel: process.env.E2E_BROWSER === 'chromium' ? undefined : (process.env.E2E_BROWSER ?? 'chrome'),
    baseURL: FRONTEND_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
