/**
 * 小程序运行时配置。
 *
 * API_BASE_URL 与 packages/api-contract/openapi.yaml 的 servers 占位地址一致，
 * 部署时替换为真实域名，并在微信公众平台配置 request 合法域名。
 */
export const API_BASE_URL = 'https://api.rehab-trainer.local/v1';

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
