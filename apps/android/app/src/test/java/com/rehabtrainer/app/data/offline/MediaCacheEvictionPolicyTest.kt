package com.rehabtrainer.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCacheEvictionPolicyTest {

    private fun entry(
        url: String,
        bytes: Long,
        lastUsedAt: Long,
        cachedAt: Long = lastUsedAt,
        pinnedBy: String? = null
    ) = MediaCacheEntry(
        url = url,
        sha256 = url,
        bytes = bytes,
        pinnedByPrescriptionId = pinnedBy,
        lastUsedAt = lastUsedAt,
        cachedAt = cachedAt,
        appVersion = "0.1.0"
    )

    @Test
    fun 超字节上限时最久未用先淘汰() {
        val entries = listOf(
            entry("a", 100, lastUsedAt = 1),
            entry("b", 100, lastUsedAt = 2),
            entry("c", 100, lastUsedAt = 3)
        )

        val plan = MediaCacheEvictionPolicy.plan(entries, MediaCacheQuota(250, 10))

        assertEquals(listOf("a"), plan.evicted.map { it.url })
        assertTrue(plan.fitsQuota)
    }

    @Test
    fun 超条目上限时按最近使用淘汰到上限内() {
        val entries = listOf(
            entry("a", 1, lastUsedAt = 3),
            entry("b", 1, lastUsedAt = 1),
            entry("c", 1, lastUsedAt = 2)
        )

        val plan = MediaCacheEvictionPolicy.plan(entries, MediaCacheQuota(10_000, 2))

        assertEquals(listOf("b"), plan.evicted.map { it.url })
        assertTrue(plan.fitsQuota)
    }

    @Test
    fun pinned条目不进入淘汰列表() {
        val entries = listOf(
            entry("pinned", 100, lastUsedAt = 1, pinnedBy = "p1"),
            entry("b", 100, lastUsedAt = 2),
            entry("c", 100, lastUsedAt = 3)
        )

        val plan = MediaCacheEvictionPolicy.plan(entries, MediaCacheQuota(150, 10))

        assertEquals(listOf("b", "c"), plan.evicted.map { it.url })
        assertTrue(plan.fitsQuota)
    }

    @Test
    fun 仅剩pinned仍超限时标记为放不下() {
        val entries = listOf(
            entry("a", 100, lastUsedAt = 1, pinnedBy = "p1"),
            entry("b", 100, lastUsedAt = 2, pinnedBy = "p1")
        )

        val plan = MediaCacheEvictionPolicy.plan(entries, MediaCacheQuota(150, 10))

        assertTrue(plan.evicted.isEmpty())
        assertFalse(plan.fitsQuota)
        assertTrue(MediaCacheEvictionPolicy.pinnedExceedsQuota(entries, MediaCacheQuota(150, 10)))
    }

    @Test
    fun 相同最近使用时间按缓存时间稳定排序() {
        val entries = listOf(
            entry("newer", 100, lastUsedAt = 5, cachedAt = 2),
            entry("older", 100, lastUsedAt = 5, cachedAt = 1)
        )

        val plan = MediaCacheEvictionPolicy.plan(entries, MediaCacheQuota(100, 10))

        assertEquals(listOf("older"), plan.evicted.map { it.url })
    }

    @Test
    fun 新条目计入字节与条目上限() {
        val entries = listOf(entry("a", 100, lastUsedAt = 1))

        val plan = MediaCacheEvictionPolicy.plan(
            entries,
            MediaCacheQuota(150, 10),
            incomingBytes = 100,
            incomingEntries = 1
        )

        assertEquals(listOf("a"), plan.evicted.map { it.url })
        assertTrue(plan.fitsQuota)
    }

    @Test
    fun 汇总字节数() {
        assertEquals(
            250L,
            MediaCacheEvictionPolicy.totalBytes(
                listOf(entry("a", 100, 1), entry("b", 150, 2))
            )
        )
    }
}
