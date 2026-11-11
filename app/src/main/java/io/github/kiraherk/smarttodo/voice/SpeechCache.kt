package io.github.kiraherk.smarttodo.voice

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Keeps natural-voice audio that was already made, in memory and on disk, so hearing the same
 * sentence again (a voice sample, "read again") doesn't use up the small free daily limit.
 */
class SpeechCache(private val dir: File, private val maxFiles: Int = 40, private val maxInMemory: Int = 12) {
    private val memory = object : LinkedHashMap<String, Pcm>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pcm>?) = size > maxInMemory
    }

    @Synchronized
    fun get(voice: String, text: String): Pcm? {
        val key = key(voice, text)
        memory[key]?.let { return it }
        val file = File(dir, "$key.pcm")
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val rate = input.readInt()
                val channels = input.readInt()
                val data = input.readBytes()
                Pcm(rate, channels, data)
            }
        }.getOrNull()?.also {
            memory[key] = it
            file.setLastModified(System.currentTimeMillis())
        }
    }

    @Synchronized
    fun put(voice: String, text: String, pcm: Pcm) {
        val key = key(voice, text)
        memory[key] = pcm
        runCatching {
            dir.mkdirs()
            DataOutputStream(File(dir, "$key.pcm").outputStream().buffered()).use { out ->
                out.writeInt(pcm.sampleRate)
                out.writeInt(pcm.channels)
                out.write(pcm.data)
            }
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(maxFiles)?.forEach { it.delete() }
        }
    }

    private fun key(voice: String, text: String): String =
        MessageDigest.getInstance("SHA-1").digest("$voice|$text".toByteArray()).joinToString("") { "%02x".format(it) }
}
