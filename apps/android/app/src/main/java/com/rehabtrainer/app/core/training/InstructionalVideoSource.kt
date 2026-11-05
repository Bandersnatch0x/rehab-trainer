package com.rehabtrainer.app.core.training

/**
 * 教学示范视频的播放来源与叠加标注。
 *
 * 视频由服务端媒体库下发（`Exercise.videoUri`）；地址缺失、为空或播放失败时
 * 回退到 APK 内置演示视频，保证训练不中断。水印与署名由客户端叠加显示，
 * 不烧录进文件，因此同一份文件可在不同机构复用。
 */
data class InstructionalVideoSource(
    val remoteUrl: String? = null,
    val watermarkText: String? = null,
    val creditLine: String? = null
) {

    val resolvedRemoteUrl: String? = remoteUrl?.trim()?.takeIf { it.isNotEmpty() }

    /** 只有服务端下发了地址才尝试远端播放，否则直接用内置演示视频。 */
    val shouldUseRemote: Boolean
        get() = resolvedRemoteUrl != null

    /** 叠加水印；空串视为未设置，不渲染空标签。 */
    val watermarkLabel: String?
        get() = watermarkText?.trim()?.takeIf { it.isNotEmpty() }

    val creditLabel: String?
        get() = creditLine?.trim()?.takeIf { it.isNotEmpty() }

    val hasOverlay: Boolean
        get() = watermarkLabel != null || creditLabel != null

    /**
     * 播放失败后的回退判定：远端失败过一次就固定用内置视频，
     * 避免错误监听里反复重设远端地址造成循环。
     */
    fun urlAfterFailure(remoteFailed: Boolean): String? =
        if (remoteFailed) null else resolvedRemoteUrl
}
