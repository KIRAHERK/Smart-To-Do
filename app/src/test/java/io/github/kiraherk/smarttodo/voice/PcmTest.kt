package io.github.kiraherk.smarttodo.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmTest {
    private val samples = ByteArray(48_000) { (it % 251).toByte() } // one second at 24 kHz, 16-bit mono

    private fun wav(data: ByteArray, rate: Int = 24_000, channels: Int = 1, dataSize: Int = data.size, extraChunk: Boolean = false): ByteArray {
        val extra = if (extraChunk) 8 + 6 else 0
        val b = ByteBuffer.allocate(44 + extra + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + extra + data.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        if (extraChunk) b.put("LIST".toByteArray()).putInt(6).put(ByteArray(6))
        b.put("data".toByteArray()).putInt(dataSize).put(data)
        return b.array()
    }

    @Test
    fun readsTheWavFilesTheNewerModelsSend() {
        val pcm = Pcm.decode(wav(samples, extraChunk = true), "audio/wav")
        assertEquals(24_000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertArrayEquals(samples, pcm.data)
        assertEquals(1000, pcm.durationMs)
    }

    @Test
    fun readsStreamedWavWithoutASize() {
        assertArrayEquals(samples, Pcm.decode(wav(samples, dataSize = -1), null).data)
    }

    @Test
    fun readsBareSamplesDescribedByTheMimeType() {
        val pcm = Pcm.decode(samples, "audio/L16;codec=pcm;rate=24000")
        assertEquals(24_000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertArrayEquals(samples, pcm.data)
        assertEquals(16_000, Pcm.decode(samples, "audio/l16; rate=16000; channels=1").sampleRate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesOtherFormats() {
        Pcm.decode(ByteArray(100), "audio/mpeg")
    }

    @Test
    fun recordingsAreSavedAsWavFilesTheAiCanRead() {
        val recorded = Pcm(16_000, 1, samples)
        val wav = recorded.toWav()
        assertEquals(44 + samples.size, wav.size)
        val back = Pcm.decode(wav, "audio/wav")
        assertEquals(16_000, back.sampleRate)
        assertArrayEquals(samples, back.data)
    }

    @Test
    fun loudnessForTheMovingRings() {
        assertEquals(0f, VoiceRecorder.level(ByteArray(320), 320), 0f)
        val loud = ByteArray(320) { if (it % 2 == 0) 0 else 0x40 } // samples of 16384
        assertEquals(1f, VoiceRecorder.level(loud, loud.size), 0f)
    }
}
