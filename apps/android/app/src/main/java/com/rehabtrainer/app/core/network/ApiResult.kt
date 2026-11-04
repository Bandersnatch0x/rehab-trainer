package com.rehabtrainer.app.core.network

import com.rehabtrainer.app.data.contract.ApiError
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** 所有网络调用的统一返回类型；页面与仓储层只消费它，不直接捕获 Retrofit 异常。 */
sealed interface ApiResult<out T> {

    data class Success<T>(val value: T) : ApiResult<T>

    /** [httpStatus] 为 null 表示请求没有到达 HTTP 层（网络或解析错误）。 */
    data class Failure(val error: ApiError, val httpStatus: Int? = null) : ApiResult<Nothing>
}

/**
 * 统一错误处理入口。用法：
 *
 * ```
 * val result = apiCall { container.patientsApi.getPatient(patientId) }
 * ```
 */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        ApiResult.Failure(
            error = ApiErrorParser.from(response = e.response(), httpStatus = e.code()),
            httpStatus = e.code()
        )
    } catch (e: IOException) {
        ApiResult.Failure(error = ApiErrorParser.fromIo(e))
    } catch (e: SerializationException) {
        ApiResult.Failure(error = ApiErrorParser.fromSerialization(e))
    }

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value
