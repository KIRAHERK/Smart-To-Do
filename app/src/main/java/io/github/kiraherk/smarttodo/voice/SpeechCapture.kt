package io.github.kiraherk.smarttodo.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Speech to text that keeps listening while he pauses to think: Android's recognizer stops after a
 * short silence, so we restart it and join the pieces until he taps "Done" (or stays silent twice
 * in a row after saying something).
 *
 * Phones differ a lot in which speech services work for other apps, so we try, in order: the
 * phone's default recognizer, Google's "Speech Services", the Google app, then any other one.
 * If none of them can even start, the caller falls back to Google's standard listening popup
 * (see [VoiceViewModel]). Must be used from the main thread.
 */
class SpeechCapture(private val context: Context) {

    data class State(
        val listening: Boolean = false,
        val text: String = "",
        val partial: String = "",
        val level: Float = 0f,
        val errorCode: Int? = null,
    ) {
        val display: String get() = listOf(text, partial).filter { it.isNotBlank() }.joinToString(" ")
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var language: String = "ar-SA"
    private var onFinished: ((String, Int?) -> Unit)? = null
    private var finishing = false
    private var silentRounds = 0
    private var session = 0

    /** null = the phone's default recognizer. */
    private var candidates: List<ComponentName?> = emptyList()
    private var candidateIndex = 0
    private var busyRetries = 0
    /** The current recognizer has shown it works (it got ready or heard something). */
    private var working = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context) || recognitionServices().isNotEmpty()

    /** [onFinished] receives the full text (may be blank) and an error code if listening failed. */
    fun start(language: String, onFinished: (text: String, errorCode: Int?) -> Unit) {
        cancel()
        this.language = language
        this.onFinished = onFinished
        finishing = false
        silentRounds = 0
        busyRetries = 0
        working = false
        candidates = buildCandidates()
        candidateIndex = 0
        _state.value = State(listening = true)
        recognizer = create(candidates.first())
        listen()
    }

    /** He tapped "Done": deliver what we have (waiting briefly for the last words). */
    fun finish() {
        if (!_state.value.listening) return
        finishing = true
        runCatching { recognizer?.stopListening() }
        val s = session
        handler.postDelayed({ if (s == session && finishing) deliver(null) }, 1500)
    }

    fun cancel() {
        session++
        handler.removeCallbacksAndMessages(null)
        onFinished = null
        destroyRecognizer()
        _state.value = State()
    }

    private fun recognitionServices(): List<ComponentName> = runCatching {
        context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
    }.getOrDefault(emptyList())

    private fun buildCandidates(): List<ComponentName?> {
        val services = recognitionServices()
        val preferred = listOf(GOOGLE_SPEECH_SERVICES, GOOGLE_APP)
        val google = preferred.flatMap { pkg -> services.filter { it.packageName == pkg } }
        val others = services.filter { it.packageName !in preferred }
        val list = mutableListOf<ComponentName?>()
        if (SpeechRecognizer.isRecognitionAvailable(context)) list += null
        list += google
        list += others
        return list.distinct().ifEmpty { listOf(null) }
    }

    private fun create(component: ComponentName?): SpeechRecognizer {
        val r = if (component == null) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context, component)
        }
        r.setRecognitionListener(Listener(session))
        return r
    }

    /** Tries the next speech service when the current one can't work on this phone. */
    private fun switchToNext(): Boolean {
        if (working || candidateIndex + 1 >= candidates.size) return false
        candidateIndex++
        busyRetries = 0
        destroyRecognizer()
        recognizer = runCatching { create(candidates[candidateIndex]) }.getOrNull() ?: return switchToNext()
        restart(300)
        return true
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        val started = runCatching { recognizer?.startListening(intent) }.isSuccess
        if (!started && !switchToNext()) deliver(SpeechRecognizer.ERROR_CLIENT)
    }

    private fun restart(delayMs: Long = 200) {
        val s = session
        handler.postDelayed({ if (s == session && !finishing) listen() }, delayMs)
    }

    private fun deliver(errorCode: Int?) {
        val text = _state.value.display.trim()
        val callback = onFinished
        session++
        handler.removeCallbacksAndMessages(null)
        onFinished = null
        destroyRecognizer()
        _state.value = State(text = text, errorCode = errorCode)
        callback?.invoke(text, errorCode)
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private inner class Listener(private val mySession: Int) : RecognitionListener {
        private fun active() = mySession == session

        override fun onReadyForSpeech(params: Bundle?) {
            if (active()) working = true
        }

        override fun onBeginningOfSpeech() {
            if (active()) working = true
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            if (active()) _state.value = _state.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active()) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (partial.isNotBlank()) working = true
            _state.value = _state.value.copy(partial = partial)
        }

        override fun onResults(results: Bundle?) {
            if (!active()) return
            working = true
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            val joined = listOf(_state.value.text, text.ifBlank { _state.value.partial }).filter { it.isNotBlank() }.joinToString(" ")
            _state.value = _state.value.copy(text = joined, partial = "")
            silentRounds = 0
            if (finishing) deliver(null) else restart()
        }

        override fun onError(error: Int) {
            if (!active()) return
            if (finishing) {
                deliver(null)
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // A service that fails this way before it was ever ready simply doesn't work here.
                    if (!working && switchToNext()) return
                    silentRounds++
                    val hasText = _state.value.display.isNotBlank()
                    if ((hasText && silentRounds >= 2) || (!hasText && silentRounds >= 3)) deliver(null) else restart()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    if (busyRetries++ < 3) {
                        destroyRecognizer()
                        recognizer = create(candidates[candidateIndex])
                        restart(500)
                    } else if (!switchToNext()) {
                        deliver(error)
                    }
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> deliver(error)
                else -> if (!switchToNext()) deliver(error)
            }
        }
    }

    companion object {
        private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        private const val GOOGLE_SPEECH_SERVICES = "com.google.android.tts"

        /** Errors that mean "no internet", not "this speech service can't work on this phone". */
        val NETWORK_ERRORS = setOf(SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT)

        /** Dialects offered in Settings (speech recognition locale tags). */
        val DIALECTS = listOf("ar-SA", "ar-AE", "ar-KW", "ar-QA", "ar-BH", "ar-OM", "ar-EG", "ar-JO", "ar-LB", "ar-PS", "ar-IQ", "ar-MA", "ar-DZ", "ar-TN", "ar-LY", "ar-SD", "ar-YE", "en-US")
    }
}
