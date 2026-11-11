package io.github.kiraherk.smarttodo.ai

import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.db.ChatMessageEntity
import io.github.kiraherk.smarttodo.domain.ChatRole
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Answers his questions about his own tasks, notes and history. One Gemini request per question. */
class AssistantService(
    private val ai: AiService,
    private val contextBuilder: ContextBuilder,
    private val db: AppDatabase,
    private val clock: Clock,
    private val replyLanguage: () -> String,
) {
    suspend fun answer(question: String): String {
        val now = ZonedDateTime.now(clock)
        val digest = contextBuilder.assistantDigest(question)
        val history = db.chat().recent(8).map {
            if (it.role == ChatRole.USER.name) GeminiClient.Turn.user(it.text) else GeminiClient.Turn.model(it.text)
        }.dropWhile { it.role != "user" }
        val prompt = buildString {
            appendLine("NOW: ${now.toLocalDate()} (${now.dayOfWeek}), ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            appendLine()
            appendLine("HIS DATA:")
            appendLine(digest)
            appendLine()
            appendLine("HIS QUESTION:")
            appendLine(question.trim())
        }
        val system = SYSTEM_PROMPT.replace("{LANG}", CommandInterpreter.languageName(replyLanguage()))
        val answer = ai.generate(AiPurpose.ASSISTANT, system, history + GeminiClient.Turn.user(prompt), schema = null, temperature = 0.3)
            .text
            .replace("*", "")
            .trim()
        val t = clock.millis()
        db.chat().insert(ChatMessageEntity(role = ChatRole.USER.name, text = question.trim(), at = t))
        db.chat().insert(ChatMessageEntity(role = ChatRole.ASSISTANT.name, text = answer, at = t + 1))
        return answer
    }

    companion object {
        val SYSTEM_PROMPT = """
You are the personal assistant inside "Smart To-Do", talking with an older businessman about his own
to-do list. Your answer is read aloud to him.
- Answer in {LANG}. Be short, warm and clear: 1 to 4 sentences, natural spoken style, no lists, no markdown, no emojis.
- Use ONLY the data provided (his tasks, statuses, notes and history). If the answer is not there, say so honestly.
- Say dates in a friendly way (for example "last Tuesday, 4 November") and mention whether things were done, partly done or not done.
- Write times and day numbers with digits, for example "الساعة 12 ظهراً" or "الساعة 4:30 عصراً", never spelled out in words.
- If he asks you to add or change something, tell him kindly to press the big talk button and say it.
""".trim()
    }
}
