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

    /**
     * Search keywords: normalised words of 3+ letters with "the / and the" prefixes removed, plus the
     * word without a one-letter prefix (ب و ل ف ك) so "بأحمد" also finds "أحمد". Matching is by
     * substring, so stripping a prefix only ever finds more, never less.
     */
    fun keywords(text: String): List<String> = normalize(text)
        .split(separators)
        .flatMap { raw ->
            val word = prefixes.firstOrNull { raw.startsWith(it) && raw.length - it.length >= 3 }?.let { raw.removePrefix(it) } ?: raw
            val short = if (word.length >= 4 && word[0] in singleLetterPrefixes) word.substring(1) else null
            listOfNotNull(word, short)
        }
        .filter { it.length >= 3 }
        .distinct()

    private val singleLetterPrefixes = setOf('ب', 'و', 'ل', 'ف', 'ك')
}
