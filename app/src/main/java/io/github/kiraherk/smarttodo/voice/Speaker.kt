package io.github.kiraherk.smarttodo.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Reads confirmations and answers aloud.
 *
 * With a natural voice chosen (the default), the text is spoken by one of Google's Gemini voices,
 * which sound like a real person. If that is not possible within a few seconds (no internet, the
 * free daily limit is used up, Google is busy), the phone's own voice reads it instead, so he
 * always hears the answer.
 */
class Speaker(
    context: Context,
    private val languageTag: () -> String,
    /** The chosen Gemini voice, or "" for the phone's voice. */
    private val voiceName: suspend () -> String = { "" },
    /** Makes natural speech; null when it isn't possible right now. */
    private val natural: (suspend (text: String, voice: String) -> Pcm?)? = null,
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val tts: TextToSpeech
    private val player = PcmPlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var ready = false
    private var pending: String? = null

    /** Null until the engine has started; false if the phone has no voice for the app language. */
    var voiceAvailable: Boolean? = null
        private set

    var enabled: Boolean = true

    /** What the last request was spoken with (for tests and the settings screen). */
    @Volatile var lastSpokenWith: String = ""
        private set

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
        pending?.let { phone(it) }
        pending = null
    }

    private fun applyLanguage() {
        val locale = Locale.forLanguageTag(languageTag())
        val result = runCatching { tts.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        voiceAvailable = result >= TextToSpeech.LANG_AVAILABLE
        // The default voice is often not the best one installed. With internet, Google's online
        // voices sound much more natural; without it, the clearest voice stored on the phone.
        runCatching {
            val online = isOnline()
            tts.voices
                ?.filter { it.locale.language == locale.language && "notInstalled" !in it.features && (online || !it.isNetworkConnectionRequired) }
                ?.maxWithOrNull(compareBy<android.speech.tts.Voice> { if (online && it.isNetworkConnectionRequired) 1 else 0 }.thenBy { it.quality })
                ?.let { if (tts.voice?.name != it.name) tts.voice = it }
        }
        tts.setSpeechRate(0.92f)
    }

    /** Speaks if read-aloud is on. A new text interrupts the one being spoken. */
    fun speak(text: String) {
        if (!enabled) return
        say(text, null)
    }

    /** Speaks even when read-aloud is switched off (for the explicit "read" buttons). */
    fun speakNow(text: String) = say(text, null)

    /** Plays [text] with a given voice ("" = phone voice) without changing the saved choice. */
    fun preview(text: String, voice: String) = say(text, voice)

    private fun say(text: String, voiceOverride: String?) {
        val clean = text.replace("*", "").trim()
        if (clean.isEmpty()) return
        stop()
        job = scope.launch {
            val voice = voiceOverride ?: runCatching { voiceName() }.getOrDefault("")
            val audio = if (voice.isNotBlank() && natural != null) {
                try {
                    natural.invoke(clean, voice)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            if (audio != null && audio.data.isNotEmpty()) {
                lastSpokenWith = voice
                try {
                    player.play(audio)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    phone(clean)
                }
            } else {
                phone(clean)
            }
        }
    }

    private fun phone(text: String) {
        lastSpokenWith = ""
        if (!ready) {
            pending = text
            return
        }
        applyLanguage()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smarttodo-${System.nanoTime()}")
    }

    fun stop() {
        job?.cancel()
        job = null
        player.stop()
        if (ready) runCatching { tts.stop() }
        pending = null
    }

    private fun isOnline(): Boolean = runCatching {
        val cm = appContext.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    fun installVoiceIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private const val GOOGLE_TTS = "com.google.android.tts"

        /** Gemini voices offered in Settings (all speak Arabic); "" = the phone's voice. */
        val NATURAL_VOICES = listOf("Kore", "Aoede", "Leda", "Charon", "Orus", "Puck")
    }
}
