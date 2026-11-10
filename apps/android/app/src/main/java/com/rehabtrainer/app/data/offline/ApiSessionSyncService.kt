package com.rehabtrainer.app.data.offline

import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.core.network.apiCall
import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.SessionBatchItemResult
import com.rehabtrainer.app.data.contract.SessionBatchRequest
import com.rehabtrainer.app.data.contract.SessionBatchResult

/**
 * 基于 `/sessions` 批量幂等接口的补传实现。
 *
 * - 只上传当前账号名下的记录：队列里的旧账号/未知归属条目不会被新令牌带上去；
 * - 逐条处理服务端 `accepted`：成功出队，可重试失败保留并累计错误；
 * - 整体失败（网络/服务端错误）时不动队列，等待下次联网重试；
 * - 整批被业务拒绝时退化为逐条提交，避免一条畸形记录卡死同批合法记录；
 * - 不可重试的逐条错误进隔离区，保留原始记录与错误等待人工处置；
 * - 使用 `clientRecordId` 幂等，超时重试安全。
 *
 * @param nowMillis 当前时间，用于判定退避是否到期；测试注入假时钟。
 */
class ApiSessionSyncService(
    private val queue: SessionUploadQueue,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val upload: suspend (SessionBatchRequest) -> SessionBatchResult
) : SessionSyncService {

    override suspend fun flushPending(
        limit: Int,
        ownerAccountId: String?
    ): ApiResult<SessionBatchResult> {
        // 没有当前账号时不上传：否则旧患者的记录会带着新会话的令牌发出去。
        if (ownerAccountId == null) {
            return ApiResult.Success(SessionBatchResult(results = emptyList()))
        }
        val now = nowMillis()
        // 先按退避过滤再截断：队列头若全在退避中，先截断会把后面到期的记录一起挡掉。
        val pending = queue.pending(Int.MAX_VALUE, ownerAccountId)
            .filter { UploadBackoffPolicy.isDue(it, now) }
            .take(limit)
        if (pending.isEmpty()) {
            // 队列为空、或全部在退避中：都不发请求。
            // 退避中返回空成功而不是失败——调用方只关心「本轮有没有传」，没有错误可提示。
            return ApiResult.Success(SessionBatchResult(results = emptyList()))
        }
        val result = apiCall { upload(SessionBatchRequest(records = pending.map { it.record })) }
        if (result is ApiResult.Failure) {
            // 只有整批校验失败才逐条定位坏记录；401/403 等鉴权问题逐条重试只会
            // 把整队记录误隔离，保持整批退避等待会话恢复。
            val batchValidationFailed = result.httpStatus == 422 ||
                result.error.code == "VALIDATION_FAILED"
            if (batchValidationFailed) {
                return uploadIndividually(pending)
            }
            queue.markFailed(pending.map { it.clientRecordId }, result.error)
            return result
        }

        val body = (result as ApiResult.Success).value
        val accepted = body.results.filter { it.accepted }.map { it.clientRecordId }
        if (accepted.isNotEmpty()) {
            queue.markUploaded(accepted)
        }
        body.results.filterNot { it.accepted }.forEach { item ->
            val error = item.error ?: ApiError("UNKNOWN_REJECTION", "服务端拒绝但未返回错误码")
            if (SessionUploadQueue.isRetryable(error.code)) {
                queue.markFailed(listOf(item.clientRecordId), error)
            } else {
                // 业务拒绝（校验失败/越权等）重试无意义，进隔离区等待人工处置。
                queue.quarantine(listOf(item.clientRecordId), error)
            }
        }
        return result
    }

    /**
     * 整批业务拒绝后的逐条提交。
     *
     * 遇到网络/服务端故障时立即停止，把当前及剩余条目一并交给退避——
     * 逐条重试同样会失败，继续发只会放大故障。
     */
    private suspend fun uploadIndividually(
        pending: List<PendingSessionUpload>
    ): ApiResult<SessionBatchResult> {
        val results = mutableListOf<SessionBatchItemResult>()
        for ((index, item) in pending.withIndex()) {
            when (val single = apiCall {
                upload(SessionBatchRequest(records = listOf(item.record)))
            }) {
                is ApiResult.Failure -> {
                    if (SessionUploadQueue.isRetryable(single.error.code)) {
                        queue.markFailed(
                            pending.drop(index).map { it.clientRecordId },
                            single.error
                        )
                        return single
                    }
                    queue.quarantine(listOf(item.clientRecordId), single.error)
                    results += SessionBatchItemResult(
                        clientRecordId = item.clientRecordId,
                        accepted = false,
                        error = single.error
                    )
                }

                is ApiResult.Success -> {
                    val itemResult = single.value.results
                        .firstOrNull { it.clientRecordId == item.clientRecordId }
                    when {
                        itemResult == null -> {
                            val error = ApiError(
                                "UNKNOWN_REJECTION",
                                "服务端未返回该记录的接收结果"
                            )
                            queue.quarantine(listOf(item.clientRecordId), error)
                            results += SessionBatchItemResult(
                                clientRecordId = item.clientRecordId,
                                accepted = false,
                                error = error
                            )
                        }

                        itemResult.accepted -> {
                            queue.markUploaded(listOf(item.clientRecordId))
                            results += itemResult
                        }

                        else -> {
                            val error = itemResult.error
                                ?: ApiError("UNKNOWN_REJECTION", "服务端拒绝但未返回错误码")
                            if (SessionUploadQueue.isRetryable(error.code)) {
                                queue.markFailed(listOf(item.clientRecordId), error)
                            } else {
                                queue.quarantine(listOf(item.clientRecordId), error)
                            }
                            results += itemResult.copy(error = error)
                        }
                    }
                }
            }
        }
        return ApiResult.Success(SessionBatchResult(results = results))
    }
}
