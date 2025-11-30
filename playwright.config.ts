import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for Vinto E2E tests
 *
 * Supports two modes:
 * 1. Local testing: Uses npm start to run dev server (default)
 * 2. CI/PR testing: Tests against Vercel preview deployment URL
 *
 * Set PLAYWRIGHT_TEST_BASE_URL environment variable to test against a specific URL
 */

// Use environment variable if provided (for Vercel previews), otherwise default to localhost
const baseURL = process.env.PLAYWRIGHT_TEST_BASE_URL || 'http://localhost:4200';
const isCI = !!process.env.CI;
const isVercelPreview = !!process.env.PLAYWRIGHT_TEST_BASE_URL;

export default defineConfig({
  testDir: './e2e',

  // Test timeout
  timeout: 60 * 1000,

  // Expect timeout for assertions
  expect: {
    timeout: 10 * 1000,
  },

  // Run tests in files in parallel
  fullyParallel: true,

  // Fail the build on CI if you accidentally left test.only in the source code
  forbidOnly: isCI,

  // Retry on CI only
  retries: isCI ? 2 : 0,

  // Opt out of parallel tests on CI
  workers: isCI ? 1 : undefined,

  // Reporter configuration
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
    ...(isCI ? [['github' as const]] : []),
  ],

  // Shared settings for all projects
  use: {
    baseURL,

    // Collect trace on failure for debugging
    trace: isCI ? 'retain-on-failure' : 'on-first-retry',

    // Screenshot on failure
    screenshot: 'only-on-failure',

    // Video on failure
    video: isCI ? 'retain-on-failure' : 'off',
  },

  // Configure projects for major browsers
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Uncomment to test on other browsers
    // {
    //   name: 'firefox',
    //   use: { ...devices['Desktop Firefox'] },
    // },
    // {
    //   name: 'webkit',
    //   use: { ...devices['Desktop Safari'] },
    // },
    // Mobile viewports
    // {
    //   name: 'Mobile Chrome',
    //   use: { ...devices['Pixel 5'] },
    // },
  ],

  // Run local dev server before starting tests (only when not testing Vercel preview)
  webServer: isVercelPreview ? undefined : {
    command: 'npm start',
    url: baseURL,
    reuseExistingServer: !isCI,
    timeout: 120 * 1000, // 2 minutes for server to start
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
