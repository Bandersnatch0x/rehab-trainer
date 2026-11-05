package com.rehabtrainer.app.core.training

/**
 * 显示与播报决策：当前模式 + 屏幕形态 + 语音开关 → 是否显示教学视频、是否播报。
 *
 * 抽成纯函数便于 JVM 单测覆盖；屏幕过窄时强制纯实景，避免双栏被压到不可读。
 */
data class TrainingDisplayPolicy(
    val mode: TrainingDisplayMode,
    val isPhoneLandscape: Boolean,
    val availableWidthDp: Int,
    val isVoiceEnabled: Boolean
) {
    val isForcedCameraOnly: Boolean
        get() = isPhoneLandscape && availableWidthDp < MIN_LANDSCAPE_DUAL_WIDTH_DP

    val effectiveMode: TrainingDisplayMode
        get() = if (isForcedCameraOnly) TrainingDisplayMode.CAMERA_ONLY else mode

    val showVideo: Boolean
        get() = effectiveMode == TrainingDisplayMode.DUAL

    val shouldSpeak: Boolean
        get() = effectiveMode == TrainingDisplayMode.CAMERA_ONLY && isVoiceEnabled

    companion object {
        /** 手机横屏可用宽度低于该值时，40% 视频栏不足 192dp，双播放失去可读性。 */
        const val MIN_LANDSCAPE_DUAL_WIDTH_DP = 480
    }
}
