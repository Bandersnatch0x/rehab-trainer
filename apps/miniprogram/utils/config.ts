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
 * 演示模式总开关。
 *
 * **生产构建必须置为 false。** 为 true 时客户端携带演示账号凭据，
 * 小程序包可被反编译取出，等同于公开该账号。演示账号只连演示数据。
 */
export const DEMO_LOGIN_ENABLED = true;

/**
 * 本地演示账号，由 `services/api` 的 seed 脚本生成，仅用于演示模式。
 * 生产环境不应存在该账号；绑定页也明确标注演示数据。
 *
 * 安全边界：这是演示凭据，不是秘密。任何拿到小程序包的人都能读到。
 * 因此生产构建必须同时满足：`DEMO_LOGIN_ENABLED = false` 且后端未创建该账号。
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
