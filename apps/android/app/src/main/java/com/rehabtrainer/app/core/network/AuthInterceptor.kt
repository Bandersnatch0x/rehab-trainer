package com.rehabtrainer.app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 给除 `/auth/*` 外的请求注入 `Authorization: Bearer <accessToken>`。
 *
 * 刷新令牌由 [TokenAuthenticator] 在收到 401 时完成，本拦截器只负责常规注入。
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(ApiConfig.AUTH_HEADER) != null || request.url.isAuthEndpoint()) {
            return chain.proceed(request)
        }

        val accessToken = tokenStore.current()?.accessToken
            ?: return chain.proceed(request)

        val authorizedRequest = request.newBuilder()
            .header(ApiConfig.AUTH_HEADER, ApiConfig.bearer(accessToken))
            .build()
        return chain.proceed(authorizedRequest)
    }
}
