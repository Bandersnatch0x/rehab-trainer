package com.rehabtrainer.app.data.contract

import kotlinx.serialization.Serializable

/** `POST /auth/bind` 请求体（契约内联 schema）。 */
@Serializable
data class BindRequest(
    val bindingToken: String,
    val deviceId: String? = null
)

/** `POST /auth/refresh` 请求体（契约内联 schema）。 */
@Serializable
data class RefreshRequest(
    val refreshToken: String
)

/** 契约 `TokenPair`；`expiresIn` 单位为秒。 */
@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

/** `POST /auth/login` 请求体（契约内联 schema）。 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val totp: String? = null
)

/** 契约 `Me`：当前账号档案。 */
@Serializable
data class Me(
    val accountId: String,
    val displayName: String,
    val roles: List<String>,
    val orgId: String,
    val orgName: String? = null
)

/** 契约 `LoginResponse`：令牌与账号档案一次返回。 */
@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val profile: Me
)
