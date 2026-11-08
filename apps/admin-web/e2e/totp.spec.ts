import { createHmac } from 'node:crypto';
import { test, expect, login } from './fixtures';

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/**
 * 认证器一端的 RFC 6238 实现（与 services/api/app/core/totp.py 同构）。
 *
 * 服务端拒绝重放已消费的时间步，所以「确认启用」用过的码不能紧接着用于登录；
 * 传 at 偏移到下一步即可，服务端允许 ±1 步的时钟漂移。
 */
function totpCode(secret: string, at: number = Date.now()): string {
  let bits = 0;
  let value = 0;
  const key: number[] = [];
  for (const char of secret.replace(/=+$/, '').toUpperCase()) {
    value = (value << 5) | BASE32.indexOf(char);
    bits += 5;
    if (bits >= 8) {
      key.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }

  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 1000 / 30)));
  const digest = createHmac('sha1', Buffer.from(key)).update(counter).digest();
  const offset = digest[digest.length - 1] & 0x0f;
  const binary = digest.readUInt32BE(offset) & 0x7fffffff;
  return String(binary % 1_000_000).padStart(6, '0');
}

const NEXT_STEP = 30_000;

/** 走完页面上的「启用」流程，返回认证器密钥。 */
async function enableTotp(page: import('@playwright/test').Page): Promise<string> {
  await page.goto('/account/security');
  await expect(page.getByRole('heading', { name: '账号安全' })).toBeVisible();
  await expect(page.getByText('未启用', { exact: true })).toBeVisible();

  const password = process.env.E2E_PASSWORD;
  if (!password) {
    throw new Error('E2E_PASSWORD must be initialized by the test configuration');
  }

  // 启用前必须再验证一次口令：只凭会话不能绑定认证器。
  const enrollForm = page.getByRole('form', { name: '启用二次验证' });
  await enrollForm.getByLabel('登录口令').fill(password);
  await enrollForm.getByRole('button', { name: '启用二次验证' }).click();
  const secret = (await page.getByTestId('totp-secret').innerText()).trim();
  expect(secret.length).toBeGreaterThanOrEqual(16);

  const form = page.getByRole('form', { name: '确认启用二次验证' });
  await form.getByLabel('认证器验证码').fill(totpCode(secret));
  await form.getByRole('button', { name: '确认启用' }).click();
  await expect(page.getByText('已启用', { exact: true })).toBeVisible();
  return secret;
}

test('账号安全页可自助启用二次验证，之后登录必须带验证码', async ({ page, data }) => {
  await login(page, data, 'rehab_therapist');
  const secret = await enableTotp(page);

  const password = process.env.E2E_PASSWORD;
  if (!password) throw new Error('E2E_PASSWORD must be initialized by the test configuration');

  // 退出后重新登录：只填口令会被拒。
  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel('账号', { exact: true }).fill(data.accounts.rehab_therapist);
  await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('二次验证');

  // 补上验证码才能进。
  await page.getByLabel('二次验证码（如已启用）').fill(totpCode(secret, Date.now() + NEXT_STEP));
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/patients$/);
});

test('停用二次验证需要口令与当前验证码', async ({ page, data }) => {
  await login(page, data, 'rehab_therapist');
  const secret = await enableTotp(page);

  const form = page.getByRole('form', { name: '停用二次验证' });

  // 只有会话不够：口令不对必须被拒。
  await form.getByLabel('登录口令').fill('wrong-password');
  await form.getByLabel('认证器验证码').fill(totpCode(secret, Date.now() + NEXT_STEP));
  await form.getByRole('button', { name: '停用二次验证' }).click();
  await expect(page.getByRole('alert')).toContainText('口令或验证码不正确');
  await expect(page.getByText('已启用', { exact: true })).toBeVisible();

  // 口令 + 验证码都正确才停用。
  const password = process.env.E2E_PASSWORD;
  if (!password) throw new Error('E2E_PASSWORD must be initialized by the test configuration');
  await form.getByLabel('登录口令').fill(password);
  await form.getByLabel('认证器验证码').fill(totpCode(secret, Date.now() + NEXT_STEP));
  await form.getByRole('button', { name: '停用二次验证' }).click();
  await expect(page.getByText('未启用', { exact: true })).toBeVisible();

  // 停用后登录不再需要验证码。
  await page.getByRole('button', { name: '退出登录' }).click();
  await page.getByLabel('账号', { exact: true }).fill(data.accounts.rehab_therapist);
  await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/patients$/);
});
