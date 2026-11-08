package com.rehabtrainer.app.data.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** 预取时的网络条件；由平台层探测，策略层与缓存层不依赖 Android API。 */
data class MediaPrefetchConditions(
    val networkType: PrefetchNetworkType,
    val isMetered: Boolean,
    val dataSaverEnabled: Boolean
) {
    companion object {
        val UNMETERED_WIFI = MediaPrefetchConditions(
            networkType = PrefetchNetworkType.WIFI,
            isMetered = false,
            dataSaverEnabled = false
        )
    }
}

/** 单条下载的结果；区分失败、配额拒绝与地址非法，便于预取报告与排障。 */
enum class MediaCacheResult {
    STORED,
    ALREADY_CACHED,
    DOWNLOAD_FAILED,
    QUOTA_REJECTED,
    INVALID_URL
}

enum class PrefetchSkipReason { NETWORK, LOW_STORAGE }

data class MediaPrefetchReport(
    val skippedReason: PrefetchSkipReason? = null,
    val downloaded: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val skipped: List<String> = emptyList()
)

/**
 * 离线媒体缓存的核心：真实下载、索引登记、pinned + LRU 双上限、跨版本存活。
 *
 * 关键约束：
 * - 下载先写 `.part`，完整落盘并改名为正式文件后才登记索引；失败即删半成品。
 * - 清理只看容量与 LRU，绝不按 [MediaCacheEntry.appVersion] 删除。
 * - pinned 条目永不淘汰；pinned 自身超限时只记日志。
 * - 预取遵守 [MediaPrefetchPolicy] 的网络门槛与并发 2、连败 3 次停止；
 *   可用空间低于 [MediaCacheConfig.MIN_FREE_BYTES_FOR_PREFETCH] 时不开始、不继续。
 */
class MediaCacheManager(
    private val store: MediaCacheStore,
    private val downloader: MediaDownloader,
    private val quota: MediaCacheQuota,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val freeBytes: () -> Long = { Long.MAX_VALUE },
    private val conditions: () -> MediaPrefetchConditions = { MediaPrefetchConditions.UNMETERED_WIFI },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val log: (String) -> Unit = {}
) {

    private val lock = Any()
    private val entries = mutableListOf<MediaCacheEntry>()
    private val saveMutex = Mutex()
    private val prefetchMutex = Mutex()
    private val downloadLocks = mutableMapOf<String, Mutex>()
    private val inFlight = mutableSetOf<String>()
    private var persistJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        entries += store.load()
        // 档位变小时（如平板换手机）启动即按 LRU 收敛，pinned 仍不动。
        val startupPlan = MediaCacheEvictionPolicy.plan(entries, quota)
        // pinned 自身超限时淘汰非 pinned 也无收益，保留可播放缓存，只告警。
        if (startupPlan.fitsQuota && startupPlan.evicted.isNotEmpty()) {
            val evictedUrls = startupPlan.evicted.mapTo(HashSet()) { it.url }
            entries.removeAll { it.url in evictedUrls }
            runCatching { store.save(entries.toList()) }
                .onFailure { log("缓存索引写入失败：${it.message}") }
            startupPlan.evicted.forEach { runCatching { store.fileFor(it).delete() } }
        }
        if (MediaCacheEvictionPolicy.pinnedExceedsQuota(entries, quota)) {
            log("pinned 条目已超出缓存配额，仅告警不删除")
        }
    }

    /** 当前可播放的缓存地址集合（文件缺失的条目不计入）。 */
    fun cachedUrls(): Set<String> = synchronized(lock) {
        entries.filter { store.fileFor(it).isFile }.mapTo(HashSet()) { it.url }
    }

    fun entriesSnapshot(): List<MediaCacheEntry> = synchronized(lock) { entries.toList() }

    /**
     * 播放路径：命中索引即返回本地绝对路径并刷新 `lastUsedAt`。
     *
     * 只查内存索引，不在 Compose 组合线程做磁盘 stat；条目只有在文件改名成功后
     * 才会登记，外部删文件属异常场景，播放失败会走既有内置视频回退。
     */
    fun localPlaybackUrl(url: String): String? {
        var resolved: File? = null
        var changed = false
        synchronized(lock) {
            val index = entries.indexOfFirst { it.url == url }
            if (index >= 0) {
                val entry = entries[index]
                resolved = store.fileFor(entry)
                val now = clock()
                if (now - entry.lastUsedAt >= TOUCH_INTERVAL_MS || now < entry.lastUsedAt) {
                    entries[index] = entry.copy(lastUsedAt = now)
                    changed = true
                }
            }
        }
        if (changed) schedulePersist()
        return resolved?.absolutePath
    }

    /**
     * 播放未命中时的后台补缓存：仅在非计费网络、未开省流量且空间充足时执行。
     * 同一地址的重复请求由 [inFlight] 去重；真正下载前还有按 URL 的互斥锁兜底。
     */
    fun requestPlaybackDownload(url: String) {
        if (!isRemoteHttpUrl(url)) return
        synchronized(lock) {
            if (entries.any { it.url == url }) return
            if (!inFlight.add(url)) return
        }
        scope.launch {
            try {
                val current = conditions()
                if (!MediaPrefetchPolicy.isPrefetchAllowed(
                        current.networkType,
                        current.isMetered,
                        current.dataSaverEnabled
                    )
                ) {
                    return@launch
                }
                if (!MediaCacheConfig.canStartPrefetch(freeBytes())) return@launch
                ensureCached(url)
            } finally {
                synchronized(lock) { inFlight.remove(url) }
            }
        }
    }

    /** 下载并登记；已缓存时直接返回，不重复下载。按 URL 串行，避免同一文件并发写。 */
    suspend fun ensureCached(
        url: String,
        pinnedByPrescriptionId: String? = null
    ): MediaCacheResult = lockFor(url).withLock {
        ensureCachedLocked(url, pinnedByPrescriptionId)
    }

    /**
     * 预取当前处方（及可选的进行中训练）条目。
     *
     * 先落 pinned 语义（换处方会解除旧处方的 pinned），再按网络与空间门槛决定是否开跑。
     * 轮次之间串行：新处方不会与旧处方的在途下载交叉写 pinned。
     */
    suspend fun prefetch(
        prescriptionId: String?,
        prescriptionUrls: List<String>,
        activeSessionUrls: List<String> = emptyList(),
        conditions: MediaPrefetchConditions = this.conditions()
    ): MediaPrefetchReport = prefetchMutex.withLock {
        prefetchLocked(prescriptionId, prescriptionUrls, activeSessionUrls, conditions)
    }

    private suspend fun prefetchLocked(
        prescriptionId: String?,
        prescriptionUrls: List<String>,
        activeSessionUrls: List<String>,
        conditions: MediaPrefetchConditions
    ): MediaPrefetchReport {
        val prescription = prescriptionUrls.filter(::isRemoteHttpUrl).distinct()
        val active = activeSessionUrls.filter(::isRemoteHttpUrl).distinct()
        applyPrescriptionPins(prescriptionId, prescription.toSet())

        if (!MediaPrefetchPolicy.isPrefetchAllowed(
                conditions.networkType,
                conditions.isMetered,
                conditions.dataSaverEnabled
            )
        ) {
            return MediaPrefetchReport(skippedReason = PrefetchSkipReason.NETWORK)
        }
        if (!MediaCacheConfig.canStartPrefetch(freeBytes())) {
            return MediaPrefetchReport(skippedReason = PrefetchSkipReason.LOW_STORAGE)
        }

        val missing = MediaPrefetchPolicy.urlsToPrefetch(
            prescriptionUrls = prescription,
            activeSessionUrls = active,
            cachedUrls = cachedUrls()
        )
        val downloaded = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var consecutiveFailures = 0
        var index = 0

        while (index < missing.size && !MediaPrefetchPolicy.shouldStopRound(consecutiveFailures)) {
            if (!MediaCacheConfig.canStartPrefetch(freeBytes())) break
            val end = minOf(index + MediaPrefetchPolicy.MAX_CONCURRENCY, missing.size)
            val batch = missing.subList(index, end)
            val results = coroutineScope {
                batch.map { url ->
                    async {
                        url to ensureCached(
                            url = url,
                            pinnedByPrescriptionId = if (url in prescription) prescriptionId else null
                        )
                    }
                }.awaitAll()
            }
            for ((url, result) in results) {
                when (result) {
                    MediaCacheResult.STORED, MediaCacheResult.ALREADY_CACHED -> {
                        downloaded += url
                        consecutiveFailures = 0
                    }
                    MediaCacheResult.DOWNLOAD_FAILED -> {
                        failed += url
                        consecutiveFailures += 1
                    }
                    MediaCacheResult.QUOTA_REJECTED, MediaCacheResult.INVALID_URL -> skipped += url
                }
            }
            index += batch.size
        }
        if (index < missing.size) skipped += missing.drop(index)

        return MediaPrefetchReport(downloaded = downloaded, failed = failed, skipped = skipped)
    }

    /** 应用启动 8 秒后再预取，避开首屏请求；新轮次取消旧轮次。 */
    fun launchStartupPrefetch(
        prescriptionId: String?,
        prescriptionUrls: List<String>,
        activeSessionUrls: List<String> = emptyList()
    ) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            delay(MediaPrefetchPolicy.START_DELAY_MS)
            prefetch(prescriptionId, prescriptionUrls, activeSessionUrls)
        }
    }

    /** 处方变更后的预取；新轮次取消旧轮次，避免旧处方的在途下载回写 pinned。 */
    fun launchPrefetch(
        prescriptionId: String?,
        prescriptionUrls: List<String>,
        activeSessionUrls: List<String> = emptyList()
    ) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch { prefetch(prescriptionId, prescriptionUrls, activeSessionUrls) }
    }

    /** 立即把内存索引写盘；测试与进程退场前可用。 */
    suspend fun flush() = persistNow()

    private suspend fun ensureCachedLocked(
        url: String,
        pinnedByPrescriptionId: String?
    ): MediaCacheResult {
        if (!isRemoteHttpUrl(url)) return MediaCacheResult.INVALID_URL

        var pinChanged = false
        val existing = synchronized(lock) {
            val index = entries.indexOfFirst { it.url == url }
            if (index < 0) return@synchronized null
            val entry = entries[index]
            if (!store.fileFor(entry).isFile) {
                entries.removeAt(index)
                return@synchronized null
            }
            if (!pinnedByPrescriptionId.isNullOrBlank() &&
                entry.pinnedByPrescriptionId != pinnedByPrescriptionId
            ) {
                entries[index] = entry.copy(pinnedByPrescriptionId = pinnedByPrescriptionId)
                pinChanged = true
            }
            entry
        }
        if (existing != null) {
            if (pinChanged) persistNow()
            return MediaCacheResult.ALREADY_CACHED
        }

        val sha256 = sha256Hex(url)
        val pinValue = pinnedByPrescriptionId?.takeIf { it.isNotBlank() }

        // 廉价预判：pinned 已占满配额时普通条目不可能放下，直接拒绝，避免整段下载后删除。
        if (pinValue == null) {
            val pinnedBlocksEverything = synchronized(lock) {
                val pinned = entries.filter { it.isPinned }
                pinned.sumOf { it.bytes } >= quota.maxBytes || pinned.size >= quota.maxEntries
            }
            if (pinnedBlocksEverything) {
                log("pinned 已占满配额，跳过普通条目 key=$sha256")
                return MediaCacheResult.QUOTA_REJECTED
            }
        }

        val partial = store.partialFileFor(sha256)
        partial.delete()
        val bytes = try {
            downloader.download(url, partial, quota.maxBytes)
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            // 只记缓存键：媒体地址可能带签名/token 查询参数，不得进日志。
            log("媒体下载失败 key=$sha256：${e.message}")
            return MediaCacheResult.DOWNLOAD_FAILED
        }
        if (bytes <= 0) {
            partial.delete()
            return MediaCacheResult.DOWNLOAD_FAILED
        }

        val now = clock()
        val entry = MediaCacheEntry(
            url = url,
            sha256 = sha256,
            bytes = bytes,
            pinnedByPrescriptionId = pinValue,
            lastUsedAt = now,
            cachedAt = now,
            appVersion = appVersion
        )
        val target = store.fileFor(entry)

        var rejected = false
        var renameFailed = false
        var pinnedOverQuota = false
        synchronized(lock) {
            entries.removeAll { it.url == url }
            val plan = MediaCacheEvictionPolicy.plan(
                entries = entries,
                quota = quota,
                incomingBytes = bytes,
                incomingEntries = 1
            )
            when {
                !plan.fitsQuota && !entry.isPinned -> rejected = true
                else -> {
                    target.delete()
                    if (!partial.renameTo(target)) {
                        renameFailed = true
                    } else {
                        if (plan.fitsQuota) {
                            // pinned 自身超限时不淘汰其他条目：删了也回不到配额内。
                            val evictedUrls = plan.evicted.mapTo(HashSet()) { it.url }
                            entries.removeAll { it.url in evictedUrls }
                            // 与改名、登记同锁完成，避免删到并发重新下载的同名文件。
                            plan.evicted.forEach { runCatching { store.fileFor(it).delete() } }
                        }
                        entries += entry
                        pinnedOverQuota = MediaCacheEvictionPolicy.pinnedExceedsQuota(entries, quota)
                    }
                }
            }
        }
        if (rejected) {
            partial.delete()
            log("缓存配额不足，跳过 key=$sha256")
            return MediaCacheResult.QUOTA_REJECTED
        }
        if (renameFailed) {
            partial.delete()
            log("媒体缓存改名失败 key=$sha256")
            return MediaCacheResult.DOWNLOAD_FAILED
        }
        if (pinnedOverQuota) log("pinned 条目已超出缓存配额，仅告警不删除")
        persistNow()
        return MediaCacheResult.STORED
    }

    private suspend fun applyPrescriptionPins(prescriptionId: String?, prescriptionUrls: Set<String>) {
        if (prescriptionId.isNullOrBlank()) return
        var changed = false
        synchronized(lock) {
            for (index in entries.indices) {
                val entry = entries[index]
                val desired = when {
                    entry.url in prescriptionUrls -> prescriptionId
                    entry.pinnedByPrescriptionId != null -> null
                    else -> null
                }
                if (desired != entry.pinnedByPrescriptionId) {
                    entries[index] = entry.copy(pinnedByPrescriptionId = desired)
                    changed = true
                }
            }
        }
        if (changed) persistNow()
    }

    private suspend fun persistNow() {
        saveMutex.withLock {
            val snapshot = synchronized(lock) { entries.toList() }
            withContext(Dispatchers.IO) {
                runCatching { store.save(snapshot) }
                    .onFailure { log("缓存索引写入失败：${it.message}") }
            }
        }
    }

    /** 播放命中会频繁刷新 `lastUsedAt`，合并成一次延迟写盘。 */
    private fun schedulePersist() {
        synchronized(lock) {
            if (persistJob?.isActive == true) return
            persistJob = scope.launch {
                delay(PERSIST_DEBOUNCE_MS)
                persistNow()
            }
        }
    }

    private fun lockFor(url: String): Mutex = synchronized(lock) {
        downloadLocks.getOrPut(url) { Mutex() }
    }

    companion object {
        const val PERSIST_DEBOUNCE_MS: Long = 500L

        /** 训练页重组合很频繁；`lastUsedAt` 最多每分钟刷新一次，避免频繁写盘。 */
        const val TOUCH_INTERVAL_MS: Long = 60_000L
    }
}
