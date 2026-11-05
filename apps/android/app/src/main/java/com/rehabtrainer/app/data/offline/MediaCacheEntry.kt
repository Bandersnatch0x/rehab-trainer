package com.rehabtrainer.app.data.offline

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * 一条已完成下载的缓存记录；只有文件落盘并改名成功后才会登记。
 *
 * [appVersion] 仅用于排障与统计，**不参与清理**：缓存跨 App 版本存活，
 * 清理只看容量与 LRU。
 */
@Serializable
data class MediaCacheEntry(
    val url: String,
    val sha256: String,
    val bytes: Long,
    val pinnedByPrescriptionId: String? = null,
    val lastUsedAt: Long,
    val cachedAt: Long,
    val appVersion: String
) {

    val isPinned: Boolean
        get() = !pinnedByPrescriptionId.isNullOrBlank()

    /** 文件名使用 URL 摘要，不落原始地址，避免路径分隔符与超长文件名。 */
    fun fileName(): String = sha256 + videoExtension(url)
}

/** 缓存键：URL 的 SHA-256，小写十六进制。 */
internal fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}

/**
 * 从地址推断容器扩展名，仅用于提高设备解码兼容性；
 * 未知或伪造的扩展名一律回落到 `.mp4`。
 */
internal fun videoExtension(url: String): String {
    val path = url.substringBefore('#').substringBefore('?')
    val name = path.substringAfterLast('/', path)
    val extension = name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "mp4", "m4v", "webm", "mkv", "mov", "3gp" -> ".$extension"
        else -> ".mp4"
    }
}

internal fun isRemoteHttpUrl(url: String): Boolean =
    url.startsWith("https://") || url.startsWith("http://")
