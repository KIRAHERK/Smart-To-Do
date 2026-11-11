package io.github.kiraherk.smarttodo.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Records his voice with the phone's microphone, for as long as he needs: it never stops on its
 * own because he paused (only when he presses "Done", or after [MAX_MS] as a safety limit).
 * Used when the phone's speech recognition can't work inside the app.
 */
class VoiceRecorder {
    data class State(val recording: Boolean = false, val level: Float = 0f, val elapsedMs: Long = 0)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var record: AudioRecord? = null
    private val buffer = ByteArrayOutputStream()

    /** Called (on a background thread) if the safety limit is reached. */
    var onLimitReached: (() -> Unit)? = null

    /** Starts recording. False if the microphone can't be opened (busy, no permission). */
    @SuppressLint("MissingPermission") // The caller asks for the microphone permission first.
    fun start(): Boolean {
        cancel()
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val r = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 4)
        }.getOrNull() ?: return false
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        runCatching { r.startRecording() }.onFailure { r.release(); return false }
        if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            r.release()
            return false
        }
        record = r
        synchronized(buffer) { buffer.reset() }
        _state.value = State(recording = true)
        val started = System.currentTimeMillis()
        job = scope.launch {
            val chunk = ByteArray(minBuffer)
            while (isActive) {
                val n = r.read(chunk, 0, chunk.size)
                if (n <= 0) break
                synchronized(buffer) { buffer.write(chunk, 0, n) }
                val elapsed = System.currentTimeMillis() - started
                _state.value = State(recording = true, level = level(chunk, n), elapsedMs = elapsed)
                if (elapsed >= MAX_MS) {
                    onLimitReached?.invoke()
                    break
                }
            }
        }
        return true
    }

    /** Stops and returns what was recorded as a WAV file (null if nothing usable was recorded). */
    suspend fun stop(): ByteArray? = withContext(Dispatchers.IO) {
        val r = record ?: return@withContext null
        record = null
        runCatching { r.stop() }
        job?.join()
        job = null
        runCatching { r.release() }
        _state.value = State()
        val pcm = synchronized(buffer) { buffer.toByteArray().also { buffer.reset() } }
        if (pcm.size < RATE * 2 / 2) null else Pcm(RATE, 1, pcm).toWav() // less than half a second: nothing said
    }

    fun cancel() {
        job?.cancel()
        job = null
        record?.let { r -> runCatching { r.stop() }; runCatching { r.release() } }
        record = null
        synchronized(buffer) { buffer.reset() }
        _state.value = State()
    }

    companion object {
        const val RATE = 16_000
        /** Safety limit for one recording. */
        const val MAX_MS = 120_000L

        /** 0..1 loudness of a chunk of 16-bit samples, for the moving rings. */
        fun level(bytes: ByteArray, length: Int): Float {
            var sum = 0.0
            var count = 0
            var i = 0
            while (i + 1 < length) {
                val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toDouble()
                sum += sample * sample
                count++
                i += 2
            }
            if (count == 0) return 0f
            val rms = sqrt(sum / count)
            return (rms / 6_000.0).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
