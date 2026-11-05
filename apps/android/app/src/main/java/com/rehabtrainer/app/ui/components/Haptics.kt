package com.rehabtrainer.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** 触发一次轻触马达回馈；硬件不支持时系统静默忽略。 */
internal fun View.performHapticTap() {
    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(feedback)
}
