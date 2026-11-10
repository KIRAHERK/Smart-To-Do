package io.github.kiraherk.smarttodo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicTextTest {
    @Test
    fun foldsSpellingVariants() {
        assertEquals(ArabicText.normalize("احمد"), ArabicText.normalize("أَحْمَد"))
        assertEquals(ArabicText.normalize("مدرسه"), ArabicText.normalize("مدرسة"))
        assertEquals(ArabicText.normalize("مستشفي"), ArabicText.normalize("مستشفى"))
        assertEquals("call ahmed", ArabicText.normalize("  Call   AHMED "))
    }

    @Test
    fun keywordsDropPrefixesAndShortWords() {
        val k = ArabicText.keywords("متى آخر مرة اتصلت بأحمد بخصوص الشحنة؟")
        assertTrue(k.toString(), "احمد" in k)
        assertTrue(k.toString(), "شحنه" in k)
        assertTrue(k.toString(), "اتصلت" in k)
        assertTrue(k.toString(), k.none { it.length < 3 })
    }
}
