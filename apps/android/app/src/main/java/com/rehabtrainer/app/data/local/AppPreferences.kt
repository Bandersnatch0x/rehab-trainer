package com.rehabtrainer.app.data.local

import android.content.Context

/** 端上轻量偏好；训练页与设置页共用同一份音频引导开关。 */
object AppPreferences {

    private const val PREFS_NAME = "rehab_settings"
    private const val KEY_AUDIO_GUIDANCE = "audio_guidance_enabled"
    private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
    private const val KEY_HIGH_CONTRAST = "high_contrast_enabled"
    private const val KEY_DEVICE_BOUND = "device_bound"

    fun isAudioGuidanceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUDIO_GUIDANCE, SettingsDefaults.AUDIO_GUIDANCE)

    fun setAudioGuidanceEnabled(context: Context, enabled: Boolean) =
        put(context, KEY_AUDIO_GUIDANCE, enabled)

    fun isHapticFeedbackEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTIC_FEEDBACK, SettingsDefaults.HAPTIC_FEEDBACK)

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) =
        put(context, KEY_HAPTIC_FEEDBACK, enabled)

    fun isHighContrastEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIGH_CONTRAST, SettingsDefaults.HIGH_CONTRAST)

    fun setHighContrastEnabled(context: Context, enabled: Boolean) =
        put(context, KEY_HIGH_CONTRAST, enabled)

    /**
     * 本机绑定状态。演示版预置为已绑定（D21），解除后转为未绑定，
     * 需重新从机构获取绑定码才能继续训练。
     */
    fun isDeviceBound(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVICE_BOUND, SettingsDefaults.DEVICE_BOUND)

    fun clearDeviceBinding(context: Context) =
        put(context, KEY_DEVICE_BOUND, false)

    /** 绑定成功后回写；否则解绑再重绑时设置页会一直显示「未绑定」。 */
    fun markDeviceBound(context: Context) =
        put(context, KEY_DEVICE_BOUND, true)

    private fun put(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/** 设置项默认值；独立成对象以便 JVM 单测直接断言。 */
object SettingsDefaults {
    const val AUDIO_GUIDANCE = true
    const val HAPTIC_FEEDBACK = true
    const val HIGH_CONTRAST = true
    const val DEVICE_BOUND = true
}
