import type { components } from '../types/api';

/** 契约 components.schemas.Error —— 服务端统一错误体。 */
export type ApiErrorBody = components['schemas']['Error'];

const STATUS_FALLBACK: Record<number, { code: string; message: string }> = {
  400: { code: 'BAD_REQUEST', message: '请求格式不正确' },
  401: { code: 'UNAUTHORIZED', message: '登录状态已失效，请重新绑定' },
  403: { code: 'FORBIDDEN', message: '没有权限执行此操作' },
  404: { code: 'NOT_FOUND', message: '请求的内容不存在' },
  409: { code: 'CONFLICT', message: '数据已被修改或绑定码已失效' },
  422: { code: 'VALIDATION_FAILED', message: '提交内容校验未通过' },
  429: { code: 'RATE_LIMITED', message: '操作过于频繁，请稍后再试' },
  500: { code: 'SERVER_ERROR', message: '服务暂时不可用，请稍后再试' },
  502: { code: 'SERVICE_UNAVAILABLE', message: '服务暂时不可用，请稍后再试' },
  503: { code: 'SERVICE_UNAVAILABLE', message: '服务暂时不可用，请稍后再试' },
  504: { code: 'SERVICE_UNAVAILABLE', message: '服务响应超时，请稍后再试' },
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** 从任意响应体中解析契约错误体；不是契约错误体时返回 null。 */
export function parseErrorBody(body: unknown): ApiErrorBody | null {
  if (!isRecord(body)) {
    return null;
  }
  const code = body.code;
  const message = body.message;
  if (typeof code !== 'string' || typeof message !== 'string') {
    return null;
  }
  const detail = body.detail;
  return {
    code,
    message,
    detail: isRecord(detail) ? detail : undefined,
  };
}

/** 服务端未返回契约错误体时，按 HTTP 状态码兜底。 */
export function fallbackForStatus(status: number): { code: string; message: string } {
  const known = STATUS_FALLBACK[status];
  if (known) {
    return known;
  }
  return {
    code: status >= 500 ? 'SERVER_ERROR' : 'REQUEST_FAILED',
    message: '请求失败（HTTP ' + status + '）',
  };
}

/**
 * 统一错误类型。
 *
 * - code / message 优先取契约错误体；
 * - status 为 HTTP 状态码，网络失败时为 0；
 * - detail 保留服务端附加信息，供调用方判断，不直接展示给患者。
 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly detail: Record<string, unknown> | undefined;

  constructor(code: string, message: string, status: number, detail?: Record<string, unknown>) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.detail = detail;
    // 保证在 ES5 目标下 instanceof 仍然成立。
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  static fromResponse(status: number, body: unknown): ApiError {
    const parsed = parseErrorBody(body);
    if (parsed) {
      return new ApiError(parsed.code, parsed.message, status, parsed.detail);
    }
    const fallback = fallbackForStatus(status);
    return new ApiError(fallback.code, fallback.message, status);
  }

  static network(message: string): ApiError {
    return new ApiError('NETWORK_ERROR', message || '网络连接失败，请检查网络后重试', 0);
  }

  static unauthorized(message: string): ApiError {
    return new ApiError('UNAUTHORIZED', message, 401);
  }
}
