package com.rehabtrainer.app.core.network

import com.rehabtrainer.app.BuildConfig
import okhttp3.HttpUrl

/** 网络层常量。 */
object ApiConfig {

    /**
     * 契约 `servers[0]`。必须以 `/` 结尾：接口路径均写相对路径（如 `patients`），
     * 这样 Retrofit 才会保留 `/v1` 前缀。
     */
    val BASE_URL: String = BuildConfig.API_BASE_URL

    const val AUTH_HEADER: String = "Authorization"
    const val BEARER_PREFIX: String = "Bearer "
    const val JSON_MEDIA_TYPE: String = "application/json"

    fun bearer(accessToken: String): String = BEARER_PREFIX + accessToken
}

/** 认证端点不带 Bearer，也不触发刷新重试。 */
internal fun HttpUrl.isAuthEndpoint(): Boolean =
    encodedPath.endsWith("/auth/bind") || encodedPath.endsWith("/auth/refresh")
