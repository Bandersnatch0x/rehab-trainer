package com.rehabtrainer.app.core.training

import android.content.Context
import java.security.MessageDigest

/**
 * 训练记录随附的模型与指标版本信息。
 *
 * 模型 SHA-256 由 APK 内的模型文件实际计算（不是写死的占位值），
 * 首次调用较慢，应在后台预热；失败时回落为 `unavailable`，由服务端存疑机制兜底。
 */
object ModelFingerprint {

    const val MODEL_VERSION = "pose_landmarker_lite"
    const val METRIC_ALGORITHM_VERSION = "metrics-0.1.0"
    const val THRESHOLD_CONFIG_VERSION = "literature-2026.11"
    const val UNKNOWN_SHA256 = "unavailable"

    @Volatile
    private var cached: String? = null

    fun sha256(context: Context): String {
        cached?.let { return it }
        return runCatching {
            // 流式计算，避免把整个模型读进堆；失败不写缓存，下次还能重试。
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open(MODEL_ASSET).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrElse { return UNKNOWN_SHA256 }.also { cached = it }
    }

    private const val MODEL_ASSET = "pose_landmarker_lite.task"
}