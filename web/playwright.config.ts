import { defineConfig } from '@playwright/test'

// CI installs its own Chromium; here a pre-installed one can be named with CHROMIUM_PATH.
const executablePath = process.env.CHROMIUM_PATH

export default defineConfig({
  testDir: 'tests',
  timeout: 30_000,
  use: {
    baseURL: 'http://localhost:4173',
    // An iPad in portrait.
    viewport: { width: 820, height: 1180 },
    hasTouch: true,
    launchOptions: executablePath ? { executablePath, args: ['--no-sandbox'] } : {},
  },
  webServer: {
    command: 'npm run build && npx vite preview --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
