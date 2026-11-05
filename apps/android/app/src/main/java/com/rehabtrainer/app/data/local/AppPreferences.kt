package com.rehabtrainer.app.data.local

import android.content.Context

/** 端上轻量偏好；训练页与设置页共用同一份音频引导开关。 */
object AppPreferences {

    private const val PREFS_NAME = "rehab_settings"
    private const val KEY_AUDIO_GUIDANCE = "audio_guidance_enabled"

    fun isAudioGuidanceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUDIO_GUIDANCE, true)

    fun setAudioGuidanceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUDIO_GUIDANCE, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
