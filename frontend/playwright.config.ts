import { defineConfig } from '@playwright/test';

const baseURL = process.env.VERIFI_TEST_BASE_URL ?? 'http://127.0.0.1:5180';
const host = new URL(baseURL).hostname;
if (!['localhost', '127.0.0.1', '[::1]'].includes(host)) {
  throw new Error('The portal smoke test creates fictional records and requires a local server.');
}

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  reporter: 'list',
  use: { baseURL, browserName: 'chromium', screenshot: 'only-on-failure' },
});
