package com.rehabtrainer.app.data.remote

import com.rehabtrainer.app.data.contract.BindRequest
import com.rehabtrainer.app.data.contract.LoginRequest
import com.rehabtrainer.app.data.contract.LoginResponse
import com.rehabtrainer.app.data.contract.RefreshRequest
import com.rehabtrainer.app.data.contract.TokenPair
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * `auth` 下的两个端点（契约安全要求为 `security: []`，不带 Bearer）。
 *
 * 这两个方法由 `ApiContainer` 中不带鉴权拦截器的 Retrofit 实例创建，
 * 以免刷新令牌时再次触发 401 刷新循环。
 */
interface AuthApi {

    /** POST /auth/bind — 用一次性绑定码换取访问令牌。 */
    @POST("auth/bind")
    suspend fun bind(@Body body: BindRequest): TokenPair

    /** POST /auth/login — 内部账号登录，令牌与账号档案一次返回。 */
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    /** POST /auth/refresh — 刷新访问令牌。 */
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPair
}
