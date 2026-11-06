import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test, expect, login } from './fixtures';
import { PYTHON } from './python';

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

test('患者详情展示每日身体感受记录', async ({ page, data }) => {
  await login(page, data);
  await page.goto(`/patients/${data.patientId}`);
  await expect(page.getByText('身体感受与评估记录')).toBeVisible();
  await expect(page.getByText('DAILY_FEELING')).toBeVisible();
  await expect(page.getByText('今天散步很轻松，练习后休息了一会儿。')).toBeVisible();
});

test('照护管理员可新建患者且刷新后仍在列表', async ({ page, data }) => {
  await login(page, data, 'care_admin');
  const name = `新建患者-${Date.now()}`;
  await page.getByRole('button', { name: '新建患者' }).click();
  await page.getByLabel('患者姓名').fill(name);
  await page.getByLabel('出生年份（可选）').fill('1952');
  await page.getByRole('button', { name: '创建档案' }).click();

  const row = page.getByRole('row').filter({ hasText: name });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('疑似待确认');

  await page.reload();
  await expect(page.getByRole('row').filter({ hasText: name })).toHaveCount(1);
});

test('审计日志显示本机构操作并可导出 CSV', async ({ page, data }) => {
  const signedIn = await login(page, data, 'care_admin');
  const { profile } = await signedIn.json();

  const name = `审计患者-${Date.now()}`;
  await page.getByRole('button', { name: '新建患者' }).click();
  await page.getByLabel('患者姓名').fill(name);
  await page.getByRole('button', { name: '创建档案' }).click();
  await expect(page.getByRole('row').filter({ hasText: name })).toHaveCount(1);

  await page.getByRole('link', { name: '审计日志' }).click();
  const createdRow = page.getByRole('row').filter({ hasText: 'patient.created' });
  await expect(createdRow.first()).toContainText(profile.accountId);

  // 动作筛选在服务端执行：点击「查询」后不存在的动作给出空态，恢复后重新出现。
  await page.getByLabel('动作').fill('no.such.action');
  await page.getByRole('button', { name: '查询' }).click();
  await expect(page.getByText('当前筛选条件下没有审计事件。')).toBeVisible();
  await page.getByLabel('动作').fill('patient.created');
  await page.getByRole('button', { name: '查询' }).click();
  await expect(createdRow.first()).toBeVisible();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出 CSV' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/^audit-log-.*\.csv$/);
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

test('预警可先确认再关闭，状态保存后刷新仍然有效', async ({ page, data, request }) => {
  const signedIn = await login(page, data, 'care_admin');
  const { accessToken } = await signedIn.json();
  await page.getByRole('link', { name: /预警/ }).click();
  const row = page.getByRole('row').filter({ hasText: 'PAIN_PERSISTENT' });
  await expect(row).toHaveCount(1);

  await row.getByRole('button', { name: '确认', exact: true }).click();
  await expect(row).toContainText('已确认');
  // 确认后仍可登记随访或关闭，但不再显示「确认」。
  await expect(row.getByRole('button', { name: '确认', exact: true })).toHaveCount(0);
  await expect(row.getByRole('button', { name: '登记随访' })).toHaveCount(1);

  await page.reload();
  await expect(row).toContainText('已确认');

  await row.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(row).toContainText('已关闭');
  await page.reload();
  await expect(row).toContainText('已关闭');
  await expect(row.getByRole('button', { name: '关闭', exact: true })).toHaveCount(0);

  const persisted = await request.get(`/v1/alerts?patientId=${data.patientId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(persisted.status()).toBe(200);
  const { items } = await persisted.json();
  expect(items).toEqual(expect.arrayContaining([expect.objectContaining({ id: data.alertId, status: 'closed' })]));
});

test('调整处方不会删除同一处方里的其他训练条目', async ({ page, data, request }) => {
  const signedIn = await login(page, data);
  const { accessToken } = await signedIn.json();
  await page.goto(`/patients/${data.patientId}/prescription`);
  await expect(page.getByText(/当前处方版本 1\.0\.0/)).toBeVisible();

  const saved = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/prescriptions/${data.prescriptionId}`) &&
      response.request().method() === 'PATCH',
  );
  await page.getByRole('button', { name: /签发并同步处方/ }).click();
  expect((await saved).status()).toBe(200);

  const listed = await request.get(`/v1/prescriptions?patientId=${data.patientId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const { items } = await listed.json();
  const codes = (items[0].items as { exerciseCode: string }[])
    .map((item) => item.exerciseCode)
    .sort();
  expect(codes).toEqual(['ex-strength-01', 'ex-stretch-01']);
});

test('可调整处方中第二个训练条目的剂量', async ({ page, data, request }) => {
  const signedIn = await login(page, data);
  const { accessToken } = await signedIn.json();
  await page.goto(`/patients/${data.patientId}/prescription`);

  const second = page.getByRole('group', { name: '训练条目 2' });
  await expect(second.locator('select')).toHaveValue('ex-strength-01');
  await second.getByLabel('组数 (Sets)：').fill('5');

  const saved = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/prescriptions/${data.prescriptionId}`) &&
      response.request().method() === 'PATCH',
  );
  await page.getByRole('button', { name: /签发并同步处方/ }).click();
  expect((await saved).status()).toBe(200);

  const listed = await request.get(`/v1/prescriptions?patientId=${data.patientId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const { items } = await listed.json();
  const rows = items[0].items as { exerciseCode: string; dosage?: { sets?: number } }[];
  expect(rows.find((row) => row.exerciseCode === 'ex-strength-01')?.dosage?.sets).toBe(5);
  // 未编辑的第一条原剂量为空，不应被页面默认值写脏。
  expect(rows.find((row) => row.exerciseCode === 'ex-stretch-01')?.dosage ?? null).toBeNull();
});

test('处方可添加与移除训练条目', async ({ page, data }) => {
  await login(page, data);
  await page.goto(`/patients/${data.patientId}/prescription`);

  await expect(page.getByRole('group', { name: '训练条目 2' })).toBeVisible();
  await page.getByRole('button', { name: /添加训练条目/ }).click();

  const third = page.getByRole('group', { name: '训练条目 3' });
  await expect(third).toBeVisible();
  // 新条目自动选用尚未占用的编码。
  await expect(third.locator('select')).toHaveValue('ex-gait-01');

  await third.getByRole('button', { name: '移除' }).click();
  await expect(third).toHaveCount(0);
  await expect(page.getByRole('group', { name: '训练条目 2' })).toBeVisible();

  // 只剩一条时不能再移除，避免提交空处方。
  await page
    .getByRole('group', { name: '训练条目 2' })
    .getByRole('button', { name: '移除' })
    .click();
  await expect(page.getByRole('group', { name: '训练条目 2' })).toHaveCount(0);
  await expect(
    page.getByRole('group', { name: '训练条目 1' }).getByRole('button', { name: '移除' }),
  ).toBeDisabled();
});

test('随访台账只显示已处置的预警', async ({ page, data, request }) => {
  const signedIn = await login(page, data, 'care_admin');
  const { accessToken, profile } = await signedIn.json();

  await page.goto('/follow-ups');
  await expect(page.getByText('暂无已处置的预警记录。')).toBeVisible();

  const resolved = await request.patch(`/v1/alerts/${data.alertId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { status: 'followed_up', note: '电话随访：患者已缓解' },
  });
  expect(resolved.status()).toBe(200);

  await page.reload();
  const row = page.getByRole('row').filter({ hasText: 'PAIN_PERSISTENT' });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('已随访');
  // 台账须显示处置人与处置说明，而不是只有状态。
  await expect(row).toContainText(profile.accountId);
  await expect(row).toContainText('电话随访：患者已缓解');
});

test('患者超过单页上限时列表仍完整显示', async ({ page }) => {
  const output = execFileSync(
    PYTHON,
    [fileURLToPath(new URL('./backend.py', import.meta.url)), 'seed-many'],
    { encoding: 'utf8', timeout: 15_000 },
  );
  const many = JSON.parse(output);
  const requests: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'GET' && request.url().includes('/v1/patients')) {
      requests.push(request.url());
    }
  });

  await login(page, many);
  await expect(page.getByRole('heading', { name: '患者工作台' })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(105);
  await expect(page.getByText(/共 105 位患者/)).toBeVisible();
  // 105 位 / 每页 100 → 必须发生翻页，否则说明循环被删掉也能通过。
  expect(requests.filter((url) => url.includes('cursor=')).length).toBeGreaterThan(0);
});

test('处方读取失败时禁止签发且不创建新处方', async ({ page, data }) => {
  const signedIn = await login(page, data);
  const { accessToken } = await signedIn.json();
  expect(accessToken).toBeTruthy();

  let created = 0;
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/v1/prescriptions')) {
      created += 1;
    }
  });
  await page.route('**/v1/prescriptions**', (route) => {
    if (route.request().method() !== 'GET') {
      return route.continue();
    }
    return route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'INTERNAL_ERROR', message: '模拟处方读取故障' }),
    });
  });

  await page.goto(`/patients/${data.patientId}/prescription`);
  await expect(page.getByRole('alert')).toContainText('现有处方读取失败');
  await expect(page.getByRole('button', { name: /签发并同步处方/ })).toBeDisabled();
  expect(created).toBe(0);
});

test('未知路由展示404并可返回患者列表', async ({ page, data }) => {
  await login(page, data);
  await page.goto('/this-route-does-not-exist');
  await expect(page.getByRole('heading', { name: /404/ })).toBeVisible();
  await page.getByRole('link', { name: '返回患者工作台' }).click();
  await expect(page).toHaveURL(/\/patients$/);
});

test('科研页展示聚合画像且不显示被抑制的小样本', async ({ page, data, request }) => {
  const signedIn = await login(page, data);
  const { accessToken } = await signedIn.json();
  await page.getByRole('link', { name: '科研数据' }).click();
  await expect(page.getByRole('heading', { name: '科研数据底座' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '机构聚合画像' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '训练数据质量' })).toBeVisible();
  // 该例 2 位真实患者、5 条记录：患者数被抑制，记录数刚好越过阈值可见。
  await expect(page.locator('.research-card').filter({ hasText: '真实患者数' })).toContainText('—');
  await expect(page.locator('.research-card').filter({ hasText: '训练记录数' })).toContainText('5');

  const overview = await request.get('/v1/research/overview', {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(overview.status()).toBe(200);
  const body = await overview.json();
  expect(body.dataOrigin).toBe('real');
  expect(body.minCellCount).toBe(5);
  expect(JSON.stringify(body)).not.toMatch(/strideLengthCm|gaitSpeedMps|swingPhasePercent|rasApplicab/);
});

/** 最小 MP4 头：上传接口只按魔数（ftyp + 品牌）判定类型。 */
function mp4Buffer(): Buffer {
  return Buffer.concat([
    Buffer.from([0x00, 0x00, 0x00, 0x18]),
    Buffer.from('ftypisom', 'latin1'),
    Buffer.from([0x00, 0x00, 0x02, 0x00]),
    Buffer.alloc(16, 0),
  ]);
}

test('上传视频为草稿，上架后才下发', async ({ page, data }) => {
  await login(page, data);
  await page.getByRole('link', { name: '训练视频' }).click();
  await expect(page.getByRole('heading', { name: '训练视频管理' })).toBeVisible();
  await expect(page.getByText('还没有符合条件的视频资产。')).toBeVisible();

  const form = page.locator('form.media-upload');
  await form.getByLabel('视频文件（仅 MP4）').setInputFiles({
    name: 'demo.mp4',
    mimeType: 'video/mp4',
    buffer: mp4Buffer(),
  });
  await form.getByLabel('关联训练条目').selectOption('ex-stretch-01');
  await form.getByLabel('水印文字').fill('演示水印');
  await form.getByLabel('来源署名').fill('来源：测试机构');
  await form.getByRole('button', { name: '上传为草稿' }).click();
  await expect(page.getByText(/已上传草稿资产/)).toBeVisible();

  const card = page.locator('.media-card').first();
  await expect(card.locator('.media-badge')).toHaveText('草稿');
  await expect(card).toContainText('演示水印');
  await expect(card).toContainText('来源：测试机构');

  page.on('dialog', (dialog) => void dialog.accept());
  await card.getByRole('button', { name: '上架' }).click();
  await expect(card.locator('.media-badge')).toHaveText('已上架');
  await expect(card.getByText('上架时间').locator('..')).not.toContainText('—');
});

test('未设置适用分期的视频不能上架', async ({ page, data }) => {
  await login(page, data);
  await page.goto('/media-assets');
  const form = page.locator('form.media-upload');
  await form.getByLabel('视频文件（仅 MP4）').setInputFiles({
    name: 'no-stage.mp4',
    mimeType: 'video/mp4',
    buffer: mp4Buffer(),
  });
  for (const stage of ['H-Y 1 期', 'H-Y 2 期', 'H-Y 3 期', 'H-Y 4 期']) {
    await form.getByLabel(stage, { exact: true }).uncheck();
  }
  await expect(form.getByText(/未选择分期时上架会被拒绝/)).toBeVisible();
  await form.getByRole('button', { name: '上传为草稿' }).click();
  await expect(page.getByText(/已上传草稿资产/)).toBeVisible();

  page.on('dialog', (dialog) => void dialog.accept());
  const card = page.locator('.media-card').first();
  await card.getByRole('button', { name: '上架' }).click();
  await expect(page.getByText('未设置适用分期，上架后不会下发任何患者')).toBeVisible();
  await expect(card.locator('.media-badge')).toHaveText('草稿');
});
