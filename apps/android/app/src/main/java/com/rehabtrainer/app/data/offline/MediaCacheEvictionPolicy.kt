package com.rehabtrainer.app.data.offline

/** 一次淘汰计划：要删除的条目，以及淘汰后是否已回到配额内。 */
data class MediaEvictionPlan(
    val evicted: List<MediaCacheEntry>,
    val fitsQuota: Boolean
)

/**
 * 容量治理的纯逻辑：pinned + LRU 双上限。
 *
 * - 顺序：非 pinned 条目按 `lastUsedAt` 升序（最久未用优先），同值再按 `cachedAt`、`url` 稳定排序。
 * - pinned 条目永不进入淘汰列表；若 pinned 本身已超限，[MediaEvictionPlan.fitsQuota] 为 false，
 *   调用方只告警，不删除。
 */
object MediaCacheEvictionPolicy {

    fun totalBytes(entries: List<MediaCacheEntry>): Long = entries.sumOf { it.bytes }

    /**
     * 计算为容纳 `incomingBytes/incomingEntries` 需要淘汰哪些现有条目。
     * 不修改入参；[MediaEvictionPlan.evicted] 与 `entries` 中的对象为同一批实例。
     */
    fun plan(
        entries: List<MediaCacheEntry>,
        quota: MediaCacheQuota,
        incomingBytes: Long = 0,
        incomingEntries: Int = 0
    ): MediaEvictionPlan {
        var bytes = totalBytes(entries) + incomingBytes
        var count = entries.size + incomingEntries
        val evicted = mutableListOf<MediaCacheEntry>()

        val candidates = entries
            .asSequence()
            .filterNot { it.isPinned }
            .sortedWith(
                compareBy<MediaCacheEntry> { it.lastUsedAt }
                    .thenBy { it.cachedAt }
                    .thenBy { it.url }
            )

        for (candidate in candidates) {
            if (bytes <= quota.maxBytes && count <= quota.maxEntries) break
            evicted += candidate
            bytes -= candidate.bytes
            count -= 1
        }

        return MediaEvictionPlan(
            evicted = evicted,
            fitsQuota = bytes <= quota.maxBytes && count <= quota.maxEntries
        )
    }

    /** pinned 条目本身超限时为 true：只告警，不删除。 */
    fun pinnedExceedsQuota(entries: List<MediaCacheEntry>, quota: MediaCacheQuota): Boolean {
        val pinned = entries.filter { it.isPinned }
        return pinned.sumOf { it.bytes } > quota.maxBytes || pinned.size > quota.maxEntries
    }
}
