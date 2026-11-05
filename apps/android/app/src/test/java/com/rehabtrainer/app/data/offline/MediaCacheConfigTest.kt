package com.rehabtrainer.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCacheConfigTest {

    @Test
    fun 手机档配额为500MB十条() {
        val quota = MediaCacheConfig.quotaForSmallestWidth(599)

        assertEquals(500L * 1024 * 1024, quota.maxBytes)
        assertEquals(10, quota.maxEntries)
    }

    @Test
    fun 平板档配额为1点5GB三十条() {
        val quota = MediaCacheConfig.quotaForSmallestWidth(600)

        assertEquals(1536L * 1024 * 1024, quota.maxBytes)
        assertEquals(30, quota.maxEntries)
    }

    @Test
    fun 可用空间低于1GB停止预取() {
        assertFalse(MediaCacheConfig.canStartPrefetch(1024L * 1024 * 1024 - 1))
        assertFalse(MediaCacheConfig.canStartPrefetch(0))
    }

    @Test
    fun 可用空间恰为1GB放行() {
        assertTrue(MediaCacheConfig.canStartPrefetch(1024L * 1024 * 1024))
    }
}
