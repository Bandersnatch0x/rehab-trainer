package com.rehabtrainer.app.data.offline

/** 预取判定所需的网络类型；由平台层探测后传入，策略层不依赖 Android API。 */
enum class PrefetchNetworkType {
    WIFI,
    CELLULAR,

    /** 2G 等窄带：即使不计费也跳过。 */
    CELLULAR_2G,
    OFFLINE,
    OTHER
}

/**
 * 离线媒体预取策略（T3，接口与规则骨架）。
 *
 * 只决定「预取哪些、能否预取、何时停止本轮」，不负责下载、磁盘配额或淘汰；
 * 真正的下载与缓存层另立票实现（TODO T3：下载执行 + 双上限 LRU）。
 */
object MediaPrefetchPolicy {

    /** 预取并发上限。 */
    const val MAX_CONCURRENCY = 2

    /** 连续失败达到此数即结束本轮，等待下次触发。 */
    const val MAX_CONSECUTIVE_FAILURES = 3

    /** 启动稳定后延迟再开始，避免与首屏请求争抢。 */
    const val START_DELAY_MS = 8_000L

    /**
     * 预取集合 = 当前处方条目 + 进行中训练条目。
     *
     * 不预取动作库全量；结果稳定去重，并跳过空地址与已缓存条目。
     * 处方在前，保证离线时优先覆盖当前处方。
     */
    fun urlsToPrefetch(
        prescriptionUrls: List<String>,
        activeSessionUrls: List<String>,
        cachedUrls: Set<String>
    ): List<String> {
        val seen = mutableSetOf<String>()
        return (prescriptionUrls + activeSessionUrls)
            .asSequence()
            .filter { it.isNotBlank() }
            .filter { seen.add(it) }
            .filterNot { it in cachedUrls }
            .toList()
    }

    /** 计费网络、2G 窄带、省流量模式或离线时一律不预取。 */
    fun isPrefetchAllowed(
        networkType: PrefetchNetworkType,
        isMetered: Boolean,
        dataSaverEnabled: Boolean
    ): Boolean = when {
        dataSaverEnabled -> false
        isMetered -> false
        networkType == PrefetchNetworkType.OFFLINE -> false
        networkType == PrefetchNetworkType.CELLULAR_2G -> false
        else -> true
    }

    /** 连续失败达到上限即结束本轮，已成功的条目不受影响。 */
    fun shouldStopRound(consecutiveFailures: Int): Boolean =
        consecutiveFailures >= MAX_CONSECUTIVE_FAILURES
}
