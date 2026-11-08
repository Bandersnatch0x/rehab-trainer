package com.rehabtrainer.app.ui.audio

/**
 * TTS 初始化看门狗。
 *
 * 系统 TTS 引擎可能「构造成功但永不回调」：语音包损坏、被安全软件拦截，或部分 OEM 实现
 * 在服务尚未就绪时直接丢弃 onInit。此时 [VoiceCoach] 的 `isReady` 永远停在初始值 false，
 * 界面却已经按「语音开」渲染，患者全程听不到提示音也无从察觉。
 *
 * 纯逻辑，不依赖 Handler / TextToSpeech：调用方在构造引擎后延时调 [onTimeout]，
 * 在引擎回调时调 [onCallback]，谁先到谁生效；[release] 之后一律不再通知，
 * 避免页面已销毁后还有回调去改已释放的 UI 状态。
 */
internal class TtsInitWatchdog {

    private var callbackSeen = false
    private var timedOut = false
    private var released = false

    /**
     * 引擎回调到达。返回 true 表示应把结果通知给界面。
     *
     * 超时之后才到的回调**仍然**返回 true：引擎只是启动慢的时候应当恢复播报，
     * 而不是永久静音。只有 [release] 之后才忽略。
     */
    fun onCallback(): Boolean {
        if (released) return false
        callbackSeen = true
        return true
    }

    /** 超时到点。只有「回调未到、尚未超时、也未释放」时才需要按不可用通知界面。 */
    fun onTimeout(): Boolean {
        if (released || callbackSeen || timedOut) return false
        timedOut = true
        return true
    }

    /** 页面销毁：此后的超时与回调都不再通知界面。 */
    fun release() {
        released = true
    }
}
