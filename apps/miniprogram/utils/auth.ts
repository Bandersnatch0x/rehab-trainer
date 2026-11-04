import type { components } from '../types/api';
import { API_BASE_URL, AUTH_ENDPOINTS, REQUEST_TIMEOUT_MS, STORAGE_KEYS } from './config';
import { ApiError } from './api-error';
import { httpRequest } from './http';

type TokenPair = components['schemas']['TokenPair'];

/** 本地登录态。accessToken 过期时间由服务端 expiresIn 换算为绝对时间。 */
export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  /** 毫秒时间戳。 */
  expiresAt: number;
  /** 毫秒时间戳。 */
  boundAt: number;
}

export interface BindPayload {
  /** 管理端生成的一次性绑定码。 */
  bindingToken: string;
  /** 设备标识，用于建立设备会话。 */
  deviceId?: string;
}

/** undefined 表示尚未从存储加载；null 表示确认无登录态。 */
let cachedSession: AuthSession | null | undefined;
let refreshInFlight: Promise<AuthSession> | null = null;

const sessionExpiredListeners: Array<() => void> = [];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readStoredSession(): AuthSession | null {
  try {
    const raw: unknown = wx.getStorageSync(STORAGE_KEYS.session);
    if (
      isRecord(raw) &&
      typeof raw.accessToken === 'string' &&
      typeof raw.refreshToken === 'string' &&
      typeof raw.expiresAt === 'number' &&
      typeof raw.boundAt === 'number'
    ) {
      return {
        accessToken: raw.accessToken,
        refreshToken: raw.refreshToken,
        expiresAt: raw.expiresAt,
        boundAt: raw.boundAt,
      };
    }
  } catch (error) {
    // 存储不可用时按未登录处理，避免阻断页面启动。
  }
  return null;
}

/** 读取当前登录态（内存缓存优先）。 */
export function getSession(): AuthSession | null {
  if (cachedSession === undefined) {
    cachedSession = readStoredSession();
  }
  return cachedSession;
}

export function getAccessToken(): string | null {
  const session = getSession();
  return session ? session.accessToken : null;
}

export function isLoggedIn(): boolean {
  return getSession() !== null;
}

/**
 * 令牌持久化。绑定码与刷新接口的响应都经这里落盘。
 * 服务端返回结构不可信，先做形状校验再写入。
 */
export function saveSession(pair: TokenPair): AuthSession {
  if (
    !pair ||
    typeof pair.accessToken !== 'string' ||
    typeof pair.refreshToken !== 'string' ||
    typeof pair.expiresIn !== 'number' ||
    !isFinite(pair.expiresIn) ||
    pair.expiresIn <= 0
  ) {
    throw new ApiError('INVALID_TOKEN_RESPONSE', '登录响应格式不正确，请重试', 0);
  }

  const now = Date.now();
  const session: AuthSession = {
    accessToken: pair.accessToken,
    refreshToken: pair.refreshToken,
    expiresAt: now + pair.expiresIn * 1000,
    boundAt: now,
  };

  cachedSession = session;
  try {
    wx.setStorageSync(STORAGE_KEYS.session, session);
  } catch (error) {
    // 写入失败时仍保留内存登录态；关闭小程序后需要重新绑定。
  }
  return session;
}

export function clearSession(): void {
  cachedSession = null;
  try {
    wx.removeStorageSync(STORAGE_KEYS.session);
  } catch (error) {
    // 忽略存储清理失败。
  }
}

/**
 * 订阅登录态失效（刷新令牌被服务端拒绝时触发）。
 * 返回取消订阅函数。页面可在 onLoad 订阅、onUnload 取消。
 */
export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.push(listener);
  return function unsubscribe(): void {
    const index = sessionExpiredListeners.indexOf(listener);
    if (index >= 0) {
      sessionExpiredListeners.splice(index, 1);
    }
  };
}

function notifySessionExpired(): void {
  for (const listener of sessionExpiredListeners.slice()) {
    try {
      listener();
    } catch (error) {
      // 单个订阅者异常不影响其他订阅者。
    }
  }
}

/** 用一次性绑定码换取访问令牌，并持久化登录态。 */
export function bindWithCode(payload: BindPayload): Promise<AuthSession> {
  if (!payload || typeof payload.bindingToken !== 'string' || payload.bindingToken.length === 0) {
    return Promise.reject(new ApiError('VALIDATION_FAILED', '请输入有效的绑定码', 422));
  }

  return httpRequest<TokenPair>({
    url: API_BASE_URL + AUTH_ENDPOINTS.bind,
    method: 'POST',
    data: {
      bindingToken: payload.bindingToken,
      deviceId: payload.deviceId,
    },
    timeout: REQUEST_TIMEOUT_MS,
  }).then((pair) => saveSession(pair));
}

/**
 * 刷新访问令牌。并发调用合并为一次请求。
 *
 * 仅当服务端明确拒绝刷新令牌（401/403）时清除登录态并广播失效；
 * 网络失败保留登录态，允许用户重试。
 */
export function refreshSession(): Promise<AuthSession> {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  const session = getSession();
  if (!session) {
    return Promise.reject(ApiError.unauthorized('尚未登录，请先使用绑定码绑定患者'));
  }

  refreshInFlight = httpRequest<TokenPair>({
    url: API_BASE_URL + AUTH_ENDPOINTS.refresh,
    method: 'POST',
    data: { refreshToken: session.refreshToken },
    timeout: REQUEST_TIMEOUT_MS,
  }).then(
    (pair) => {
      refreshInFlight = null;
      return saveSession(pair);
    },
    (error: unknown) => {
      refreshInFlight = null;
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        clearSession();
        notifySessionExpired();
      }
      throw error;
    },
  );

  return refreshInFlight;
}

/** 主动退出：清除登录态与代操作上下文。 */
export function logout(): void {
  refreshInFlight = null;
  clearSession();
  setActingForPatientId(null);
}

/**
 * 代操作上下文（占位）。
 *
 * 契约尚未提供绑定码之外的代操作端点（未决项 3），这里只保存当前选中的患者，
 * 供后续请求头或参数使用；不要据此认为代操作鉴权已经实现。
 */
export function getActingForPatientId(): string | null {
  try {
    const raw: unknown = wx.getStorageSync(STORAGE_KEYS.actingForPatient);
    return typeof raw === 'string' && raw.length > 0 ? raw : null;
  } catch (error) {
    return null;
  }
}

export function setActingForPatientId(patientId: string | null): void {
  try {
    if (patientId) {
      wx.setStorageSync(STORAGE_KEYS.actingForPatient, patientId);
    } else {
      wx.removeStorageSync(STORAGE_KEYS.actingForPatient);
    }
  } catch (error) {
    // 忽略存储异常；代操作本身仍是占位能力。
  }
}
