package com.rehabtrainer.app.data.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections

class MediaCacheManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val logs = Collections.synchronizedList(mutableListOf<String>())

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun newManager(
        downloader: MediaDownloader = FakeDownloader(),
        quota: MediaCacheQuota = MediaCacheQuota(10_000, 100),
        appVersion: String = "0.1.0",
        directory: File = folder.newFolder(),
        clock: () -> Long = { 1_000L },
        freeBytes: () -> Long = { Long.MAX_VALUE },
        log: (String) -> Unit = { logs += it }
    ): MediaCacheManager {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scopes += scope
        return MediaCacheManager(
            store = MediaCacheStore(directory),
            downloader = downloader,
            quota = quota,
            appVersion = appVersion,
            clock = clock,
            freeBytes = freeBytes,
            conditions = { MediaPrefetchConditions.UNMETERED_WIFI },
            scope = scope,
            log = log
        )
    }

    @Test
    fun 命中缓存返回本地路径并刷新最近使用时间() {
        var now = 1_000L
        val manager = newManager(clock = { now })

        runBlocking { assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_A)) }
        now += MediaCacheManager.TOUCH_INTERVAL_MS
        val path = manager.localPlaybackUrl(URL_A)

        assertNotNull(path)
        assertTrue(File(path!!).isFile)
        assertEquals(1_000L + MediaCacheManager.TOUCH_INTERVAL_MS, manager.entriesSnapshot().single().lastUsedAt)
    }

    @Test
    fun 已缓存条目不再重复下载() {
        val downloader = FakeDownloader()
        val manager = newManager(downloader = downloader)

        runBlocking {
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_A))
            assertEquals(MediaCacheResult.ALREADY_CACHED, manager.ensureCached(URL_A))
        }

        assertEquals(listOf(URL_A), downloader.calls)
    }

    @Test
    fun 下载失败不登记且不留半成品() {
        val directory = folder.newFolder()
        val manager = newManager(
            downloader = FakeDownloader(failUrls = setOf(URL_A)),
            directory = directory
        )

        runBlocking { assertEquals(MediaCacheResult.DOWNLOAD_FAILED, manager.ensureCached(URL_A)) }

        assertTrue(manager.entriesSnapshot().isEmpty())
        assertEquals(0, directory.listFiles().orEmpty().count { it.name.endsWith(".part") })
        assertFalse(File(directory, sha256Hex(URL_A) + ".mp4").exists())
    }

    @Test
    fun 下载失败日志不含签名地址() {
        val signedUrl = "https://media.example/v.mp4?token=super-secret"
        val manager = newManager(downloader = FakeDownloader(failUrls = setOf(signedUrl)))

        runBlocking { assertEquals(MediaCacheResult.DOWNLOAD_FAILED, manager.ensureCached(signedUrl)) }

        assertTrue(logs.isNotEmpty())
        assertTrue(logs.none { it.contains("super-secret") || it.contains("media.example") })
    }

    @Test
    fun 超过容量按LRU淘汰且删除文件() {
        val directory = folder.newFolder()
        var now = 1_000L
        val manager = newManager(
            quota = MediaCacheQuota(250, 10),
            directory = directory,
            clock = { now }
        )

        runBlocking {
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_A))
            now++
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_B))
            now++
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_C))
        }

        assertEquals(setOf(URL_B, URL_C), manager.cachedUrls())
        assertFalse(File(directory, sha256Hex(URL_A) + ".mp4").exists())
    }

    @Test
    fun pinned条目不参与LRU淘汰() {
        var now = 1_000L
        val manager = newManager(quota = MediaCacheQuota(250, 10), clock = { now })

        runBlocking {
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_A, "p1"))
            now++
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_B))
            now++
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_C))
        }

        val entries = manager.entriesSnapshot()
        assertEquals(setOf(URL_A, URL_C), entries.map { it.url }.toSet())
        assertTrue(entries.first { it.url == URL_A }.isPinned)
    }

    @Test
    fun pinned超配额只告警并拒绝新普通条目() {
        val directory = folder.newFolder()
        val manager = newManager(quota = MediaCacheQuota(150, 10), directory = directory)

        runBlocking {
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_A, "p1"))
            assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_B, "p2"))
            assertEquals(MediaCacheResult.QUOTA_REJECTED, manager.ensureCached(URL_C))
        }

        assertEquals(setOf(URL_A, URL_B), manager.cachedUrls())
        assertFalse(File(directory, sha256Hex(URL_C) + ".mp4").exists())
        assertTrue(logs.any { it.contains("pinned") })
    }

    @Test
    fun pinned已占满配额时普通条目不发起下载() {
        val downloader = FakeDownloader()
        val manager = newManager(
            downloader = downloader,
            quota = MediaCacheQuota(10_000, 1)
        )
        runBlocking { assertEquals(MediaCacheResult.STORED, manager.ensureCached(URL_A, "p1")) }
        downloader.calls.clear()

        val result = runBlocking { manager.ensureCached(URL_B) }

        assertEquals(MediaCacheResult.QUOTA_REJECTED, result)
        assertTrue(downloader.calls.isEmpty())
    }

    @Test
    fun pinned自身超配额时启动与新增pinned都不删非pinned() {
        val sizes = mapOf(URL_A to 200L, URL_B to 100L, URL_C to 100L, URL_D to 50L)
        val downloader = FakeDownloader(sizeFor = { url -> sizes.getValue(url) })
        val directory = folder.newFolder()
        val wide = newManager(
            downloader = downloader,
            quota = MediaCacheQuota(10_000, 100),
            directory = directory
        )
        runBlocking {
            wide.ensureCached(URL_A, "p1")
            wide.ensureCached(URL_B, "p1")
            wide.ensureCached(URL_C)
        }

        // pinned 自身 300 字节已超 250：启动不应用会删掉 C 的淘汰计划。
        val narrow = newManager(
            downloader = downloader,
            quota = MediaCacheQuota(250, 10),
            directory = directory
        )
        assertTrue(narrow.cachedUrls().containsAll(listOf(URL_A, URL_B, URL_C)))

        // 再新增 pinned 仍超限：只告警，不淘汰 C。
        val result = runBlocking { narrow.ensureCached(URL_D, "p2") }

        assertEquals(MediaCacheResult.STORED, result)
        assertTrue(narrow.cachedUrls().containsAll(listOf(URL_A, URL_B, URL_C, URL_D)))
        assertTrue(File(directory, sha256Hex(URL_C) + ".mp4").exists())
        assertTrue(logs.any { it.contains("pinned") })
    }

    @Test
    fun 缓存跨App版本存活() {
        val directory = folder.newFolder()
        val first = newManager(directory = directory, appVersion = "0.1.0")
        runBlocking { first.ensureCached(URL_A) }

        val upgraded = newManager(directory = directory, appVersion = "0.2.0")

        assertEquals(setOf(URL_A), upgraded.cachedUrls())
        assertNotNull(upgraded.localPlaybackUrl(URL_A))
        assertEquals("0.1.0", upgraded.entriesSnapshot().single().appVersion)
    }

    @Test
    fun 预取只下载缺失条目且并发不超过2() {
        val downloader = FakeDownloader(delayMillis = 20)
        val manager = newManager(downloader = downloader)
        runBlocking { manager.ensureCached(URL_A) }
        downloader.calls.clear()

        val report = runBlocking {
            manager.prefetch("p1", listOf(URL_A, URL_B, URL_C, URL_D))
        }

        assertEquals(setOf(URL_B, URL_C, URL_D), downloader.calls.toSet())
        assertTrue(downloader.maxActive <= MediaPrefetchPolicy.MAX_CONCURRENCY)
        assertEquals(setOf(URL_B, URL_C, URL_D), report.downloaded.toSet())
        assertTrue(report.failed.isEmpty())
        assertTrue(manager.entriesSnapshot().all { it.pinnedByPrescriptionId == "p1" })
    }

    @Test
    fun 可用空间不足时停止预取() {
        val downloader = FakeDownloader()
        val manager = newManager(
            downloader = downloader,
            freeBytes = { 900L * 1024 * 1024 }
        )

        val report = runBlocking { manager.prefetch("p1", listOf(URL_A)) }

        assertEquals(PrefetchSkipReason.LOW_STORAGE, report.skippedReason)
        assertTrue(downloader.calls.isEmpty())
    }

    @Test
    fun 预取中途空间不足则跳过剩余且不重复记录() {
        var freeCalls = 0
        val manager = newManager(
            downloader = FakeDownloader(),
            freeBytes = {
                freeCalls++
                if (freeCalls <= 2) Long.MAX_VALUE else 0L
            }
        )

        val report = runBlocking {
            manager.prefetch("p1", listOf(URL_A, URL_B, URL_C, URL_D))
        }

        assertEquals(listOf(URL_A, URL_B), report.downloaded)
        assertEquals(listOf(URL_C, URL_D), report.skipped)
        assertEquals(report.skipped.size, report.skipped.toSet().size)
    }

    @Test
    fun 计费网络与省流量模式不预取() {
        val downloader = FakeDownloader()
        val manager = newManager(downloader = downloader)

        val metered = runBlocking {
            manager.prefetch(
                "p1",
                listOf(URL_A),
                conditions = MediaPrefetchConditions(PrefetchNetworkType.WIFI, true, false)
            )
        }
        val dataSaver = runBlocking {
            manager.prefetch(
                "p1",
                listOf(URL_A),
                conditions = MediaPrefetchConditions(PrefetchNetworkType.WIFI, false, true)
            )
        }
        val offline = runBlocking {
            manager.prefetch(
                "p1",
                listOf(URL_A),
                conditions = MediaPrefetchConditions(PrefetchNetworkType.OFFLINE, false, false)
            )
        }

        assertEquals(PrefetchSkipReason.NETWORK, metered.skippedReason)
        assertEquals(PrefetchSkipReason.NETWORK, dataSaver.skippedReason)
        assertEquals(PrefetchSkipReason.NETWORK, offline.skippedReason)
        assertTrue(downloader.calls.isEmpty())
    }

    @Test
    fun 连续失败后停止本轮并保留未尝试条目() {
        val urls = (1..10).map { "https://media.example/fail-$it.mp4" }
        val downloader = FakeDownloader(failUrls = urls.toSet())
        val manager = newManager(downloader = downloader)

        val report = runBlocking { manager.prefetch("p1", urls) }

        assertEquals(4, downloader.calls.size)
        assertEquals(4, report.failed.size)
        assertEquals(6, report.skipped.size)
        assertTrue(manager.entriesSnapshot().isEmpty())
    }

    @Test
    fun 启动时按新配额LRU收敛且不动pinned() {
        val directory = folder.newFolder()
        var now = 1_000L
        val wide = newManager(
            quota = MediaCacheQuota(10_000, 100),
            directory = directory,
            clock = { now }
        )
        runBlocking {
            wide.ensureCached(URL_A, "p1")
            now++
            wide.ensureCached(URL_B)
            now++
            wide.ensureCached(URL_C)
        }

        val narrow = newManager(
            quota = MediaCacheQuota(250, 10),
            directory = directory,
            clock = { now }
        )

        assertEquals(setOf(URL_A, URL_C), narrow.cachedUrls())
        assertTrue(narrow.entriesSnapshot().first { it.url == URL_A }.isPinned)
        assertFalse(File(directory, sha256Hex(URL_B) + ".mp4").exists())
    }

    @Test
    fun 换处方解除旧处方pinned() {
        val manager = newManager()

        runBlocking {
            manager.prefetch("p1", listOf(URL_A))
            assertTrue(manager.entriesSnapshot().single().isPinned)

            manager.prefetch("p2", emptyList())
            assertNull(manager.entriesSnapshot().single().pinnedByPrescriptionId)
        }
    }

    @Test
    fun 文件被外部删除后下次启动加载会清理索引() {
        val directory = folder.newFolder()
        val manager = newManager(directory = directory)
        runBlocking { manager.ensureCached(URL_A) }
        File(directory, sha256Hex(URL_A) + ".mp4").delete()

        runBlocking { manager.flush() }
        val reloaded = newManager(directory = directory)

        assertTrue(reloaded.cachedUrls().isEmpty())
    }

    private class FakeDownloader(
        private val failUrls: Set<String> = emptySet(),
        private val sizeFor: (String) -> Long = { 100L },
        private val delayMillis: Long = 0
    ) : MediaDownloader {

        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        @Volatile
        var maxActive: Int = 0
        private var active: Int = 0

        override suspend fun download(url: String, target: File, maxBytes: Long): Long {
            calls += url
            synchronized(this) {
                active++
                maxActive = maxOf(maxActive, active)
            }
            try {
                if (delayMillis > 0) delay(delayMillis)
                if (url in failUrls) throw MediaDownloadException("fake failure")
                val bytes = sizeFor(url)
                if (bytes > maxBytes) throw MediaDownloadException("too big")
                target.parentFile?.mkdirs()
                target.writeBytes(ByteArray(bytes.toInt()))
                return bytes
            } finally {
                synchronized(this) { active-- }
            }
        }
    }

    private companion object {
        const val URL_A = "https://media.example/a.mp4"
        const val URL_B = "https://media.example/b.mp4"
        const val URL_C = "https://media.example/c.mp4"
        const val URL_D = "https://media.example/d.mp4"
    }
}
