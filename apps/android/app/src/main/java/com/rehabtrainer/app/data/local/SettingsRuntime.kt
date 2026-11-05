package com.rehabtrainer.app.data.local

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 全局设置变更计数。设置页写入偏好后调用 [notifyChanged]，
 * 主题等全局消费者以此为 key 重新读取持久化值。
 *
 * 只服务于跨页面生效的设置（当前是高对比度主题）。
 */
object SettingsRuntime {

    var revision by mutableIntStateOf(0)
        private set

    fun notifyChanged() {
        revision++
    }
}
