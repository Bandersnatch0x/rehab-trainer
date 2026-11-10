import { API_BASE_URL, REQUEST_TIMEOUT_MS } from './config';
import { ApiError } from './api-error';
import { httpRequest, type HttpMethod } from './http';
import { clearSession, getAccessToken, refreshSession } from './auth';

export { ApiError };
export type { HttpMethod };

/**
 * 小程序请求层。
 *
 * 约定（D3 / T5）：小程序端训练走云端异步 CV —— 训练视频上传后由服务端分析，
 * 小程序不提供实时姿态反馈，本层只负责接口调用，不承载训练执行逻辑。
 *
 * 认证流程：
 * 1. 除 auth: false 的端点外，自动附加 Bearer 令牌；
 * 2. 服务端返回 401 时合并刷新一次令牌，成功后重放原请求一次；
 * 3. 刷新被拒（401/403）会清除登录态并广播失效事件，调用方自行引导重新绑定。
 */
export interface RequestOptions {
  /** 以 / 开头，相对 API_BASE_URL；也接受完整 http(s) 地址。 */
  path: string;
  method?: HttpMethod;
  data?: unknown;
  header?: Record<string, string>;
  /** 是否附加令牌，默认 true。 */
  auth?: boolean;
  timeout?: number;
  /** 401 后是否尝试刷新并重放，默认 true。 */
  retryOnUnauthorized?: boolean;
}

function joinUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  return API_BASE_URL + (path.charAt(0) === '/' ? path : '/' + path);
}

function send<T>(options: RequestOptions, accessToken: string | null): Promise<T> {
  const header: Record<string, string> = {
    ...(options.header || {}),
  };
  if (accessToken) {
    header.Authorization = 'Bearer ' + accessToken;
  }

  return httpRequest<T>({
    url: joinUrl(options.path),
    method: options.method || 'GET',
    data: options.data,
    header,
    timeout: options.timeout || REQUEST_TIMEOUT_MS,
  });
}

/**
 * 发起业务请求。
 *
 * 失败时抛出 ApiError：优先使用服务端契约错误体的 code/message，
 * 无错误体时按 HTTP 状态码兜底，网络失败时 code 为 NETWORK_ERROR。
 */
export async function request<T>(options: RequestOptions): Promise<T> {
  const requiresAuth = options.auth !== false;
  const accessToken = requiresAuth ? getAccessToken() : null;

  if (requiresAuth && !accessToken) {
    throw ApiError.unauthorized('尚未登录，请先使用绑定码绑定患者');
  }

  try {
    return await send<T>(options, accessToken);
  } catch (error) {
    const shouldRefresh =
      requiresAuth &&
      accessToken !== null &&
      options.retryOnUnauthorized !== false &&
      error instanceof ApiError &&
      error.isUnauthorized;

    if (!shouldRefresh) {
      throw error;
    }

    // 并发 401 由 auth.refreshSession 合并为一次刷新；失败时异常继续上抛。
    const session = await refreshSession();
    try {
      return await send<T>(options, session.accessToken);
    } catch (error) {
      // 新令牌也被拒（账号被禁用/撤销）时必须清掉缓存会话，否则会反复刷新重试。
      if (error instanceof ApiError && error.isUnauthorized) {
        clearSession();
      }
      throw error;
    }
  }
}
