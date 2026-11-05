package com.rehabtrainer.app.data.offline

/** 单机媒体缓存上限：字节数与条目数双上限，超限先按 LRU 淘汰非 pinned 条目。 */
data class MediaCacheQuota(
    val maxBytes: Long,
    val maxEntries: Int
) {
    init {
        require(maxBytes > 0) { "maxBytes 必须为正数" }
        require(maxEntries > 0) { "maxEntries 必须为正数" }
    }
}

/**
 * 离线媒体缓存配额与预取门槛（D27 默认值）。
 *
 * 手机档按 `smallestScreenWidthDp < 600` 判定，取平板档的 1/3；
 * 数值集中在此处，UI 与下载层只读取，不各自硬编码。
 */
object MediaCacheConfig {

    const val PHONE_MAX_BYTES: Long = 500L * 1024 * 1024
    const val PHONE_MAX_ENTRIES: Int = 10

    const val TABLET_MAX_BYTES: Long = 1536L * 1024 * 1024
    const val TABLET_MAX_ENTRIES: Int = 30

    /** 可用空间低于此值时停止本轮预取，不删除已有缓存。 */
    const val MIN_FREE_BYTES_FOR_PREFETCH: Long = 1024L * 1024 * 1024

    /** sw600dp 及以上视为平板档。 */
    const val TABLET_SMALLEST_WIDTH_DP: Int = 600

    val PHONE_QUOTA: MediaCacheQuota = MediaCacheQuota(PHONE_MAX_BYTES, PHONE_MAX_ENTRIES)
    val TABLET_QUOTA: MediaCacheQuota = MediaCacheQuota(TABLET_MAX_BYTES, TABLET_MAX_ENTRIES)

    fun quotaFor(isTablet: Boolean): MediaCacheQuota = if (isTablet) TABLET_QUOTA else PHONE_QUOTA

    fun quotaForSmallestWidth(smallestScreenWidthDp: Int): MediaCacheQuota =
        quotaFor(smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP)

    /** 可用空间为 0 或负值（读取失败）时同样视为不足。 */
    fun canStartPrefetch(availableBytes: Long): Boolean =
        availableBytes >= MIN_FREE_BYTES_FOR_PREFETCH
}
