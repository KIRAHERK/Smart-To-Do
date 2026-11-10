package io.github.kiraherk.smarttodo.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads confirmations and answers aloud. Prefers Google's TTS engine, which has good Arabic voices. */
class Speaker(context: Context, private val languageTag: () -> String) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val tts: TextToSpeech
    private var ready = false
    private var pending: String? = null

    /** Null until the engine has started; false if the phone has no voice for the app language. */
    var voiceAvailable: Boolean? = null
        private set

    var enabled: Boolean = true

    init {
        val engines = runCatching {
            appContext.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                .map { it.serviceInfo.packageName }
        }.getOrDefault(emptyList())
        tts = if (GOOGLE_TTS in engines) TextToSpeech(appContext, this, GOOGLE_TTS) else TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            voiceAvailable = false
            return
        }
        ready = true
        applyLanguage()
        pending?.let { speak(it) }
        pending = null
    }

    private fun applyLanguage() {
        val result = runCatching { tts.setLanguage(Locale.forLanguageTag(languageTag())) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        voiceAvailable = result >= TextToSpeech.LANG_AVAILABLE
        tts.setSpeechRate(0.92f)
    }

    fun speak(text: String) {
        if (!enabled || text.isBlank()) return
        if (!ready) {
            pending = text
            return
        }
        applyLanguage()
        tts.speak(text.replace("*", ""), TextToSpeech.QUEUE_FLUSH, null, "smarttodo-${System.nanoTime()}")
    }

    /** Speaks even when read-aloud is switched off (for the explicit "read" buttons). */
    fun speakNow(text: String) {
        val was = enabled
        enabled = true
        speak(text)
        enabled = was
    }

    fun stop() {
        if (ready) runCatching { tts.stop() }
        pending = null
    }

    fun installVoiceIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private const val GOOGLE_TTS = "com.google.android.tts"
    }
}
