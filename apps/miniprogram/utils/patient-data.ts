import type { components } from '../types/api';
import { ApiError, request } from './request';

type Me = components['schemas']['Me'];
type SessionRecord = components['schemas']['SessionRecord'];
type Alert = components['schemas']['Alert'];
type Assessment = components['schemas']['Assessment'];

export interface PatientContext {
  accountName: string;
  orgName: string;
  identity: string;
  patient: { id: string; displayName: string; birthYear: string } | null;
}

const RECENT_DAYS = 30;

function invalidResponse(): never {
  throw new ApiError('INVALID_RESPONSE', '服务返回的数据格式不正确，请重试', 0);
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readPage(value: unknown): { items: unknown[]; nextCursor: string | null } {
  if (!isObject(value) || !Array.isArray(value.items)) return invalidResponse();
  if (value.nextCursor != null && typeof value.nextCursor !== 'string') return invalidResponse();
  return { items: value.items, nextCursor: value.nextCursor || null };
}

async function readAll<T>(path: string, parse: (value: unknown) => T): Promise<T[]> {
  const items: T[] = [];
  const cursors = new Set<string>();
  let cursor: string | null = null;
  do {
    const suffix: string = cursor ? '&cursor=' + encodeURIComponent(cursor) : '';
    const page = readPage(await request<unknown>({ path: path + suffix }));
    items.push(...page.items.map(parse));
    cursor = page.nextCursor;
    if (cursor && cursors.has(cursor)) return invalidResponse();
    if (cursor) cursors.add(cursor);
  } while (cursor);
  return items;
}

export async function loadPatientContext(): Promise<PatientContext> {
  const me = await request<Me>({ path: '/me' });
  if (!me || typeof me.displayName !== 'string' || !Array.isArray(me.roles) ||
      (me.orgName != null && typeof me.orgName !== 'string')) return invalidResponse();
  if ((!me.roles.includes('patient') && !me.roles.includes('caregiver')) ||
      me.roles.includes('care_admin') || me.roles.includes('rehab_therapist')) {
    throw new ApiError('PATIENT_ACCOUNT_REQUIRED', '请使用已绑定的患者或照护者账号查看', 403);
  }

  // /me 没有患者 ID；仅使用后端已按关联关系隔离的患者列表。
  const page = readPage(await request<unknown>({ path: '/patients?limit=2' }));
  if (page.items.length > 1 || page.nextCursor) {
    throw new ApiError('PATIENT_SELECTION_REQUIRED', '账号关联了多位患者，请联系管理员确认当前绑定', 409);
  }
  const context: PatientContext = {
    accountName: me.displayName,
    orgName: me.orgName || '暂未提供',
    identity: me.roles.includes('patient') ? '本人操作' : '照护者代记录',
    patient: null,
  };
  if (!page.items.length) return context;
  const patient = page.items[0];
  if (!isObject(patient) || typeof patient.id !== 'string' || !patient.id ||
      typeof patient.displayName !== 'string' ||
      (patient.birthYear != null && !Number.isInteger(patient.birthYear))) return invalidResponse();
  // 患者端只接收安全展示字段，分级与症状不进入页面数据。
  context.patient = {
    id: patient.id,
    displayName: patient.displayName,
    birthYear: patient.birthYear == null ? '暂未提供' : patient.birthYear + ' 年',
  };
  return context;
}

function timestamp(value: string): number {
  // 服务端将 UTC 序列化为无时区时间；有显式偏移的响应保留原偏移。
  return Date.parse(/(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : value + 'Z');
}

function isDateTime(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(value) && Number.isFinite(timestamp(value));
}

function parseSession(value: unknown): SessionRecord {
  if (!isObject(value)) return invalidResponse();
  for (const key of ['id', 'clientRecordId', 'patientId', 'modelVersion', 'metricAlgorithmVersion', 'thresholdConfigVersion']) {
    if (value[key] != null && typeof value[key] !== 'string') return invalidResponse();
  }
  for (const key of ['completionCount', 'movementErrorCount', 'interruptionCount']) {
    const count = value[key];
    if (count != null && (typeof count !== 'number' || !Number.isInteger(count) || count < 0)) return invalidResponse();
  }
  for (const key of ['checkedIn', 'isDoubtful']) {
    if (value[key] != null && typeof value[key] !== 'boolean') return invalidResponse();
  }
  const ratio = value.validFrameRatio;
  if (ratio != null && (typeof ratio !== 'number' || !Number.isFinite(ratio) || ratio < 0 || ratio > 1)) return invalidResponse();
  if (value.recordedAt != null && !isDateTime(value.recordedAt)) return invalidResponse();
  return value as SessionRecord;
}

function dayKey(date: Date): string {
  return [date.getFullYear(), date.getMonth() + 1, date.getDate()].join('-');
}

function formatTime(value: string | undefined): string {
  if (!value) return '时间暂未提供';
  const date = new Date(timestamp(value));
  const pad = (part: number): string => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

async function recentSessions(patientId: string, now: Date): Promise<SessionRecord[]> {
  const from = new Date(now.getFullYear(), now.getMonth(), now.getDate() - RECENT_DAYS + 1);
  const path = '/sessions?patientId=' + encodeURIComponent(patientId) + '&limit=100' +
    '&from=' + encodeURIComponent(from.toISOString()) + '&to=' + encodeURIComponent(now.toISOString());
  const sessions = await readAll(path, parseSession);
  if (sessions.some((record) => record.patientId != null && record.patientId !== patientId)) return invalidResponse();
  return sessions.sort((a, b) => (b.recordedAt ? timestamp(b.recordedAt) : 0) - (a.recordedAt ? timestamp(a.recordedAt) : 0));
}

function sessionView(record: SessionRecord, index: number) {
  return {
    id: record.id || record.clientRecordId || String(index),
    recordedAt: formatTime(record.recordedAt),
    status: record.checkedIn === true ? '已打卡' : record.checkedIn === false ? '未打卡' : '打卡状态未提供',
    completionCount: record.completionCount ?? '未提供',
    movementErrorCount: record.movementErrorCount ?? '未提供',
    interruptionCount: record.interruptionCount ?? '未提供',
    validFrameRatio: record.validFrameRatio == null ? '未提供' : Math.round(record.validFrameRatio * 100) + '%',
    flag: record.isDoubtful === true ? '存在疑点' : record.isDoubtful === false ? '无存疑' : '存疑状态未提供',
    doubtful: record.isDoubtful === true,
    modelVersion: record.modelVersion || '未提供',
    metricAlgorithmVersion: record.metricAlgorithmVersion || '未提供',
    thresholdConfigVersion: record.thresholdConfigVersion || '未提供',
  };
}

export async function loadRecords(now = new Date()) {
  const context = await loadPatientContext();
  const sessions = context.patient ? await recentSessions(context.patient.id, now) : [];
  return { context, records: sessions.map(sessionView) };
}

export type RecordsData = Awaited<ReturnType<typeof loadRecords>>;

export async function loadOverview(now = new Date()) {
  const context = await loadPatientContext();
  const sessions = context.patient ? await recentSessions(context.patient.id, now) : [];
  const checkedIn = sessions.filter((record) => record.checkedIn === true);
  const dates = new Set(checkedIn.flatMap((record) => record.recordedAt ? [dayKey(new Date(timestamp(record.recordedAt)))] : []));
  const day = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (!dates.has(dayKey(day))) day.setDate(day.getDate() - 1);
  let streakDays = 0;
  while (dates.has(dayKey(day)) && streakDays < RECENT_DAYS) {
    streakDays += 1;
    day.setDate(day.getDate() - 1);
  }
  return {
    context,
    trainingCount: checkedIn.length,
    todayCount: checkedIn.filter((record) => record.recordedAt && dayKey(new Date(timestamp(record.recordedAt))) === dayKey(now)).length,
    streakDays,
    records: sessions.slice(0, 3).map(sessionView),
  };
}

export type OverviewData = Awaited<ReturnType<typeof loadOverview>>;

const ALERT_LEVELS = { info: '提示', warning: '警告', critical: '严重' };
const ALERT_STATUSES = { open: '待处理', acknowledged: '已确认', followed_up: '已随访', closed: '已关闭' };
const CHANNELS = { in_app_popup: '应用内提醒', voice_prompt: '语音提示', push_notification: '服务通知', audit_log: '处理记录' };
const DELIVERY_STATUSES = { pending: '待发送', sent: '已发送', failed: '发送失败', fallback: '已转备用渠道' };

function parseAlert(value: unknown): Alert {
  if (!isObject(value) || typeof value.id !== 'string' || typeof value.patientId !== 'string' ||
      typeof value.level !== 'string' || !Object.prototype.hasOwnProperty.call(ALERT_LEVELS, value.level) ||
      typeof value.status !== 'string' || !Object.prototype.hasOwnProperty.call(ALERT_STATUSES, value.status) ||
      !isDateTime(value.triggeredAt)) return invalidResponse();
  if (value.deliveries != null) {
    if (!Array.isArray(value.deliveries)) return invalidResponse();
    for (const delivery of value.deliveries) {
      if (!isObject(delivery) || typeof delivery.channel !== 'string' ||
          !Object.prototype.hasOwnProperty.call(CHANNELS, delivery.channel) || typeof delivery.status !== 'string' ||
          !Object.prototype.hasOwnProperty.call(DELIVERY_STATUSES, delivery.status)) return invalidResponse();
    }
  }
  return value as Alert;
}

export async function loadAlerts() {
  const context = await loadPatientContext();
  const patient = context.patient;
  const alerts = patient ? await readAll('/alerts?patientId=' + encodeURIComponent(patient.id) + '&limit=100', parseAlert) : [];
  if (alerts.some((alert) => alert.patientId !== patient?.id)) return invalidResponse();
  return {
    context,
    alerts: alerts.map((alert) => ({
      id: alert.id,
      level: alert.level,
      levelLabel: ALERT_LEVELS[alert.level],
      status: ALERT_STATUSES[alert.status],
      triggeredAt: formatTime(alert.triggeredAt),
      deliveries: (alert.deliveries || []).map((delivery) => CHANNELS[delivery.channel] + '：' + DELIVERY_STATUSES[delivery.status]).join('；') || '暂无通知记录',
    })),
  };
}

export type AlertsData = Awaited<ReturnType<typeof loadAlerts>>;

export async function submitAssessment(patientId: string, feeling: string): Promise<string> {
  const text = feeling.trim();
  if (!patientId || !text || text.length > 1000) {
    throw new ApiError('VALIDATION_FAILED', '请填写 1 至 1000 字的身体感受', 422);
  }
  const payload: components['schemas']['AssessmentCreate'] = {
    instrument: 'DAILY_FEELING',
    answers: { feeling: text },
    assessedAt: new Date().toISOString(),
  };
  const result = await request<Assessment>({
    path: '/patients/' + encodeURIComponent(patientId) + '/assessments',
    method: 'POST',
    data: payload,
  });
  if (!result || typeof result.id !== 'string' || !result.id || result.patientId !== patientId ||
      result.instrument !== payload.instrument || !isDateTime(result.assessedAt)) return invalidResponse();
  return formatTime(result.assessedAt);
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
