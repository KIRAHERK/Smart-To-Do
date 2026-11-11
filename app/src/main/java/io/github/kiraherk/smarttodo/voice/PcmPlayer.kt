package io.github.kiraherk.smarttodo.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Plays [Pcm] audio. Cancelling the calling coroutine (or [stop]) stops the sound at once. */
class PcmPlayer {
    @Volatile private var track: AudioTrack? = null

    suspend fun play(pcm: Pcm) = withContext(Dispatchers.IO) {
        stop()
        val channelMask = if (pcm.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(pcm.sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
        track = t
        try {
            t.play()
            var offset = 0
            while (offset < pcm.data.size) {
                coroutineContext.ensureActive()
                val n = t.write(pcm.data, offset, minOf(minBuffer, pcm.data.size - offset))
                if (n <= 0) break
                offset += n
            }
            // Let the last part finish playing before releasing.
            val frames = pcm.data.size / (2 * pcm.channels)
            val deadline = System.currentTimeMillis() + 2_000 + pcm.durationMs
            while (track === t && t.playbackHeadPosition < frames && System.currentTimeMillis() < deadline) {
                delay(40)
            }
        } finally {
            release(t)
        }
    }

    fun stop() {
        track?.let { release(it) }
    }

    private fun release(t: AudioTrack) {
        if (track === t) track = null
        runCatching { t.pause(); t.flush() }
        runCatching { t.release() }
    }
}
