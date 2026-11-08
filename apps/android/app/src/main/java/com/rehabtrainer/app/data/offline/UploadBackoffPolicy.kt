package com.rehabtrainer.app.data.offline

/**
 * 待上传记录的退避策略（票 0026 遗留的「客户端统一退避」）。
 *
 * 服务端过载时快速返回 503，客户端必须跟着退让：没有退避时，App 每次启动、每次进记录页
 * 都会立刻重试一遍，把服务端的快速失败放大成重试风暴。
 *
 * 指数退避——第 n 次失败后需等待 `BASE × 2^(n-1)`，封顶 [MAX_DELAY_MS]：
 * 30s、1min、2min、4min… 30min。从第 7 次起固定 30 分钟。
 *
 * 只读 [PendingSessionUpload] 的既有字段，不引入新的持久化状态，进程重启后退避进度仍在。
 */
object UploadBackoffPolicy {

    /** 首次失败后的等待时长。 */
    const val BASE_DELAY_MS: Long = 30_000L

    /** 等待时长上限。长期离线时不能退到「几小时才重试一次」，否则恢复联网后迟迟传不上去。 */
    const val MAX_DELAY_MS: Long = 30 * 60_000L

    /** 位移上限：`2^20 × 30s` 已远超 [MAX_DELAY_MS]，再大只是徒增溢出风险。 */
    private const val MAX_SHIFT = 20

    /**
     * 第 [attemptCount] 次失败后、距上次尝试需等待的毫秒数。
     *
     * 从未失败过（`attemptCount <= 0`）返回 0，表示立即可尝试。
     */
    fun delayAfterFailure(attemptCount: Int): Long {
        if (attemptCount <= 0) return 0L
        val shift = minOf(attemptCount - 1, MAX_SHIFT)
        return minOf(BASE_DELAY_MS shl shift, MAX_DELAY_MS)
    }

    /**
     * 该条目现在是否到了可重试的时间。
     *
     * 从未尝试过（`lastAttemptAtEpochMillis == null`）视为立即可试。
     * 系统时钟回拨时 `now - lastAttempt` 为负，按「未到期」处理，避免疯狂重试。
     */
    fun isDue(item: PendingSessionUpload, nowEpochMillis: Long): Boolean {
        val lastAttempt = item.lastAttemptAtEpochMillis ?: return true
        return nowEpochMillis - lastAttempt >= delayAfterFailure(item.attemptCount)
    }
}
