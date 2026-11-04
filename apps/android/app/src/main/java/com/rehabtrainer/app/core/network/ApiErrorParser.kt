package com.rehabtrainer.app.core.network

import com.rehabtrainer.app.data.contract.ApiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException

/** 统一错误码：优先使用服务端 `Error.code`，本地错误使用下列常量。 */
object ErrorCodes {
    const val UNAUTHORIZED: String = "UNAUTHORIZED"
    const val FORBIDDEN: String = "FORBIDDEN"
    const val NOT_FOUND: String = "NOT_FOUND"
    const val CONFLICT: String = "CONFLICT"
    const val VALIDATION_FAILED: String = "VALIDATION_FAILED"
    const val NETWORK_ERROR: String = "NETWORK_ERROR"
    const val INVALID_RESPONSE: String = "INVALID_RESPONSE"
    const val UNKNOWN: String = "UNKNOWN"
}

/**
 * 把契约 `Error` schema 解析为 [ApiError]。
 *
 * 契约未给 409（绑定码过期）等响应定义 body，解析失败时按 HTTP 状态码回退。
 */
internal object ApiErrorParser {

    suspend fun from(response: Response<*>?, httpStatus: Int): ApiError =
        withContext(Dispatchers.IO) {
            val rawBody = response?.errorBody()?.string()
            if (!rawBody.isNullOrBlank()) {
                val parsed = runCatching {
                    ApiJson.instance.decodeFromString(ApiError.serializer(), rawBody)
                }.getOrNull()
                if (parsed != null) {
                    return@withContext parsed
                }
            }
            ApiError(
                code = fallbackCode(httpStatus),
                message = "请求失败（HTTP $httpStatus）"
            )
        }

    fun fromIo(cause: IOException): ApiError {
        val serializationCause = generateSequence<Throwable>(cause) { it.cause }
            .filterIsInstance<SerializationException>()
            .firstOrNull()
        if (serializationCause != null) {
            return fromSerialization(serializationCause)
        }
        return ApiError(
            code = ErrorCodes.NETWORK_ERROR,
            message = cause.message?.takeIf { it.isNotBlank() }
                ?: "网络不可用，请检查网络后重试"
        )
    }

    fun fromSerialization(cause: SerializationException): ApiError = ApiError(
        code = ErrorCodes.INVALID_RESPONSE,
        message = cause.message?.takeIf { it.isNotBlank() }
            ?: "服务端响应无法解析"
    )

    private fun fallbackCode(httpStatus: Int): String = when (httpStatus) {
        401 -> ErrorCodes.UNAUTHORIZED
        403 -> ErrorCodes.FORBIDDEN
        404 -> ErrorCodes.NOT_FOUND
        409 -> ErrorCodes.CONFLICT
        422 -> ErrorCodes.VALIDATION_FAILED
        else -> "HTTP_$httpStatus"
    }
}
