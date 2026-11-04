package com.rehabtrainer.app.data.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 契约 `Error` schema 的镜像。
 *
 * 所有非 2xx 响应都由 `core.network.ApiErrorParser` 统一解析为该类型，
 * 调用方通过 `ApiResult.Failure.error` 消费，不直接处理 HTTP 状态码。
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val detail: JsonObject? = null
)
