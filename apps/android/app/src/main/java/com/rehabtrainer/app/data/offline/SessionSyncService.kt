package com.rehabtrainer.app.data.offline

import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.contract.SessionBatchResult

/**
 * 离线记录补传服务。
 *
 * 行为约定：
 * - 只上传 [ownerAccountId] 名下、且未到退避时间的 [SessionUploadQueue.MAX_BATCH_SIZE] 条记录；
 * - 服务端按 `clientRecordId` 去重，因此网络超时后的重试是安全的；
 * - 逐条处理 `accepted`：成功条目出队；可重试失败保留并记录 `error`；
 * - 错误码命中 [SessionUploadQueue.NON_RETRYABLE_ERROR_CODES] 时移入隔离区，等待人工处置；
 * - 整批被业务拒绝时退化为逐条提交，单条畸形记录不能拖死同批合法记录；
 * - `ApiResult.Failure` 时不得清空队列，等待下次联网重试。
 */
interface SessionSyncService {

    suspend fun flushPending(
        limit: Int = SessionUploadQueue.MAX_BATCH_SIZE,
        ownerAccountId: String? = null
    ): ApiResult<SessionBatchResult>
}
