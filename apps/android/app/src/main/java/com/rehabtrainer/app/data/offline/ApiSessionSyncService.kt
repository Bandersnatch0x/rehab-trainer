package com.rehabtrainer.app.data.offline

import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.core.network.apiCall
import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.SessionBatchRequest
import com.rehabtrainer.app.data.contract.SessionBatchResult

/**
 * 基于 `/sessions` 批量幂等接口的补传实现。
 *
 * - 逐条处理服务端 `accepted`：成功出队，失败保留并累计错误；
 * - 整体失败（网络/服务端错误）时不动队列，等待下次联网重试；
 * - 未到 [UploadBackoffPolicy] 退避时间的条目本轮跳过，不发请求；
 * - 使用 `clientRecordId` 幂等，超时重试安全。
 *
 * @param nowMillis 当前时间，用于判定退避是否到期；测试注入假时钟。
 */
class ApiSessionSyncService(
    private val queue: SessionUploadQueue,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val upload: suspend (SessionBatchRequest) -> SessionBatchResult
) : SessionSyncService {

    override suspend fun flushPending(limit: Int): ApiResult<SessionBatchResult> {
        val now = nowMillis()
        // 先按退避过滤再截断：队列头若全在退避中，先截断会把后面到期的记录一起挡掉。
        val pending = queue.pending(Int.MAX_VALUE)
            .filter { UploadBackoffPolicy.isDue(it, now) }
            .take(limit)
        if (pending.isEmpty()) {
            // 队列为空、或全部在退避中：都不发请求。
            // 退避中返回空成功而不是失败——调用方只关心「本轮有没有传」，没有错误可提示。
            return ApiResult.Success(SessionBatchResult(results = emptyList()))
        }
        val result = apiCall { upload(SessionBatchRequest(records = pending.map { it.record })) }
        if (result is ApiResult.Failure) {
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
                // 业务拒绝（校验失败/越权等）重试无意义，移出队列避免无限重试。
                queue.markFailed(listOf(item.clientRecordId), error)
                queue.remove(listOf(item.clientRecordId))
            }
        }
        return result
    }
}