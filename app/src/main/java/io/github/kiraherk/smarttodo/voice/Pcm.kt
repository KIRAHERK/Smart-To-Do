package io.github.kiraherk.smarttodo.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Raw 16-bit little-endian PCM audio, ready for [PcmPlayer]. */
class Pcm(val sampleRate: Int, val channels: Int, val data: ByteArray) {
    val durationMs: Long get() = if (sampleRate <= 0 || channels <= 0) 0 else data.size * 1000L / (sampleRate * channels * 2)

    /** This audio as a standard WAV file (16-bit little-endian PCM). */
    fun toWav(): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + data.size).put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(channels.toShort()).putInt(sampleRate)
            .putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII)).putInt(data.size)
        return header.array() + data
    }

    companion object {
        /**
         * Gemini's speech models answer either with a WAV file or with bare samples described by the
         * MIME type ("audio/L16;codec=pcm;rate=24000"). Both carry 16-bit little-endian samples.
         */
        fun decode(bytes: ByteArray, mimeType: String?): Pcm {
            if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") {
                return fromWav(bytes)
            }
            val mime = mimeType.orEmpty().lowercase()
            require(mime.isEmpty() || "l16" in mime || "pcm" in mime) { "unsupported audio type $mimeType" }
            val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24_000
            val channels = Regex("channels=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            return Pcm(rate, channels, bytes.copyOf(bytes.size - bytes.size % (2 * channels)))
        }

        private fun fromWav(bytes: ByteArray): Pcm {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var pos = 12
            var rate = 0
            var channels = 0
            var bits = 0
            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4, Charsets.US_ASCII)
                val size = buf.getInt(pos + 4)
                val body = pos + 8
                when (id) {
                    "fmt " -> {
                        channels = buf.getShort(body + 2).toInt()
                        rate = buf.getInt(body + 4)
                        bits = buf.getShort(body + 14).toInt()
                    }
                    "data" -> {
                        require(bits == 16 && rate > 0 && channels > 0) { "unsupported wav: $bits bit, $rate Hz, $channels ch" }
                        // Streamed WAVs may say 0 or -1 for the size: take everything that is there.
                        val end = if (size <= 0 || body + size > bytes.size) bytes.size else body + size
                        val length = (end - body) - (end - body) % (2 * channels)
                        return Pcm(rate, channels, bytes.copyOfRange(body, body + length))
                    }
                }
                if (size < 0) break
                pos = body + size + (size and 1)
            }
            throw IllegalArgumentException("wav without data")
        }
    }
}
