import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test as base, expect, type Page } from '@playwright/test';
import { PYTHON } from './python';

type Role = 'care_admin' | 'rehab_therapist' | 'patient';
type CaseData = {
  patientId: string;
  otherPatientId: string;
  prescriptionId: string;
  alertId: string;
  accounts: Record<Role, string>;
};

export const test = base.extend<{ data: CaseData }>({
  data: async ({}, use) => {
    const output = execFileSync(PYTHON, [fileURLToPath(new URL('./backend.py', import.meta.url)), 'seed'], {
      encoding: 'utf8', timeout: 15_000,
    });
    await use(JSON.parse(output));
  },
});

export async function login(page: Page, data: CaseData, role: Role = 'rehab_therapist', valid = true) {
  const password = process.env.E2E_PASSWORD;
  if (!password) throw new Error('E2E_PASSWORD must be initialized by the test configuration');
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(data.accounts[role]);
  await page.getByLabel('密码', { exact: true }).fill(valid ? password : `${password}-invalid`);
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith('/v1/auth/login') && response.request().method() === 'POST');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(valid ? 200 : 401);
  if (valid && role !== 'patient') await expect(page).toHaveURL(/\/patients$/);
  return response;
}

export { expect };
