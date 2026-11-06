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
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
        }.getOrDefault(UNKNOWN_SHA256).also { cached = it }
    }

    private const val MODEL_ASSET = "pose_landmarker_lite.task"
}