package com.rehabtrainer.app.data.offline

import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.SessionRecordCreate

/** 队列条目的处置状态。 */
enum class UploadStatus {
    /** 等待上传（含退避中）。 */
    PENDING,

    /** 服务端明确拒绝或归属不明，重试不会自动改变结果；保留记录与错误等待人工处置。 */
    QUARANTINED
}

/** 待上传训练记录及其重试元数据。 */
data class PendingSessionUpload(
    val record: SessionRecordCreate,
    val enqueuedAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMillis: Long? = null,
    val lastError: ApiError? = null,
    /** 入队时登录的账号；null 表示升级前遗留、归属未知。 */
    val ownerAccountId: String? = null,
    val status: UploadStatus = UploadStatus.PENDING
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
 * - 每条记录带 [PendingSessionUpload.ownerAccountId]：上传只取当前账号的条目，
 *   旧账号/未知归属的记录不能借新令牌上传。
 * - 不可重试的拒绝进 [quarantine]，保留记录与错误等待人工处置，不得静默删除。
 */
interface SessionUploadQueue {

    /**
     * 入队。
     *
     * @param ownerAccountId 当前登录账号；未知时传 null，条目会被当作「归属不明」等待人工处理。
     * @return true 表示新增；false 表示该 clientRecordId 已在队列中（幂等去重）。
     */
    suspend fun enqueue(record: SessionRecordCreate, ownerAccountId: String?): Boolean

    /**
     * 取出待上传条目。
     *
     * @param ownerAccountId 非 null 时只返回该账号的条目；null 表示不过滤归属（测试/内部用途）。
     *   只返回 [UploadStatus.PENDING]；隔离条目不会自动上传。
     */
    suspend fun pending(
        limit: Int = MAX_BATCH_SIZE,
        ownerAccountId: String? = null
    ): List<PendingSessionUpload>

    /** 服务端 `accepted=true` 后从队列移除。 */
    suspend fun markUploaded(clientRecordIds: Collection<String>)

    /** 本次上传失败（网络或逐条错误），保留记录并记录错误。 */
    suspend fun markFailed(clientRecordIds: Collection<String>, error: ApiError)

    /** 不可重试的拒绝：保留原始记录与错误，移出自动上传，等待人工处置。 */
    suspend fun quarantine(clientRecordIds: Collection<String>, error: ApiError)

    /** 主动丢弃记录（例如用户确认不再上传）。 */
    suspend fun remove(clientRecordIds: Collection<String>)

    /** 登出/换绑时清空队列：旧患者的记录不能在新账号下重放。 */
    suspend fun clear()

    /** 只清某个归属的条目；null 表示清掉归属未知的遗留条目。 */
    suspend fun clearByOwner(ownerAccountId: String?)

    /** 当前账号需人工处置的条目数（服务端拒绝）。归属未知的遗留条目不计入。 */
    suspend fun heldCount(ownerAccountId: String): Int

    /**
     * 把当前账号的隔离条目归还并重置退避，等待下一次上传。
     *
     * 不认领归属未知的遗留条目：无法证明它属于当前账号，认领可能把别人的记录
     * 写到本账号名下。这类条目由登出时的 [clearByOwner] 清理。
     */
    suspend fun retryHeld(ownerAccountId: String)

    /** 待上传记录数（全部条目，含退避与隔离）。 */
    suspend fun pendingCount(): Int

    companion object {
        const val MAX_BATCH_SIZE: Int = 100

        /**
         * 不可重试的逐条错误码：业务拒绝，重试不会改变结果。
         * 命中后应移入隔离区等待人工处置；其余错误走退避重试。
         */
        val NON_RETRYABLE_ERROR_CODES: Set<String> =
            setOf("VALIDATION_FAILED", "FORBIDDEN", "NOT_FOUND")

        fun isRetryable(errorCode: String): Boolean =
            errorCode !in NON_RETRYABLE_ERROR_CODES
    }
}
