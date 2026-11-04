package com.rehabtrainer.app.data.remote

import com.rehabtrainer.app.data.contract.Me
import retrofit2.http.GET

/**
 * `GET /me` — 当前账号信息，需要 Bearer。
 *
 * 由带 [com.rehabtrainer.app.core.network.AuthInterceptor] 的 Retrofit 实例创建，
 * 与 [AuthApi] 的无鉴权实例分开。
 */
interface AccountApi {

    /** GET /me — 返回账号、角色与所属机构。 */
    @GET("me")
    suspend fun me(): Me
}
