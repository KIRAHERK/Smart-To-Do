package io.github.kiraherk.smarttodo.ai

import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Turns one spoken sentence into a list of [AiAction]s with a single Gemini request. */
class CommandInterpreter(
    private val ai: AiService,
    private val context: ContextBuilder,
    private val clock: Clock,
    private val replyLanguage: () -> String,
) {
    suspend fun interpret(transcript: String, focusTaskId: Long? = null, review: Boolean = false): List<AiAction> {
        val now = ZonedDateTime.now(clock)
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
            appendLine("EXISTING TASKS (#id | title | category | date | status | repeat | reminder):")
            appendLine(context.taskList(focusTaskId).ifBlank { "(none yet)" })
            appendLine()
            appendLine("HE SAID:")
            appendLine(transcript.trim())
        }
        val text = ai.generate(AiPurpose.COMMAND, SYSTEM_PROMPT, listOf(GeminiClient.Turn.user(user)), ActionJson.schema, temperature = 0.1)
        return ActionJson.parse(text)
    }

    companion object {
        fun languageName(code: String) = if (code == "en") "English" else "Arabic"

        val SYSTEM_PROMPT = """
You are the brain of "Smart To-Do", a voice-first to-do app used by an older businessman.
He speaks Arabic (any dialect), sometimes mixed with English or French words. Speech recognition
can contain mistakes, so infer what he most likely meant.

Turn what he said into a JSON list of actions. Action types:
- create_task: a new thing to do. Give a short clear title in his language without date/time words, optional details, the best category, and date/time/recurrence/reminder if he mentioned them.
- update_task: change the title, details, category or priority of an existing task (date/time changes go in date/time with scope).
- set_status: he reports what happened with an existing task. status is one of NOT_STARTED, IN_PROGRESS, PARTIALLY_DONE, DONE, NOT_DONE, POSTPONED, CANCELLED. Put his explanation in note. For PARTIALLY_DONE or IN_PROGRESS give progress 0-100 if he implied one ("half" = 50).
- add_note: add a note to an existing task; keep his words (cleaned up, not shortened).
- set_reminder: turn a reminder on (reminder true) or off (false) for an existing task, optionally with date, time, reminderMinutesBefore and recurrence.
- change_recurrence: change how an existing task repeats (recurrence), or stop repeating (removeRecurrence true).
- postpone: move an existing task to another date and/or time. scope THIS = only this time, ALL_FUTURE = from now on.
- delete_task: remove an existing task completely (only when he clearly asks to delete or forget it).
- ask_question: he is asking about his tasks, notes or history. Put the full question in question. Never answer it yourself.
- unclear: you really cannot tell what he wants. spokenSummary politely asks him to say it again.

Rules:
- One sentence can contain several actions; return all of them in the order he said them.
- When he refers to an existing task (by name, person, place, or "it/this"), use its taskId from EXISTING TASKS. Never invent ids. If nothing matches, create a new task instead.
- Categories: WORK = business, clients, meetings, office, employees, suppliers, company money. HOME = house, family needs, repairs, groceries, home bills. PERSONAL = health, doctor, medicine, personal errands, friends, social visits, religion, hobbies, personal money. TRAVEL = trips, flights, hotels, visas, passports, packing, airport.
- Dates are ISO yyyy-MM-dd computed from NOW; times are 24-hour HH:mm. بكرة/غدا = tomorrow, بعد بكرة = the day after, الأسبوع الجاي = next week, a weekday name = the next such day. Without an exact time: الصبح ≈ 09:00, الظهر ≈ 13:00, العصر ≈ 16:00, المغرب ≈ 18:00, المسا/بالليل ≈ 20:00.
- Recurrence: frequency DAILY/WEEKLY/MONTHLY/YEARLY plus interval ("every two weeks" = WEEKLY interval 2, "every other day" = DAILY interval 2), weekdays as ISO numbers (1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday, 7=Sunday), dayOfMonth for monthly, endDate if he says until when. A repeating task without a start date starts from today.
- "ذكرني / نبهني / remind me" means reminder true. Use the time he gives; if he gives none, leave time empty.
- priority HIGH only when he says it is urgent or important.
- spokenSummary: ONE short friendly sentence in the reply language that says exactly what will be done, read aloud for him to confirm. Example: "سأضيف مهمة: الاتصال بأحمد في العمل، كل أسبوعين يوم الاثنين الساعة العاشرة صباحاً، مع تذكير."
- Output actions only. Do not chat.
""".trim()
    }
}
