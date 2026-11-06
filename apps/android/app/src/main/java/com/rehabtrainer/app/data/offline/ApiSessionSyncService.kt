package com.rehabtrainer.app.data.offline

import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.core.network.apiCall
import com.rehabtrainer.app.data.contract.SessionBatchRequest
import com.rehabtrainer.app.data.contract.SessionBatchResult

/**
 * 基于 `/sessions` 批量幂等接口的补传实现。
 *
 * - 逐条处理服务端 `accepted`：成功出队，失败保留并累计错误；
 * - 整体失败（网络/服务端错误）时不动队列，等待下次联网重试；
 * - 使用 `clientRecordId` 幂等，超时重试安全。
 */
class ApiSessionSyncService(
    private val queue: SessionUploadQueue,
    private val upload: suspend (SessionBatchRequest) -> SessionBatchResult
) : SessionSyncService {

    override suspend fun flushPending(limit: Int): ApiResult<SessionBatchResult> {
        val pending = queue.pending(limit)
        if (pending.isEmpty()) {
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
            val error = item.error ?: return@forEach
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