package com.rehabtrainer.app.ui.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 训练语音与节拍音。语音用系统 TTS，不引入第三方依赖；节拍用短提示音避免盖住环境声。
 *
 * 初始化失败时静默降级（[isReady] 保持 false），调用方据此在界面提示并停止播报。
 */
class VoiceCoach(
    context: Context,
    private val onReadyChanged: (Boolean) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceSeq = AtomicInteger(0)

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isReady = false

    private var toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    }.getOrNull()

    init {
        val engine = runCatching {
            // 回调可能先于字段赋值触发；转投主线程确保 tts 已就绪。
            TextToSpeech(context.applicationContext) { status ->
                mainHandler.post { handleInit(status) }
            }
        }.getOrNull()
        tts = engine
        if (engine == null) {
            Log.w(TAG, "TTS engine creation failed")
            isReady = false
            mainHandler.post { onReadyChanged(false) }
        }
    }

    private fun handleInit(status: Int) {
        val engine = tts
        var languageResult = if (status == TextToSpeech.SUCCESS && engine != null) {
            engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            languageResult = engine?.setLanguage(Locale.CHINESE) ?: languageResult
        }
        val usable = status == TextToSpeech.SUCCESS &&
            languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        Log.i(TAG, "TTS init status=$status language=$languageResult usable=$usable")
        isReady = usable
        mainHandler.post { onReadyChanged(usable) }
    }

    fun speak(text: String, flush: Boolean = true) {
        val engine = tts ?: return
        if (!isReady) return
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine.speak(text, queueMode, null, "voice-coach-${utteranceSeq.incrementAndGet()}")
    }

    fun beep() {
        runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS) }
    }

    fun stop() {
        tts?.stop()
        runCatching { toneGenerator?.stopTone() }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        toneGenerator?.release()
        toneGenerator = null
    }

    private companion object {
        const val TAG = "VoiceCoach"
        const val TONE_VOLUME = 80
        const val BEEP_DURATION_MS = 60
    }
}
