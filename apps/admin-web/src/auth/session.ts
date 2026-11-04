import { isAdminRole, type AdminRole } from "./roles";

const STORAGE_KEY = "rehab-trainer.admin.session";

/** 401 时由 API 客户端广播，AuthProvider 监听后跳转登录页。 */
export const SESSION_EXPIRED_EVENT = "rehab-trainer:session-expired";

/**
 * 账号档案来自 `POST /auth/login` 返回的 `profile`（LoginResponse）。
 * 如需刷新档案，调用 `GET /me`；两者结构同为契约中的 Me。
 */
export interface AdminProfile {
  accountId: string;
  displayName: string;
  orgId: string;
  role: AdminRole;
}

export interface AdminSession extends AdminProfile {
  accessToken: string;
  refreshToken: string;
}

let unauthorizedNotified = false;

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.length > 0;
}

function isAdminSession(value: unknown): value is AdminSession {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    isNonEmptyString(candidate.accountId) &&
    isNonEmptyString(candidate.displayName) &&
    isNonEmptyString(candidate.orgId) &&
    isNonEmptyString(candidate.accessToken) &&
    isNonEmptyString(candidate.refreshToken) &&
    isAdminRole(candidate.role)
  );
}

export function readSession(): AdminSession | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed: unknown = JSON.parse(raw);
    return isAdminSession(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export function writeSession(session: AdminSession): void {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  unauthorizedNotified = false;
}

export function clearSession(): void {
  window.localStorage.removeItem(STORAGE_KEY);
}

export function getAccessToken(): string | null {
  return readSession()?.accessToken ?? null;
}

/**
 * 统一 401 出口：清会话并广播一次过期事件。
 * 多个并发请求同时 401 时只跳转一次。
 */
export function notifyUnauthorized(): void {
  clearSession();
  if (unauthorizedNotified) {
    return;
  }
  unauthorizedNotified = true;
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}
