package com.rehabtrainer.app.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.rehabtrainer.app.BuildConfig
import com.rehabtrainer.app.core.training.CachedMediaPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * 离线媒体缓存的应用级入口。
 *
 * 在 `MainActivity` 启动时调用 [install]：读取本机档位配额、装载索引，
 * 并把播放层的缓存钩子接到 [MediaCacheManager]。
 * 未安装时播放层保持“只用远端”的既有行为。
 */
object MediaCache {

    private const val TAG = "MediaCache"
    private const val CACHE_DIR_NAME = "media-cache"

    @Volatile
    private var manager: MediaCacheManager? = null

    fun install(context: Context) {
        if (manager != null) return
        synchronized(this) {
            if (manager != null) return
            val appContext = context.applicationContext
            val configuration = appContext.resources.configuration
            val quota = MediaCacheConfig.quotaForSmallestWidth(configuration.smallestScreenWidthDp)
            val created = MediaCacheManager(
                store = MediaCacheStore(File(appContext.filesDir, CACHE_DIR_NAME)),
                downloader = HttpMediaDownloader(),
                quota = quota,
                appVersion = BuildConfig.VERSION_NAME,
                freeBytes = { appContext.filesDir.usableSpace },
                conditions = { readConditions(appContext) },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                log = { message -> Log.w(TAG, message) }
            )
            manager = created
            CachedMediaPlayback.resolveLocalUrl = { url ->
                if (isRemoteHttpUrl(url)) created.localPlaybackUrl(url) else null
            }
            CachedMediaPlayback.onPlaybackMiss = { url ->
                if (isRemoteHttpUrl(url)) created.requestPlaybackDownload(url)
            }
        }
    }

    /** 应用启动 8 秒后的预取；当前处方地址由处方数据层传入。 */
    fun requestStartupPrefetch(
        prescriptionId: String?,
        prescriptionUrls: List<String>,
        activeSessionUrls: List<String> = emptyList()
    ) {
        manager?.launchStartupPrefetch(prescriptionId, prescriptionUrls, activeSessionUrls)
    }

    /** 处方变更后的预取；只传当前处方（可含进行中训练条目）。 */
    fun requestPrescriptionPrefetch(
        prescriptionId: String?,
        prescriptionUrls: List<String>,
        activeSessionUrls: List<String> = emptyList()
    ) {
        manager?.launchPrefetch(prescriptionId, prescriptionUrls, activeSessionUrls)
    }

    private fun readConditions(context: Context): MediaPrefetchConditions {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return OFFLINE_CONDITIONS
        val network = connectivity.activeNetwork ?: return OFFLINE_CONDITIONS
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: return OFFLINE_CONDITIONS

        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val dataSaver = connectivity.restrictBackgroundStatus ==
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> PrefetchNetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                if (capabilities.linkDownstreamBandwidthKbps in 1 until NARROWBAND_KBPS) {
                    PrefetchNetworkType.CELLULAR_2G
                } else {
                    PrefetchNetworkType.CELLULAR
                }
            else -> PrefetchNetworkType.OTHER
        }
        return MediaPrefetchConditions(type, metered, dataSaver)
    }

    private val OFFLINE_CONDITIONS = MediaPrefetchConditions(
        networkType = PrefetchNetworkType.OFFLINE,
        isMetered = true,
        dataSaverEnabled = true
    )

    /** 窄带判定：下行带宽低于 100 kbps 视为 2G 档，不额外申请电话状态权限。 */
    private const val NARROWBAND_KBPS = 100
}
