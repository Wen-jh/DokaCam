package com.dokacam.camera.ai

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 被拍者语音引导：系统 TextToSpeech 朗读姿势引导文案。
 *
 * - 初始化异步；无 TTS 引擎 / 无中文语音数据的机型静默降级（不打断拍摄流程）；
 * - 节流：同一文案不重复播，两次播报最小间隔 [MIN_GAP_MS]（引导语连续变化时不会刷屏）；
 * - [speak] force = true 用于「到位确认」关键时刻，跳过节流。
 * 所有公开方法需在主线程调用（TTS 内部有 Handler 绑定）。
 */
class PoseVoiceGuide(context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var lastSpoken: String? = null
    private var lastSpeakAtMs = 0L

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "tts init failed: $status")
                    return@TextToSpeech
                }
                val t = tts ?: return@TextToSpeech
                val lang = runCatching { t.setLanguage(Locale.CHINESE) }.getOrNull()
                if (lang == null ||
                    lang == TextToSpeech.LANG_MISSING_DATA ||
                    lang == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "chinese voice unavailable: $lang")
                    return@TextToSpeech
                }
                ready = true
                Log.i(TAG, "tts ready")
            }
        }.onFailure {
            Log.w(TAG, "tts ctor failed", it)
            tts = null
        }
    }

    /** 朗读一句引导；默认受去重 + 3s 间隔节流，[force] 时无条件插播 */
    fun speak(text: String, force: Boolean = false) {
        val t = tts ?: return
        if (!ready) return
        val now = SystemClock.elapsedRealtime()
        if (!force) {
            if (text == lastSpoken) return
            if (now - lastSpeakAtMs < MIN_GAP_MS) return
        }
        runCatching {
            val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pose_voice_$now")
            if (r == TextToSpeech.SUCCESS) {
                lastSpoken = text
                lastSpeakAtMs = now
                Log.i(TAG, "speak: $text")
            } else {
                Log.w(TAG, "speak rejected ($r): $text")
            }
        }
    }

    fun shutdown() {
        ready = false
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        Log.i(TAG, "tts shutdown")
    }

    companion object {
        const val TAG = "PoseVoice"
        private const val MIN_GAP_MS = 3000L
    }
}
