import { defineConfig, devices, Project } from '@playwright/test';
import { nxE2EPreset } from '@nx/playwright/preset';
import { workspaceRoot } from '@nx/devkit';

// For CI, you may want to set BASE_URL to the deployed application.
const baseURL = process.env.PLAYWRIGHT_TEST_BASE_URL || 'http://localhost:3000';
const isCI = !!process.env.CI;
const isVercelPreview = !!process.env.PLAYWRIGHT_TEST_BASE_URL;
const isPR = !!process.env.IS_PR;
const isNightly = !!process.env.IS_NIGHTLY;
const prBrowsers: Project[] = [
  {
    name: 'chromium',
    use: { ...devices['Desktop Chrome'] },
  },
  {
    name: 'Mobile Chrome',
    use: { ...devices['Pixel 5'] },
  },
];
const nightlyBrowsers: Project[] = [
  {
    name: 'chromium',
    use: { ...devices['Desktop Chrome'] },
  },
  {
    name: 'Mobile Chrome',
    use: { ...devices['Pixel 5'] },
  },
  {
    name: 'firefox',
    use: { ...devices['Desktop Firefox'] },
  },

  {
    name: 'webkit',
    use: { ...devices['Desktop Safari'] },
  },
  {
    name: 'Mobile Safari',
    use: { ...devices['iPhone 12'] },
  },
];

const defaultBrowsers: Project[] = [
  {
    name: 'Mobile Chrome',
    use: { ...devices['Pixel 5'] },
  },
];

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
// require('dotenv').config();

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  ...nxE2EPreset(__filename, { testDir: './e2e' }),
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    baseURL,
    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: isCI ? 'retain-on-failure' : 'on-first-retry',
    screenshot: isCI ? 'only-on-failure' : 'off',
    video: 'off',
    launchOptions: {
      args: ['--disable-dev-shm-usage'],
    },
  },
  /* Run your local dev server before starting the tests */
  webServer: isVercelPreview
    ? undefined
    : {
        command: 'npx nx dev @vinto/game',
        url: 'http://localhost:3000',
        reuseExistingServer: true,
        cwd: workspaceRoot,
      },
  // Fail the build on CI if you accidentally left test.only in the source code
  forbidOnly: isCI,
  // Retry on CI only
  retries: isCI ? 2 : 0,
  workers: isCI ? '50%' : undefined,
  reporter: isCI
    ? [
        ['html', { outputFolder: '../../playwright-report' }],
        ['json', { outputFile: '../../playwright-report/results.json' }],
        ['list'],
        ['github' as const],
      ]
    : [
        ['html', { outputFolder: '../../playwright-report' }],
        ['json', { outputFile: '../../playwright-report/results.json' }],
        ['list'],
      ],

  projects: isPR ? prBrowsers : isNightly ? nightlyBrowsers : defaultBrowsers,
});
