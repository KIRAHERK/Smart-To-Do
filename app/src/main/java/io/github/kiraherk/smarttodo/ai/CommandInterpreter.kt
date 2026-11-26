package io.github.kiraherk.smarttodo.ai

import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Turns one spoken sentence into a list of [AiAction]s with a single Gemini request. */
class CommandInterpreter(
    private val ai: AiService,
    private val context: ContextBuilder,
    private val clock: Clock,
    /** His categories as (key, name), so the AI can also file tasks under ones he made. */
    private val categories: suspend () -> List<Pair<String, String>> = { DEFAULT_CATEGORIES },
    private val replyLanguage: () -> String,
) {
    suspend fun interpret(transcript: String, focusTaskId: Long? = null, review: Boolean = false): List<AiAction> {
        val now = ZonedDateTime.now(clock)
        val cats = runCatching { categories() }.getOrNull().orEmpty().ifEmpty { DEFAULT_CATEGORIES }
        val schema = ActionJson.schema(cats.map { it.first })
        val user = buildString {
            appendLine("NOW: ${now.toLocalDate()} (${now.dayOfWeek}), ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}, timezone ${now.zone}")
            appendLine("REPLY LANGUAGE for spokenSummary: ${languageName(replyLanguage())}")
            if (focusTaskId != null) appendLine("He is looking at task #$focusTaskId, so 'this', 'it' or 'هذا' mean that task.")
            if (review) {
                appendLine(
                    "This is his DAILY REVIEW: he is telling you what happened with today's tasks. " +
                        "Use set_status on the matching tasks (DONE, PARTIALLY_DONE with progress, IN_PROGRESS, NOT_DONE, POSTPONED) " +
                        "and put his explanation in note. Tasks marked (TODAY) are today's."
                )
            }
            appendLine()
            appendLine("CATEGORIES (use the key in \"category\"):")
            cats.forEach { (key, name) ->
                appendLine("- $key = $name" + (BUILT_IN_HINTS[key]?.let { " ($it)" } ?: ""))
            }
            appendLine()
            appendLine("EXISTING TASKS (#id | title | category | date | status | repeat | reminder):")
            appendLine(context.taskList(focusTaskId).ifBlank { "(none yet)" })
            appendLine()
            context.deletedList().takeIf { it.isNotBlank() }?.let {
                appendLine("RECENTLY DELETED TASKS, newest first (#id | title | category | when deleted):")
                appendLine(it)
                appendLine()
            }
            appendLine("HE SAID:")
            appendLine(transcript.trim())
        }
        val first = ai.generate(AiPurpose.COMMAND, SYSTEM_PROMPT, listOf(GeminiClient.Turn.user(user)), schema, temperature = 0.1)
        val parsed = runCatching { ActionJson.parse(first.text) }
        val actions = parsed.getOrNull()
        if (actions != null && ActionSanity.problems(actions, transcript).isEmpty()) return actions

        // The answer was broken or had the model's thinking in it: ask once more, on another model if possible.
        val again = try {
            val retry = ai.generate(
                AiPurpose.COMMAND, SYSTEM_PROMPT, listOf(GeminiClient.Turn.user(user + "\n" + RETRY_NOTE)), schema,
                temperature = 0.1, avoid = setOf(first.model), budgetMs = RETRY_BUDGET_MS,
            )
            runCatching { ActionJson.parse(retry.text) }.getOrNull()
        } catch (e: AiException) {
            null
        }
        if (again != null && ActionSanity.problems(again, transcript).isEmpty()) return again
        val best = again ?: actions ?: throw (parsed.exceptionOrNull() ?: AiException.BadResponse("no actions"))
        return ActionSanity.repair(best, transcript)
    }

    companion object {
        fun languageName(code: String) = if (code == "en") "English" else "Arabic"

        val DEFAULT_CATEGORIES = listOf("WORK" to "Work", "HOME" to "Home", "PERSONAL" to "Personal", "TRAVEL" to "Travel")

        /** What the built-in categories are for. */
        val BUILT_IN_HINTS = mapOf(
            "WORK" to "business, clients, meetings, office, employees, suppliers, company money",
            "HOME" to "house, family needs, repairs, groceries, home bills",
            "PERSONAL" to "health, doctor, medicine, personal errands, friends, social visits, religion, hobbies, personal money",
            "TRAVEL" to "trips, flights, hotels, visas, passports, packing, airport",
        )

        private const val RETRY_BUDGET_MS = 25_000L

        const val RETRY_NOTE = "IMPORTANT: a previous answer to this was rejected because a field contained reasoning or " +
            "explanations. Every field must hold only its final value. title = only the short task name (2 to 8 words)."

        val SYSTEM_PROMPT = """
You are the brain of "Thakkirni" (ذكّرني), a voice-first to-do app used by an older businessman.
He speaks Arabic (any dialect), sometimes mixed with English or French words. Speech recognition
can contain mistakes, so infer what he most likely meant.

Turn what he said into a JSON list of actions. Action types:
- create_task: a new thing to do. Give a short clear title in his language (2 to 8 words, the thing itself, e.g. "الاتصال بأحمد شماط") without date/time words, optional details, the best category, and date/time/recurrence/reminder if he mentioned them.
- update_task: change anything about an existing task: title, details, category, priority, date, time (with scope). removeDate true / removeTime true take the day / the time off it ("بدون موعد", "شيل الوقت").
- set_status: he reports what happened with an existing task, or asks to change its status. status is one of NOT_STARTED, IN_PROGRESS, PARTIALLY_DONE, DONE, NOT_DONE, POSTPONED, CANCELLED. Put his explanation in note. For PARTIALLY_DONE or IN_PROGRESS give progress 0-100 if he implied one ("half" = 50). Works on finished tasks too: "رجّعها/ما خلصت/افتحها من جديد" on a DONE task = NOT_STARTED (or what he says). Whenever he names a day for a status (امبارح = yesterday, أول أمس, يوم الأحد...), put that day in date as yyyy-MM-dd; leave date empty only when he names no day.
  Status words: خلصت/تم/انتهيت/سويتها = DONE; بديت/شغال عليها/قيد التنفيذ = IN_PROGRESS; نصها/جزء منها/منجز جزئياً = PARTIALLY_DONE; ما سويتها/ما قدرت/لم تنجز = NOT_DONE; أجّلها/مؤجلة = POSTPONED; ألغيها/ملغية/ما عاد لها داعي = CANCELLED; لم تبدأ/ما بديت = NOT_STARTED.
- add_note: add a note to an existing task; keep his words (cleaned up, not shortened).
- set_reminder: turn a reminder on (reminder true) or off (false) for an existing task, optionally with date, time, reminderMinutesBefore and recurrence.
- change_recurrence: change how an existing task repeats (recurrence), or stop repeating (removeRecurrence true).
- postpone: move an existing task to another date and/or time. scope THIS = only this time, ALL_FUTURE = from now on.
- delete_task: remove an existing task completely (only when he clearly asks to delete or forget it).
- restore_task: bring back a task he deleted, using its taskId from RECENTLY DELETED TASKS. "رجّعها", "استرجعها", "تراجع عن الحذف" or "bring it back" right after deleting mean the newest deleted task. Never create a new task for this.
- clear_history: he asks to remove a task from the History/log (taskId), or to clear the whole History (no taskId): "امسحها من السجل", "نظّف السجل", "امسح السجل كله". Only when he says History/log (السجل); deleting a task itself is delete_task.
- ask_question: he is asking about his tasks, notes or history. Put the full question in question. Never answer it yourself.
- unclear: you really cannot tell what he wants. spokenSummary politely asks him to say it again.

Rules:
- One sentence can contain several actions; return all of them in the order he said them.
- When he refers to an existing task (by name, person, place, or "it/this"), use its taskId from EXISTING TASKS (whatever its status). Never invent ids. If nothing matches, create a new task instead.
- He can change anything by voice. Prefer changing an existing task over creating a duplicate of it.
- Categories: put each task in the one category from CATEGORIES that fits it best, judging by the category names (he may have made his own, e.g. "الصحة" or "المزرعة"; prefer a specific one of his over a general one). Use only keys from that list.
- Dates are ISO yyyy-MM-dd computed from NOW; times are 24-hour HH:mm. بكرة/غدا = tomorrow, بعد بكرة = the day after, الأسبوع الجاي = next week, a weekday name = the next such day. Without an exact time: الصبح ≈ 09:00, الظهر ≈ 13:00, العصر ≈ 16:00, المغرب ≈ 18:00, المسا/بالليل ≈ 20:00.
- Recurrence: frequency DAILY/WEEKLY/MONTHLY/YEARLY plus interval ("every two weeks" = WEEKLY interval 2, "every other day" = DAILY interval 2), weekdays as ISO numbers (1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday, 7=Sunday), dayOfMonth for monthly, endDate if he says until when. A repeating task without a start date starts from today.
- "ذكرني / نبهني / remind me" means reminder true. Use the time he gives; if he gives none, leave time empty.
- priority HIGH only when he says it is urgent or important.
- spokenSummary: ONE short friendly sentence in the reply language that says exactly what will be done, read aloud for him to confirm. Example: "سأضيف مهمة: الاتصال بأحمد في العمل، كل أسبوعين يوم الاثنين الساعة العاشرة صباحاً، مع تذكير."
- Output actions only. Do not chat.
- Every field holds only its final value. Never write your reasoning, notes to yourself, English explanations or field names (like "Wait", "Category:" or "Date:") inside any field. If you are unsure about the category, just pick the closest one.
""".trim()
    }
}
