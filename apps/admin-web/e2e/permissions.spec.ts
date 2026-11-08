import { test, expect, login } from './fixtures';

test('权限管理列出本机构真实账号并可开通新账号', async ({ page, data }) => {
  await login(page, data, 'care_admin');
  await page.goto('/admin/permissions');
  await expect(page.getByRole('heading', { name: '机构权限与角色管理' })).toBeVisible();
  // 页面读的是后端真实账号，不再是演示数据。
  await expect(page.getByText('演示数据')).toHaveCount(0);
  const colleagueRow = page.getByRole('row').filter({ hasText: data.colleague.username });
  await expect(colleagueRow).toHaveCount(1);
  await expect(colleagueRow).toContainText(data.colleague.displayName);
  await expect(colleagueRow).toContainText('康复治疗师');

  const username = `new-nurse-${Date.now()}`;
  await page.getByRole('button', { name: /分配机构账号权限/ }).click();
  const form = page.getByRole('form', { name: '开通机构账号' });
  await form.getByLabel('姓名').fill('新同事');
  await form.getByLabel('登录名').fill(username);
  await form.getByLabel('初始密码').fill('wader-quint-92');
  await form.getByLabel('管理端角色').selectOption('rehab_therapist');
  await form.getByRole('button', { name: '开通账号' }).click();

  const newRow = page.getByRole('row').filter({ hasText: username });
  await expect(newRow).toHaveCount(1);
  await expect(newRow).toContainText('康复治疗师');
  await page.reload();
  await expect(page.getByRole('row').filter({ hasText: username })).toHaveCount(1);
});

test('权限管理可改名与停用账号，停用后不能登录', async ({ page, data, request }) => {
  await login(page, data, 'care_admin');
  await page.goto('/admin/permissions');
  const row = page.getByRole('row').filter({ hasText: data.colleague.username });
  await expect(row).toHaveCount(1);
  await row.getByRole('button', { name: '权限配置' }).click();

  const form = page.getByRole('form', { name: '账号权限配置' });
  await form.getByLabel('姓名').fill('改名后的同事');
  await form.getByLabel('账号状态').selectOption('disabled');
  await form.getByRole('button', { name: '保存' }).click();

  const updated = page.getByRole('row').filter({ hasText: data.colleague.username });
  await expect(updated).toContainText('改名后的同事');
  await expect(updated).toContainText('已停用');
  await page.reload();
  await expect(page.getByRole('row').filter({ hasText: data.colleague.username })).toContainText('已停用');

  const password = process.env.E2E_PASSWORD;
  if (!password) throw new Error('E2E_PASSWORD must be initialized by the test configuration');
  const denied = await request.post('/v1/auth/login', {
    data: { username: data.colleague.username, password },
  });
  expect(denied.status()).toBe(401);
});

test('不能停用当前登录账号', async ({ page, data, request }) => {
  const signedIn = await login(page, data, 'care_admin');
  const { accessToken } = await signedIn.json();
  // 本机构只有一位 care_admin 时，停用自己会先命中 last_care_admin 守卫；
  // 补一位同机构 care_admin 后才能命中 self_disable（与后端 test_accounts.py 的用例同构）。
  const backup = await request.post('/v1/accounts', {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      displayName: '备用管理员',
      username: `backup-admin-${Date.now()}`,
      password: 'wader-quint-92',
      role: 'care_admin',
    },
  });
  expect(backup.status()).toBe(201);

  await page.goto('/admin/permissions');
  const ownRow = page.getByRole('row').filter({ hasText: data.accounts.care_admin });
  await expect(ownRow).toHaveCount(1);
  await ownRow.getByRole('button', { name: '权限配置' }).click();

  const form = page.getByRole('form', { name: '账号权限配置' });
  await form.getByLabel('账号状态').selectOption('disabled');
  await form.getByRole('button', { name: '保存' }).click();

  // 后端 409 必须原样透出，而不是静默失败。
  await expect(page.getByRole('alert')).toContainText('不能禁用当前登录账号');
});

test('治疗师只能授予治疗师，且改不了管理员账号', async ({ page, data }) => {
  await login(page, data, 'rehab_therapist');
  await page.goto('/admin/permissions');
  await expect(page.getByRole('heading', { name: '机构权限与角色管理' })).toBeVisible();

  // 角色下拉里不能出现「照护管理员」——治疗师自造管理员等于变相自升权。
  await page.getByRole('button', { name: /分配机构账号权限/ }).click();
  const form = page.getByRole('form', { name: '开通机构账号' });
  await expect(form.getByLabel('管理端角色').locator('option')).toHaveText(['康复治疗师']);

  // 管理员那一行不给入口；服务端另有 403 兜底。
  const adminRow = page.getByRole('row').filter({ hasText: data.accounts.care_admin });
  await expect(adminRow.getByRole('button', { name: '权限配置' })).toBeDisabled();
});
