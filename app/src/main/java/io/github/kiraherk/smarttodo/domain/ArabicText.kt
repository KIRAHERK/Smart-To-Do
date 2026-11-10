package io.github.kiraherk.smarttodo.domain

/**
 * Normalises Arabic text so searching works no matter how a word was spelled or recognised:
 * alef forms, taa marbuta, alef maqsura, hamza seats and diacritics are folded together.
 */
object ArabicText {
    private val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val spaces = Regex("\\s+")
    private val separators = Regex("[\\s،,.؟?!:;\"'()\\[\\]-]+")
    private val prefixes = listOf("وال", "بال", "فال", "كال", "لل", "ال")

    fun normalize(text: String): String = text.lowercase()
        .replace(marks, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace(spaces, " ")
        .trim()

    /** Search keywords: normalised words of 3+ letters with the common "the/and the" prefixes removed. */
    fun keywords(text: String): List<String> = normalize(text)
        .split(separators)
        .map { word -> prefixes.firstOrNull { word.startsWith(it) && word.length - it.length >= 3 }?.let { word.removePrefix(it) } ?: word }
        .filter { it.length >= 3 }
        .distinct()
}
