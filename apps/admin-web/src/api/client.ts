import createClient, { type Middleware } from "openapi-fetch";
import { getAccessToken, notifyUnauthorized } from "../auth/session";
import type { ApiContractError } from "./types";
import type { paths } from "./schema";

const DEFAULT_BASE_URL = "https://api.rehab-trainer.local/v1";
const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

export const API_BASE_URL: string =
  configuredBaseUrl && configuredBaseUrl.length > 0
    ? configuredBaseUrl
    : DEFAULT_BASE_URL;

/** 这两个端点本身用于换取令牌，不带 Bearer，也不触发登录跳转。 */
const PUBLIC_PATHS = new Set(["/auth/bind", "/auth/login", "/auth/refresh"]);

const STATUS_FALLBACK: Record<number, { code: string; message: string }> = {
  400: { code: "VALIDATION_FAILED", message: "请求校验失败" },
  401: { code: "UNAUTHORIZED", message: "未认证或令牌已失效" },
  403: { code: "FORBIDDEN", message: "无权限或跨机构访问被拒绝" },
  404: { code: "NOT_FOUND", message: "资源不存在" },
  409: { code: "CONFLICT", message: "版本冲突或重复提交" },
  422: { code: "VALIDATION_FAILED", message: "请求校验失败" },
  500: { code: "INTERNAL_ERROR", message: "服务端错误" },
};

export interface ApiError {
  code: string;
  message: string;
  detail?: Record<string, unknown>;
  /** 网络层失败时没有 HTTP 状态码。 */
  status?: number;
}

export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; error: ApiError };

/** openapi-fetch 返回值的最小结构，便于统一归一化。 */
type OpenApiResponse<T> = {
  data?: T;
  error?: unknown;
  response: Response;
};

function isContractError(value: unknown): value is ApiContractError {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.code === "string" && typeof candidate.message === "string"
  );
}

export function normalizeApiError(error: unknown, status?: number): ApiError {
  if (isContractError(error)) {
    return {
      code: error.code,
      message: error.message,
      detail: error.detail,
      status,
    };
  }
  const fallback = status === undefined ? undefined : STATUS_FALLBACK[status];
  return {
    code: fallback?.code ?? "UNKNOWN_ERROR",
    message: fallback?.message ?? "请求失败",
    status,
  };
}

/** 把 openapi-fetch 的 { data, error, response } 归一化为可区分成功/失败的联合。 */
export async function toResult<T>(
  pending: Promise<OpenApiResponse<T>>,
): Promise<ApiResult<T>> {
  try {
    const result = await pending;
    if (result.response.ok) {
      return { ok: true, data: result.data as T };
    }
    return {
      ok: false,
      error: normalizeApiError(result.error, result.response.status),
    };
  } catch (cause) {
    return {
      ok: false,
      error: {
        code: "NETWORK_ERROR",
        message: cause instanceof Error ? cause.message : "网络请求失败",
      },
    };
  }
}

const authMiddleware: Middleware = {
  onRequest({ request, schemaPath }) {
    const token = getAccessToken();
    if (!token || PUBLIC_PATHS.has(schemaPath)) {
      return undefined;
    }
    const headers = new Headers(request.headers);
    headers.set("Authorization", `Bearer ${token}`);
    return new Request(request, { headers });
  },
  onResponse({ response, schemaPath }) {
    if (response.status === 401 && !PUBLIC_PATHS.has(schemaPath)) {
      notifyUnauthorized();
    }
    return undefined;
  },
};

export const api = createClient<paths>({ baseUrl: API_BASE_URL });

api.use(authMiddleware);
