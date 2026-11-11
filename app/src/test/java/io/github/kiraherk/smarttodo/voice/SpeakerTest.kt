package io.github.kiraherk.smarttodo.voice

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.ai.AiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = Application::class)
class SpeakerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun lastPhoneText(): String? = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText

    @Test
    fun usesThePhoneVoiceWhenTheNaturalVoiceFails() {
        val speaker = Speaker(app, { "ar" }, { "Kore" }, { _, _ -> throw AiException.Quota(null, "free limit used up") })
        speaker.onInit(TextToSpeech.SUCCESS)
        speaker.speakNow("سأضيف مهمة")
        ShadowLooper.idleMainLooper()
        assertEquals("سأضيف مهمة", lastPhoneText())
        assertEquals("", speaker.lastSpokenWith)
    }

    @Test
    fun usesTheNaturalVoiceWhenItAnswers() {
        val speaker = Speaker(app, { "ar" }, { "Kore" }, { _, _ -> Pcm(24_000, 1, ByteArray(480)) })
        speaker.onInit(TextToSpeech.SUCCESS)
        speaker.speakNow("سأضيف مهمة")
        ShadowLooper.idleMainLooper()
        assertEquals("Kore", speaker.lastSpokenWith)
        assertNull(lastPhoneText())
        speaker.stop()
    }

    @Test
    fun phoneVoiceSettingSkipsTheInternet() {
        var asked = false
        val speaker = Speaker(app, { "ar" }, { "" }, { _, _ -> asked = true; null })
        speaker.onInit(TextToSpeech.SUCCESS)
        speaker.speakNow("مرحباً")
        ShadowLooper.idleMainLooper()
        assertEquals(false, asked)
        assertEquals("مرحباً", lastPhoneText())
    }

    @Test
    fun aChosenPhoneVoiceSpeaksAtOnceWithoutTheInternet() {
        var asked = false
        val speaker = Speaker(app, { "ar" }, { "phone:ar-xa-x-ard-local" }, { _, _ -> asked = true; null })
        speaker.onInit(TextToSpeech.SUCCESS)
        speaker.speakNow("تم الحفظ")
        ShadowLooper.idleMainLooper()
        assertEquals(false, asked)
        assertEquals("تم الحفظ", lastPhoneText())
        assertEquals("phone:ar-xa-x-ard-local", speaker.lastSpokenWith)
    }

    @Test
    fun onlyArabicVoicesStoredOnThePhoneAreOffered() {
        val ar = java.util.Locale.forLanguageTag("ar")
        fun voice(name: String, locale: java.util.Locale, network: Boolean = false, features: Set<String> = emptySet()) =
            android.speech.tts.Voice(name, locale, android.speech.tts.Voice.QUALITY_HIGH, android.speech.tts.Voice.LATENCY_NORMAL, network, features)
        val all = listOf(
            voice("ar-xa-x-arz-local", ar),
            voice("ar-xa-x-arc-local", ar),
            voice("ar-xa-x-arc-network", ar, network = true),
            voice("ar-xa-x-ard-local", ar, features = setOf("notInstalled")),
            voice("ar-language", ar, features = setOf("legacySetLanguageVoice")),
            voice("en-us-x-sfg-local", java.util.Locale.US),
        )
        assertEquals(listOf("ar-xa-x-arc-local", "ar-xa-x-arz-local"), Speaker.offlineVoices(all, ar).map { it.name })
    }
}
