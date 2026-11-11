package io.github.kiraherk.smarttodo.ai

/**
 * Small language models sometimes write their own thinking into a field ("Wait, let's look at the
 * title: ... Category: WORK ..."). This spots that, so the app can ask again, and as a last resort
 * cleans the text so a task never gets a paragraph of English reasoning as its name.
 */
object ActionSanity {
    const val MAX_TITLE_CHARS = 80
    const val MAX_TITLE_WORDS = 12
    const val MAX_SUMMARY_CHARS = 350

    // English words a model uses when it "thinks aloud" or names fields. Ignored if he said them himself.
    private val markers = Regex(
        """\b(wait|let'?s|let me|actually|hmm+|okay|alright|title|category|priority|reminder|recurrence|spokensummary|""" +
            """taskid|json|the user|he said|he wants|i will|i'll|i think|maybe|however|therefore|so the)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val fieldLabel = Regex("""\b(date|time|status|type|details|note)\s*:""", RegexOption.IGNORE_CASE)
    private val isoDate = Regex("""\d{4}-\d{2}-\d{2}""")
    private val latin = Regex("[A-Za-z]")

    /** True when [text] looks like reasoning rather than a value meant for him. */
    fun looksLikeReasoning(text: String?, transcript: String): Boolean {
        if (text.isNullOrBlank()) return false
        if ('\n' in text.trim()) return true
        val heard = transcript.lowercase()
        if (markers.findAll(text).any { it.value.lowercase() !in heard }) return true
        return fieldLabel.containsMatchIn(text)
    }

    fun badTitle(title: String?, transcript: String): Boolean {
        if (title.isNullOrBlank()) return false
        val t = title.trim()
        return t.length > MAX_TITLE_CHARS ||
            t.split(Regex("\\s+")).size > MAX_TITLE_WORDS ||
            (isoDate.containsMatchIn(t) && !isoDate.containsMatchIn(transcript)) ||
            looksLikeReasoning(t, transcript)
    }

    /** Short descriptions of what is wrong; empty when everything looks fine. */
    fun problems(actions: List<AiAction>, transcript: String): List<String> = buildList {
        actions.forEachIndexed { i, a ->
            if (badTitle(a.title, transcript)) add("action ${i + 1}: title")
            if (a.spokenSummary.length > MAX_SUMMARY_CHARS || looksLikeReasoning(a.spokenSummary, transcript)) add("action ${i + 1}: spokenSummary")
            if (looksLikeReasoning(a.details, transcript)) add("action ${i + 1}: details")
            if (looksLikeReasoning(a.note, transcript)) add("action ${i + 1}: note")
            if (looksLikeReasoning(a.question, transcript)) add("action ${i + 1}: question")
        }
    }

    /** Last resort when asking again didn't help: keep what is usable, drop the rest. */
    fun repair(actions: List<AiAction>, transcript: String): List<AiAction> = actions.map { a ->
        a.copy(
            title = if (badTitle(a.title, transcript)) cleanTitle(a.title.orEmpty(), transcript) else a.title,
            spokenSummary = if (a.spokenSummary.length > MAX_SUMMARY_CHARS || looksLikeReasoning(a.spokenSummary, transcript)) "" else a.spokenSummary,
            details = a.details?.takeUnless { looksLikeReasoning(it, transcript) },
            note = if (looksLikeReasoning(a.note, transcript)) transcript.trim() else a.note,
            question = if (looksLikeReasoning(a.question, transcript)) transcript.trim() else a.question,
        )
    }

    /** Pulls the real name out of a polluted title, or falls back to the start of what he said. */
    fun cleanTitle(title: String, transcript: String): String {
        val afterLabel = Regex("""title\s*[:：]\s*(.+)""", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)
        val candidates = listOfNotNull(afterLabel, title)
        for (c in candidates) {
            val cut = c.split('\n').first()
            val end = listOf(latin.find(cut)?.range?.first, cut.indexOf('.').takeIf { it >= 0 }, cut.indexOf(':').takeIf { it >= 0 })
                .filterNotNull().minOrNull() ?: cut.length
            val piece = shorten(cut.substring(0, end))
            if (piece.length >= 3) return piece
        }
        return shorten(transcript).ifBlank { transcript.take(MAX_TITLE_CHARS) }
    }

    private fun shorten(text: String): String = text
        .trim { it.isWhitespace() || it in ".,،؛;:-–…\"'«»()" }
        .split(Regex("\\s+")).filter { it.isNotBlank() }.take(8).joinToString(" ")
        .take(MAX_TITLE_CHARS)
}
