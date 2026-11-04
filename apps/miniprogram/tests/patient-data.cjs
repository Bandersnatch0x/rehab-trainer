const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { test } = require('node:test');

process.env.TZ = 'Asia/Shanghai';

const project = path.resolve(__dirname, '..');
const build = path.resolve(project, '../../.agent/tmp/miniprogram-check-build');
const compile = spawnSync(process.execPath, [
  require.resolve('typescript/bin/tsc'), '-p', project, '--noEmit', 'false', '--outDir', build,
], { encoding: 'utf8', timeout: 60000 });
assert.equal(compile.status, 0, compile.error?.message || compile.stdout + compile.stderr);

const now = new Date(2026, 10, 5, 12);
const atDay = (offset) => new Date(2026, 10, 5 + offset, 10).toISOString();
const patientId = 'patient/a b';
const patient = { id: patientId, orgId: 'org-1', displayName: '训练者', birthYear: 1960, hyStage: 3, symptoms: ['clinical-only'] };
const me = { accountId: 'account-1', displayName: '记录人', roles: ['patient'], orgId: 'org-1', orgName: '训练中心' };
const session = (id, offset, overrides = {}) => ({
  id, patientId, recordedAt: atDay(offset), checkedIn: true, completionCount: 8,
  movementErrorCount: 0, interruptionCount: 1, validFrameRatio: 0.875, isDoubtful: false,
  modelVersion: 'm1', metricAlgorithmVersion: 'a1', thresholdConfigVersion: 't1', ...overrides,
});
const alert = (id, overrides = {}) => ({
  id, patientId, ruleCode: 'clinical-only', level: 'warning', triggeredAt: atDay(0), status: 'open', ...overrides,
});

function harness(options = {}) {
  for (const key of Object.keys(require.cache)) {
    if (key.startsWith(build + path.sep)) delete require.cache[key];
  }
  const storage = new Map([
    ['rehab.auth.session.v1', { accessToken: 'access-test', refreshToken: 'refresh-test', expiresAt: Date.now() + 60000, boundAt: Date.now() }],
    ['rehab.auth.actingFor.v1', 'untrusted-other-patient'],
  ]);
  if (options.loggedOut) storage.clear();
  const calls = [];
  global.wx = {
    getStorageSync: (key) => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: (key) => storage.delete(key),
    request(request) {
      const url = new URL(request.url);
      calls.push({ ...request, url });
      let response = options.respond?.(request, url, calls);
      if (!response) {
        if (url.pathname === '/v1/me') response = { data: options.me || me };
        else if (url.pathname === '/v1/patients') response = { data: { items: options.patients || [patient] } };
        else if (url.pathname === '/v1/sessions' || url.pathname === '/v1/alerts') {
          const pages = url.pathname.endsWith('sessions') ? options.sessions || [[]] : options.alerts || [[]];
          const index = Number(url.searchParams.get('cursor') || '0');
          assert.ok(pages[index], '请求了不存在的分页');
          response = { data: { items: pages[index], nextCursor: index + 1 < pages.length ? String(index + 1) : null } };
        } else if (url.pathname.endsWith('/assessments')) {
          response = { status: 201, data: { id: 'assessment-1', patientId, instrument: request.data.instrument, assessedAt: request.data.assessedAt, assessedBy: me.accountId } };
        } else {
          throw new Error('Unexpected endpoint: ' + url.pathname);
        }
      }
      queueMicrotask(() => {
        if (response.fail) request.fail(response.fail);
        else request.success({ statusCode: response.status || 200, data: response.data });
      });
    },
  };
  const api = require(path.join(build, 'utils/patient-data.js'));
  return { api, calls, storage };
}

function page(name) {
  let instance;
  global.Page = (definition) => {
    instance = { ...definition, data: structuredClone(definition.data) };
    instance.setData = (patch) => Object.assign(instance.data, patch);
  };
  const filename = path.join(build, 'pages', name, 'index.js');
  delete require.cache[filename];
  require(filename);
  return instance;
}

test('overview uses scoped patient ID, real bearer requests, every page, local dates and safe profile fields', async () => {
  const { api, calls } = harness({ sessions: [
    [session('today-1', 0), session('today-2', 0, { isDoubtful: true })],
    [session('yesterday', -1), session('not-checked-in', -2, { checkedIn: false })],
  ] });
  const result = await api.loadOverview(now);
  assert.equal(result.context.patient.id, patientId);
  assert.equal(result.trainingCount, 3);
  assert.equal(result.todayCount, 2);
  assert.equal(result.streakDays, 2);
  assert.equal(result.records.length, 3);
  assert.doesNotMatch(JSON.stringify(result), /hyStage|symptoms|clinical-only/);
  const requests = calls.filter((call) => call.url.pathname === '/v1/sessions');
  assert.equal(requests.length, 2);
  assert.equal(requests[1].url.searchParams.get('cursor'), '1');
  assert.equal(requests[0].url.searchParams.get('patientId'), patientId);
  assert.equal(requests[0].url.searchParams.get('from'), new Date(2026, 9, 7).toISOString());
  assert.equal(requests[0].url.searchParams.get('to'), now.toISOString());
  assert.ok(calls.every((call) => call.header.Authorization === 'Bearer access-test'));
});

test('streak counts distinct days through yesterday and stops at a gap', async () => {
  const { api } = harness({ sessions: [[session('y1', -1), session('y2', -1), session('d2', -2), session('d4', -4)]] });
  const result = await api.loadOverview(now);
  assert.equal(result.todayCount, 0);
  assert.equal(result.trainingCount, 4);
  assert.equal(result.streakDays, 2);
});

test('records preserve zero, format frame ratios and distinguish absent fields from success', async () => {
  const { api } = harness({ sessions: [[{}, session('zero', 0, { completionCount: 0, validFrameRatio: 0, isDoubtful: true })]] });
  const { records } = await api.loadRecords(now);
  assert.equal(records[0].completionCount, 0);
  assert.equal(records[0].validFrameRatio, '0%');
  assert.equal(records[0].flag, '存在疑点');
  assert.equal(records[1].completionCount, '未提供');
  assert.equal(records[1].status, '打卡状态未提供');
  assert.equal(records[1].flag, '存疑状态未提供');
  assert.equal(records[1].validFrameRatio, '未提供');
});

test('UTC timestamps without offsets from the backend still group into the correct local day', async () => {
  const { api } = harness({ sessions: [[session('after-midnight', 0, { recordedAt: '2026-11-04T16:30:00' })]] });
  const result = await api.loadOverview(now);
  assert.equal(result.todayCount, 1);
  assert.equal(result.streakDays, 1);
  assert.equal(result.records[0].recordedAt, '2026-11-05 00:30');
});

test('alerts are patient scoped, paginated and expose delivery outcomes without clinical rule names', async () => {
  const { api, calls } = harness({ alerts: [[alert('a1')], [alert('a2', {
    level: 'critical', status: 'followed_up', deliveries: [{ channel: 'voice_prompt', status: 'failed', deliveredAt: atDay(0) }],
  })]] });
  const result = await api.loadAlerts();
  assert.equal(result.alerts.length, 2);
  assert.equal(result.alerts[1].levelLabel, '严重');
  assert.equal(result.alerts[1].status, '已随访');
  assert.equal(result.alerts[1].deliveries, '语音提示：发送失败');
  assert.doesNotMatch(JSON.stringify(result), /clinical-only|ruleCode/);
  assert.ok(calls.filter((call) => call.url.pathname === '/v1/alerts').every((call) => call.url.searchParams.get('patientId') === patientId));
});

test('unbound accounts return empty states without downstream requests', async () => {
  const { api, calls } = harness({ patients: [] });
  assert.equal((await api.loadOverview(now)).context.patient, null);
  assert.deepEqual((await api.loadRecords(now)).records, []);
  assert.deepEqual((await api.loadAlerts()).alerts, []);
  assert.ok(calls.every((call) => ['/v1/me', '/v1/patients'].includes(call.url.pathname)));
});

test('ambiguous or clinical accounts cannot silently select a patient', async () => {
  let run = harness({ patients: [patient, { ...patient, id: 'another' }] });
  await assert.rejects(run.api.loadPatientContext(), { code: 'PATIENT_SELECTION_REQUIRED' });
  run = harness({ me: { ...me, roles: ['patient', 'rehab_therapist'] } });
  await assert.rejects(run.api.loadPatientContext(), { code: 'PATIENT_ACCOUNT_REQUIRED' });
  assert.equal(run.calls.length, 1);
  run = harness({ me: { ...me, roles: ['caregiver'] } });
  assert.equal((await run.api.loadPatientContext()).identity, '照护者代记录');
});

test('backend message and network failure survive the full request layer', async () => {
  for (const method of ['loadOverview', 'loadRecords', 'loadAlerts', 'loadPatientContext']) {
    const { api } = harness({ respond: () => ({ status: 403, data: { code: 'FORBIDDEN', message: '服务端拒绝此访问' } }) });
    await assert.rejects(api[method](now), { message: '服务端拒绝此访问', status: 403 });
  }
  const { api } = harness({ respond: () => ({ fail: { errMsg: 'request:fail timeout' } }) });
  await assert.rejects(api.loadOverview(now), { message: 'request:fail timeout', code: 'NETWORK_ERROR' });
});

test('401 refresh replays with the replacement bearer token', async () => {
  let expired = true;
  const { api, calls, storage } = harness({ respond: (request, url) => {
    if (url.pathname === '/v1/me' && expired) {
      expired = false;
      return { status: 401, data: { code: 'UNAUTHORIZED', message: 'expired' } };
    }
    if (url.pathname === '/v1/auth/refresh') {
      assert.equal(request.data.refreshToken, 'refresh-test');
      return { data: { accessToken: 'renewed-test', refreshToken: 'renewed-refresh-test', expiresIn: 3600 } };
    }
    return null;
  } });
  await api.loadOverview(now);
  assert.equal(calls.filter((call) => call.url.pathname === '/v1/auth/refresh').length, 1);
  assert.equal(calls[2].header.Authorization, 'Bearer renewed-test');
  assert.equal(storage.get('rehab.auth.session.v1').accessToken, 'renewed-test');
});

test('malformed pages, bad metrics, repeated cursors and foreign records fail visibly', async () => {
  for (const data of [null, { items: {} }, { items: [], nextCursor: 10 }, { items: [session('bad', 0, { validFrameRatio: 3 })] }, { items: [session('other', 0, { patientId: 'other' })] }]) {
    const { api } = harness({ respond: (_, url) => url.pathname === '/v1/sessions' ? { data } : null });
    await assert.rejects(api.loadRecords(now), { code: 'INVALID_RESPONSE' });
  }
  const { api } = harness({ respond: (_, url) => url.pathname === '/v1/sessions' ? { data: { items: [], nextCursor: 'repeat' } } : null });
  await assert.rejects(api.loadRecords(now), { code: 'INVALID_RESPONSE' });
  const foreign = harness({ alerts: [[alert('other', { patientId: 'other' })]] });
  await assert.rejects(foreign.api.loadAlerts(), { code: 'INVALID_RESPONSE' });
});

test('assessment posts free text to the encoded patient path, without invented score or actor', async () => {
  const { api, calls } = harness();
  assert.ok(await api.submitAssessment(patientId, '  今天散步很轻松  '));
  const call = calls[0];
  assert.equal(call.url.pathname, '/v1/patients/patient%2Fa%20b/assessments');
  assert.equal(call.method, 'POST');
  assert.equal(call.header.Authorization, 'Bearer access-test');
  assert.equal(call.data.instrument, 'DAILY_FEELING');
  assert.deepEqual(call.data.answers, { feeling: '今天散步很轻松' });
  assert.ok(Number.isFinite(Date.parse(call.data.assessedAt)));
  assert.ok(!('score' in call.data) && !('assessedBy' in call.data));
  for (const value of ['   ', '字'.repeat(1001)]) await assert.rejects(api.submitAssessment(patientId, value), { code: 'VALIDATION_FAILED' });
  assert.equal(calls.length, 1);
});

test('all five page lifecycles show loading, ready/empty state, backend error and successful retry', async () => {
  for (const name of ['index', 'patient', 'records', 'alerts', 'assessment']) {
    let fail = true;
    harness({ respond: (_, url) => fail && url.pathname === '/v1/me' ? { status: 500, data: { code: 'SERVER_ERROR', message: '页面加载失败的原始消息' } } : null });
    const instance = page(name);
    const pending = instance.onShow();
    assert.equal(instance.data.loading, true);
    await pending;
    assert.equal(instance.data.error, '页面加载失败的原始消息');
    assert.equal(instance.data.loading, false);
    fail = false;
    await instance.loadData();
    assert.equal(instance.data.error, '');
    const data = instance.data.overview || instance.data.result || instance.data.context;
    assert.equal((data.context || data).patient.id, patientId);
    const template = readFileSync(path.join(project, 'pages', name, 'index.wxml'), 'utf8');
    assert.match(template, /bindtap="loadData"/);
    assert.match(template, /\{\{error\}\}/);
    assert.match(template, /尚未关联患者/);
  }
});

test('assessment page preserves failed input, exposes backend message and prevents double submission', async () => {
  let fail = true;
  const { calls } = harness({ respond: (_, url) => fail && url.pathname.endsWith('/assessments') ? { status: 422, data: { code: 'VALIDATION_FAILED', message: '后端要求补充内容' } } : null });
  const instance = page('assessment');
  await instance.onShow();
  instance.onFeelingInput({ detail: { value: '今天很轻松' } });
  await instance.submit();
  assert.equal(instance.data.feeling, '今天很轻松');
  assert.equal(instance.data.submitError, '后端要求补充内容');
  assert.equal(instance.data.submittedAt, '');
  fail = false;
  const pending = instance.submit();
  assert.equal(instance.data.submitting, true);
  await instance.submit();
  await pending;
  assert.equal(instance.data.submitting, false);
  assert.equal(instance.data.feeling, '');
  assert.ok(instance.data.submittedAt);
  assert.equal(calls.filter((call) => call.method === 'POST').length, 2);
});

test('logged out pages show the binding error without issuing unauthenticated requests', async () => {
  const { calls } = harness({ loggedOut: true });
  const instance = page('index');
  await instance.onShow();
  assert.match(instance.data.error, /尚未登录/);
  assert.equal(calls.length, 0);
});

test('patient templates omit clinical fields and both training surfaces explicitly remain demos', () => {
  const profile = readFileSync(path.join(project, 'pages/patient/index.wxml'), 'utf8');
  assert.doesNotMatch(profile, /H-Y|hyStage|symptoms|症状|分级|96%|1954/);
  for (const name of ['training', 'training/session']) {
    const template = readFileSync(path.join(project, 'pages', name, 'index.wxml'), 'utf8');
    assert.match(template, /演示数据/);
    assert.match(template, /没有实时动作反馈/);
    assert.doesNotMatch(template, /H-Y|hyStage|适用分级|5 级/);
  }
});
