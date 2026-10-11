import { defineConfig } from '@playwright/test'

// CI installs its own Chromium; here a pre-installed one can be named with CHROMIUM_PATH.
const executablePath = process.env.CHROMIUM_PATH
// Parallel checkouts each take their own port (PORT=4174 npx playwright test).
const port = Number(process.env.PORT ?? 4173)

export default defineConfig({
  testDir: 'tests',
  timeout: 30_000,
  // Pages wait on the stand-in Drive and Calendar; on a busy runner 5 seconds (the default) was sometimes too short.
  expect: { timeout: 10_000 },
  use: {
    baseURL: `http://localhost:${port}`,
    launchOptions: executablePath ? { executablePath, args: ['--no-sandbox'] } : {},
  },
  // Every test runs on an iPad in portrait and in a desktop browser window.
  projects: [
    { name: 'ipad', use: { viewport: { width: 820, height: 1180 }, hasTouch: true } },
    { name: 'desktop', use: { viewport: { width: 1440, height: 900 } } },
  ],
  webServer: {
    command: `npm run build && npx vite preview --port ${port} --strictPort`,
    url: `http://localhost:${port}`,
    reuseExistingServer: !process.env.CI,
    // The client ID is built in for the real site (.env.production); the tests use their own.
    env: { VITE_GOOGLE_CLIENT_ID: 'test.apps.googleusercontent.com' },
    timeout: 120_000,
  },
})
