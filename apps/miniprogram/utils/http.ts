import { ApiError } from './api-error';

/**
 * 契约中出现的方法。wx.request 官方类型未列出 PATCH，
 * 而契约里的 PATCH 端点属于管理端职责（处方调整、随访登记），小程序端不调用；
 * 这里保留 PATCH 仅为与契约对齐，真正调用时依赖基础库透传。
 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface HttpRequestOptions {
  url: string;
  method?: HttpMethod;
  data?: unknown;
  header?: Record<string, string>;
  timeout?: number;
}

/**
 * 最底层 HTTP 调用：只做 wx.request 的 Promise 化与统一错误解析。
 * 不注入令牌、不处理 401 —— 这两件事由 utils/request.ts 负责，
 * 以免 auth 模块与 request 模块互相依赖。
 */
export function httpRequest<T>(options: HttpRequestOptions): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const header: Record<string, string> = {
      'content-type': 'application/json',
      ...(options.header || {}),
    };

    wx.request({
      url: options.url,
      // PATCH 不在 wx.request 官方类型联合中，见 HttpMethod 注释。
      method: (options.method || 'GET') as unknown as WechatMiniprogram.RequestOption['method'],
      data: options.data as WechatMiniprogram.IAnyObject | string | ArrayBuffer | undefined,
      header,
      timeout: options.timeout,
      success: (res) => {
        const status = res.statusCode;
        if (status >= 200 && status < 300) {
          resolve(res.data as unknown as T);
          return;
        }
        reject(ApiError.fromResponse(status, res.data));
      },
      fail: (err) => {
        reject(ApiError.network(err && err.errMsg ? err.errMsg : ''));
      },
    });
  });
}
