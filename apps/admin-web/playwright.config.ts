import { randomBytes, randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig, devices } from '@playwright/test';

const artifacts = fileURLToPath(new URL('../../.agent/tmp/admin-e2e/', import.meta.url));
mkdirSync(artifacts, { recursive: true });
process.env.E2E_RUN_ID ??= randomUUID();
process.env.E2E_PASSWORD ??= randomBytes(24).toString('hex');
process.env.REHAB_JWT_SECRET ??= randomBytes(32).toString('hex');
process.env.REHAB_DATABASE_URL = `sqlite:///${artifacts.replaceAll('\\', '/')}${process.env.E2E_RUN_ID}.db`;
process.env.VITE_API_BASE_URL = '/v1';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 5_000 },
  forbidOnly: true,
  outputDir: `${artifacts}/results`,
  reporter: [
    ['list'],
    ['html', { outputFolder: `${artifacts}/html`, open: 'never' }],
    ['json', { outputFile: `${artifacts}/results.json` }],
  ],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'python e2e/backend.py serve',
      url: 'http://127.0.0.1:18765/openapi.json',
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: 'npm run build && node e2e/preview.mjs',
      url: 'http://127.0.0.1:4173',
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
