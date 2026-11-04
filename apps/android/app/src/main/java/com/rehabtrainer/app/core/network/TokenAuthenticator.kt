package com.rehabtrainer.app.core.network

import com.rehabtrainer.app.data.contract.TokenPair
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * 401 时的 Bearer 刷新器。
 *
 * - [refresh] 必须是阻塞调用（OkHttp Authenticator 线程不接受协程），由 [ApiContainer] 注入
 *   `POST /auth/refresh` 的实现。
 * - 多个并发请求同时 401 时只刷新一次：后到者发现 accessToken 已变化，直接换新令牌重试。
 * - 同一请求最多重试 [MAX_RETRIES] 次，避免刷新死循环。
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refresh: (refreshToken: String) -> TokenPair?
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedRequest = response.request
        if (failedRequest.url.isAuthEndpoint()) {
            return null
        }
        if (response.priorResponseCount() >= MAX_RETRIES) {
            return null
        }

        val failedAccessToken = failedRequest.header(ApiConfig.AUTH_HEADER)
            ?.removePrefix(ApiConfig.BEARER_PREFIX)

        synchronized(refreshLock) {
            val current = tokenStore.current()

            // 其他线程已完成刷新：直接用新令牌重放，不再请求 /auth/refresh。
            if (current != null && current.accessToken != failedAccessToken) {
                return failedRequest.withBearer(current.accessToken)
            }

            val refreshToken = current?.refreshToken ?: return null
            val refreshed = refresh(refreshToken) ?: return null
            tokenStore.save(refreshed)
            return failedRequest.withBearer(refreshed.accessToken)
        }
    }

    private fun Request.withBearer(accessToken: String): Request =
        newBuilder()
            .header(ApiConfig.AUTH_HEADER, ApiConfig.bearer(accessToken))
            .build()

    private fun Response.priorResponseCount(): Int {
        var count = 0
        var cursor: Response? = this
        while (cursor != null) {
            count += 1
            cursor = cursor.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_RETRIES: Int = 3
    }
}
