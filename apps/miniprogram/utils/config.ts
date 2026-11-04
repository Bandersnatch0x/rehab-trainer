/**
 * 小程序运行时配置。
 *
 * 开发工具连接本机后端；真机需换成开发机局域网地址。
 * 发布前替换为 HTTPS 域名、配置 request 合法域名并恢复域名校验。
 */
export const API_BASE_URL = 'http://127.0.0.1:8000/v1';

/** 单次请求默认超时（毫秒）。 */
export const REQUEST_TIMEOUT_MS = 30000;

/** 无需访问令牌的认证端点。 */
export const AUTH_ENDPOINTS = {
  bind: '/auth/bind',
  refresh: '/auth/refresh',
} as const;

/** 本地存储键。以 v1 结尾，便于登录态结构变更时整体失效。 */
export const STORAGE_KEYS = {
  session: 'rehab.auth.session.v1',
  actingForPatient: 'rehab.auth.actingFor.v1',
} as const;
