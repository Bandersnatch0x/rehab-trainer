package com.rehabtrainer.app.data.offline

import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.contract.SessionBatchResult

/**
 * 离线记录补传服务（接口先行，实现待接入）。
 *
 * 行为约定：
 * - 从 [SessionUploadQueue] 取最多 [limit] 条，组装 `SessionBatchRequest` 调 `POST /sessions`。
 * - 服务端按 `clientRecordId` 去重，因此网络超时后的重试是安全的。
 * - 逐条处理 `accepted`：成功条目出队；失败条目留在队列并记录 `error`。
 * - `ApiResult.Failure` 时不得清空队列，等待下次联网重试。
 */
interface SessionSyncService {

    suspend fun flushPending(
        limit: Int = SessionUploadQueue.MAX_BATCH_SIZE
    ): ApiResult<SessionBatchResult>
}
