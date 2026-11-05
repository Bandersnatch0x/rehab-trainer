package com.rehabtrainer.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPrefetchPolicyTest {

    @Test
    fun prefetchSetIsPrescriptionThenSessionDeduplicated() {
        val result = MediaPrefetchPolicy.urlsToPrefetch(
            prescriptionUrls = listOf("a.mp4", "b.mp4", "a.mp4"),
            activeSessionUrls = listOf("b.mp4", "c.mp4"),
            cachedUrls = emptySet()
        )

        assertEquals(listOf("a.mp4", "b.mp4", "c.mp4"), result)
    }

    @Test
    fun prefetchSetSkipsCachedAndBlankUrls() {
        val result = MediaPrefetchPolicy.urlsToPrefetch(
            prescriptionUrls = listOf("", "a.mp4"),
            activeSessionUrls = listOf("cached.mp4", "new.mp4"),
            cachedUrls = setOf("cached.mp4")
        )

        assertEquals(listOf("a.mp4", "new.mp4"), result)
    }

    @Test
    fun dataSaverBlocksPrefetch() {
        assertFalse(
            MediaPrefetchPolicy.isPrefetchAllowed(
                PrefetchNetworkType.WIFI,
                isMetered = false,
                dataSaverEnabled = true
            )
        )
    }

    @Test
    fun meteredNetworkBlocksPrefetch() {
        assertFalse(
            MediaPrefetchPolicy.isPrefetchAllowed(
                PrefetchNetworkType.WIFI,
                isMetered = true,
                dataSaverEnabled = false
            )
        )
    }

    @Test
    fun narrowbandBlocksPrefetch() {
        assertFalse(
            MediaPrefetchPolicy.isPrefetchAllowed(
                PrefetchNetworkType.CELLULAR_2G,
                isMetered = false,
                dataSaverEnabled = false
            )
        )
    }

    @Test
    fun offlineBlocksPrefetch() {
        assertFalse(
            MediaPrefetchPolicy.isPrefetchAllowed(
                PrefetchNetworkType.OFFLINE,
                isMetered = false,
                dataSaverEnabled = false
            )
        )
    }

    @Test
    fun unmeteredWifiAndCellularAreAllowed() {
        assertTrue(
            MediaPrefetchPolicy.isPrefetchAllowed(
                PrefetchNetworkType.WIFI,
                isMetered = false,
                dataSaverEnabled = false
            )
        )
        assertTrue(
            MediaPrefetchPolicy.isPrefetchAllowed(
                PrefetchNetworkType.CELLULAR,
                isMetered = false,
                dataSaverEnabled = false
            )
        )
    }

    @Test
    fun thirdConsecutiveFailureStopsRound() {
        assertFalse(MediaPrefetchPolicy.shouldStopRound(0))
        assertFalse(MediaPrefetchPolicy.shouldStopRound(2))
        assertTrue(MediaPrefetchPolicy.shouldStopRound(3))
        assertTrue(MediaPrefetchPolicy.shouldStopRound(4))
    }
}
