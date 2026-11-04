import { test, expect, login } from './fixtures';

test('登录成功后进入患者工作台', async ({ page, data }) => {
  await login(page, data);
  await expect(page.getByRole('heading', { name: '患者工作台' })).toBeVisible();
});

test('登录失败显示后端错误且不建立会话', async ({ page, data }) => {
  await login(page, data, 'rehab_therapist', false);
  await expect(page.getByRole('alert')).toContainText('用户名或密码错误');
  await page.goto('/patients');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '医护人员登录' })).toBeVisible();
});

test('无管理端权限账号登录后显示403页', async ({ page, data }) => {
  await login(page, data, 'patient');
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.getByRole('heading', { name: /403/ })).toBeVisible();
});

test('H-Y筛选只保留所选分期且可恢复全部', async ({ page, data }) => {
  await login(page, data);
  const rows = page.locator('tbody tr');
  await expect(rows).toHaveCount(2);
  await page.getByLabel('H-Y 分级：', { exact: true }).selectOption('3');
  await expect(rows).toHaveCount(1);
  await expect(rows).toContainText('测试三期患者');
  await expect(rows).toContainText(/H-Y\s*3/);
  await page.getByLabel('H-Y 分级：', { exact: true }).selectOption('');
  await expect(rows).toHaveCount(2);
});

test('患者详情展示真实分期和当前处方', async ({ page, data }) => {
  await login(page, data);
  await page.goto(`/patients/${data.patientId}`);
  await expect(page.getByRole('heading', { name: '患者详情档案' })).toBeVisible();
  await expect(page.getByText('测试三期患者', { exact: false })).toBeVisible();
  await expect(page.getByText(/H-Y\s*3/)).toBeVisible();
  await expect(page.getByText(/当前有效处方/)).toContainText('1.0.0');
  await expect(page.getByText(/肩颈牵伸|ex-stretch-01/)).toBeVisible();
});

test('康复治疗师可见并进入分期确认', async ({ page, data }) => {
  await login(page, data);
  await page.goto(`/patients/${data.patientId}`);
  await page.getByRole('link', { name: /重新确认分期/ }).click();
  await expect(page).toHaveURL(new RegExp(`/patients/${data.patientId}/stage-confirmation$`));
  await expect(page.getByRole('heading', { name: 'H-Y 临床分期确认' })).toBeVisible();
});

test('照护管理员看不到分期入口且直接访问被拒绝', async ({ page, data }) => {
  await login(page, data, 'care_admin');
  await expect(page.getByRole('link', { name: /分期确认/ })).toHaveCount(0);
  await page.goto(`/patients/${data.patientId}`);
  await expect(page.getByRole('link', { name: /确认分期/ })).toHaveCount(0);
  await page.goto(`/patients/${data.patientId}/stage-confirmation`);
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.getByRole('heading', { name: /403/ })).toBeVisible();
});

test('另一端更新处方后旧版本保存显示冲突', async ({ page, data, request }) => {
  const signedIn = await login(page, data);
  const { accessToken } = await signedIn.json();
  await page.goto(`/patients/${data.patientId}/prescription`);
  await expect(page.getByRole('heading', { name: '康复训练处方开具与调整' })).toBeVisible();
  const updated = await request.patch(`/v1/prescriptions/${data.prescriptionId}`, {
    headers: { Authorization: `Bearer ${accessToken}`, 'If-Match': '1.0.0' },
    data: { note: '另一端先完成更新' },
  });
  expect(updated.status()).toBe(200);
  expect((await updated.json()).version).toBe('1.0.1');
  const [conflict] = await Promise.all([
    page.waitForResponse(response =>
      response.url().endsWith(`/prescriptions/${data.prescriptionId}`) && response.request().method() === 'PATCH',
    { timeout: 10_000 }),
    page.getByRole('button', { name: /签发并同步处方/ }).click(),
  ]);
  expect(conflict.status()).toBe(409);
  await expect(page.getByRole('alert')).toContainText(/版本冲突/);
  await expect(page.getByText(/签发成功/)).toHaveCount(0);
});

test('预警处理结果保存后刷新仍然有效', async ({ page, data, request }) => {
  const signedIn = await login(page, data, 'care_admin');
  const { accessToken } = await signedIn.json();
  await page.getByRole('link', { name: /预警/ }).click();
  const row = page.getByRole('row').filter({ hasText: 'PAIN_PERSISTENT' });
  await expect(row).toHaveCount(1);
  await row.getByRole('button', { name: '登记随访' }).click();
  await expect(row).toContainText('已随访');
  await page.reload();
  await expect(row).toContainText('已随访');
  const persisted = await request.get(`/v1/alerts?patientId=${data.patientId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(persisted.status()).toBe(200);
  const { items } = await persisted.json();
  expect(items).toEqual(expect.arrayContaining([expect.objectContaining({ id: data.alertId, status: 'followed_up' })]));
});

test('未知路由展示404并可返回患者列表', async ({ page, data }) => {
  await login(page, data);
  await page.goto('/this-route-does-not-exist');
  await expect(page.getByRole('heading', { name: /404/ })).toBeVisible();
  await page.getByRole('link', { name: '返回患者工作台' }).click();
  await expect(page).toHaveURL(/\/patients$/);
});
