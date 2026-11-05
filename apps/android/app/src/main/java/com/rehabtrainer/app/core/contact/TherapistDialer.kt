package com.rehabtrainer.app.core.contact

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rehabtrainer.app.BuildConfig

/**
 * 呼叫康复治疗师：只打开系统拨号盘（`ACTION_DIAL`），不自动拨出，因此不需要 `CALL_PHONE` 权限。
 *
 * 号码来自构建配置 [BuildConfig.CONTACT_PHONE]，代码内不硬编码真实号码；
 * 未配置时返回 [DialResult.NotConfigured]，由界面提示联系机构。
 */
object TherapistDialer {

    /** 拨号盘是否可用，纯逻辑可测；界面据此决定提示文案。 */
    fun resolveNumber(rawNumber: String?): DialResult {
        val trimmed = rawNumber?.trim().orEmpty()
        return if (trimmed.isEmpty()) DialResult.NotConfigured else DialResult.Ready(trimmed)
    }

    fun currentNumber(): DialResult = resolveNumber(BuildConfig.CONTACT_PHONE)

    /** @return true 表示拨号盘已打开；false 表示未配置或无可用拨号应用。 */
    fun dial(context: Context, number: String): Boolean {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

sealed interface DialResult {
    data class Ready(val number: String) : DialResult
    data object NotConfigured : DialResult
}
