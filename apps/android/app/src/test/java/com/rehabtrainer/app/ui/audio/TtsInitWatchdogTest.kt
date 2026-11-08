package com.rehabtrainer.app.ui.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsInitWatchdogTest {

    @Test
    fun timeoutBeforeCallbackReportsUnavailable() {
        // 引擎构造成功但从不回调：超时必须按不可用通知界面，否则「语音开」永远无声。
        assertTrue(TtsInitWatchdog().onTimeout())
    }

    @Test
    fun callbackBeforeTimeoutSuppressesTimeout() {
        val watchdog = TtsInitWatchdog()
        assertTrue(watchdog.onCallback())
        assertFalse(watchdog.onTimeout())
    }

    @Test
    fun lateCallbackStillRecoversAfterTimeout() {
        // 冷启动慢的引擎超时后才就绪：应当恢复播报，而不是永久静音。
        val watchdog = TtsInitWatchdog()
        assertTrue(watchdog.onTimeout())
        assertTrue(watchdog.onCallback())
    }

    @Test
    fun repeatedTimeoutOnlyReportsOnce() {
        val watchdog = TtsInitWatchdog()
        assertTrue(watchdog.onTimeout())
        assertFalse(watchdog.onTimeout())
    }

    @Test
    fun releasedWatchdogIgnoresTimeout() {
        // 页面已销毁，迟到的超时不能再改已释放的 UI 状态。
        val watchdog = TtsInitWatchdog()
        watchdog.release()
        assertFalse(watchdog.onTimeout())
    }

    @Test
    fun releasedWatchdogIgnoresCallback() {
        val watchdog = TtsInitWatchdog()
        watchdog.release()
        assertFalse(watchdog.onCallback())
    }
}
