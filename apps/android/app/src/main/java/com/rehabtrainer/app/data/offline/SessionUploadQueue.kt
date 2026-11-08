package com.rehabtrainer.app.data.offline

import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.SessionRecordCreate

/** 待上传训练记录及其重试元数据。 */
data class PendingSessionUpload(
    val record: SessionRecordCreate,
    val enqueuedAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMillis: Long? = null,
    val lastError: ApiError? = null
) {
    val clientRecordId: String get() = record.clientRecordId
}

/**
 * 本地待传训练记录队列。
 *
 * 实现约定（Room/SQLite 或 DataStore 均可，当前阶段只定接口）：
 * - 以 `SessionRecordCreate.clientRecordId` 作为本地主键，[enqueue] 遇到同一幂等键应返回 false，
 *   不得产生重复条目。
 * - 队列中的记录可跨进程重启恢复；上传成功后由 [markUploaded] 删除。
 * - 上传失败保留记录并累计 [PendingSessionUpload.attemptCount] 与
 *   [PendingSessionUpload.lastAttemptAtEpochMillis]；退避由 [UploadBackoffPolicy] 按这两个字段计算，
 *   实现只需如实写入，不要自行丢弃或重置。
 * - 单次最多取出 [MAX_BATCH_SIZE] 条，与服务端 `minItems=1, maxItems=100` 对齐。
 */
interface SessionUploadQueue {

    /**
     * 入队。
     *
     * @return true 表示新增；false 表示该 clientRecordId 已在队列中（幂等去重）。
     */
    suspend fun enqueue(record: SessionRecordCreate): Boolean

    suspend fun pending(limit: Int = MAX_BATCH_SIZE): List<PendingSessionUpload>

    /** 服务端 `accepted=true` 后从队列移除。 */
    suspend fun markUploaded(clientRecordIds: Collection<String>)

    /** 本次上传失败（网络或逐条错误），保留记录并记录错误。 */
    suspend fun markFailed(clientRecordIds: Collection<String>, error: ApiError)

    /** 主动丢弃记录（例如数据损坏且用户确认）。 */
    suspend fun remove(clientRecordIds: Collection<String>)

    /** 待上传记录数（pending 计数），含等待重试的条目。 */
    suspend fun pendingCount(): Int

    companion object {
        const val MAX_BATCH_SIZE: Int = 100

        /**
         * 不可重试的逐条错误码：业务拒绝，重试不会改变结果。
         * 命中后应移出重试队列，留待人工处置；其余错误走退避重试。
         */
        val NON_RETRYABLE_ERROR_CODES: Set<String> =
            setOf("VALIDATION_FAILED", "FORBIDDEN", "NOT_FOUND")

        fun isRetryable(errorCode: String): Boolean =
            errorCode !in NON_RETRYABLE_ERROR_CODES
    }
}
