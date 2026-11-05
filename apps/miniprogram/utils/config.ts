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
  login: '/auth/login',
  refresh: '/auth/refresh',
} as const;

/**
 * 本地演示账号，由 `services/api` 的 seed 脚本生成，仅用于演示模式。
 * 生产环境不应存在该账号；绑定页也明确标注演示数据。
 */
export const DEMO_ACCOUNT = {
  username: 'patient',
  password: 'patient123',
} as const;

/** 本地存储键。以 v1 结尾，便于登录态结构变更时整体失效。 */
export const STORAGE_KEYS = {
  session: 'rehab.auth.session.v1',
  actingForPatient: 'rehab.auth.actingFor.v1',
  demoMode: 'rehab.auth.demo.v1',
} as const;
