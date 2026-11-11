package io.github.kiraherk.smarttodo.ai

import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.db.OccurrenceEntity
import io.github.kiraherk.smarttodo.data.db.TaskEntity
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Builds the compact text the AI sees about his tasks. Kept small so each request stays fast
 * and inside the free quota.
 */
class ContextBuilder(
    private val db: AppDatabase,
    private val repo: TaskRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {

    /** One line per task that is open or was touched in the last two weeks. */
    suspend fun taskList(focusTaskId: Long? = null, limit: Int = 150): String {
        val today = repo.today()
        val open = db.occurrences().open(TaskStatus.OPEN)
        val firstOpen = LinkedHashMap<Long, Pair<TaskEntity, OccurrenceEntity>>()
        open.forEach { firstOpen.putIfAbsent(it.task.id, it.task to it.occurrence) }
        val recentClosed = db.occurrences().between(today.minusDays(14).toString(), today.toString())
            .filter { !it.occurrence.isOpen && it.task.id !in firstOpen }
            .associateBy { it.task.id }
            .values.map { it.task to it.occurrence }
        val all = (firstOpen.values + recentClosed)
            .sortedWith(compareBy<Pair<TaskEntity, OccurrenceEntity>> { if (it.first.id == focusTaskId) 0 else 1 }.thenBy { it.second.date ?: "9999" })
            .take(limit)
        return all.joinToString("\n") { (t, o) ->
            val marker = when {
                t.id == focusTaskId -> " (HE IS LOOKING AT THIS)"
                o.date == today.toString() -> " (TODAY)"
                else -> ""
            }
            line(t, o) + marker
        }
    }

    /** Tasks he deleted, newest first, so "bring it back" can find them. */
    suspend fun deletedList(limit: Int = 15): String = db.tasks().recentlyDeleted(limit).joinToString("\n") { t ->
        val at = Instant.ofEpochMilli(t.updatedAt).atZone(zone())
        "#${t.id} | ${t.title} | ${t.category} | deleted ${at.toLocalDate()} ${"%02d:%02d".format(at.hour, at.minute)}"
    }

    /** Everything relevant for answering a question: open tasks, recent history, notes, and older matches. */
    suspend fun assistantDigest(question: String, maxChars: Int = 60_000): String {
        val today = repo.today()
        val open = db.occurrences().open(TaskStatus.OPEN)
        val since = today.minusDays(90).atStartOfDay(zone()).toInstant().toEpochMilli()
        val activity = db.activity().since(since)
        val hits = repo.search(question).take(12)

        val openSection = buildString {
            appendLine("OPEN TASKS (#id | title | category | date | status | repeat | reminder):")
            if (open.isEmpty()) appendLine("(none)")
            open.forEach { appendLine(line(it.task, it.occurrence)) }
        }
        val matchSection = buildString {
            if (hits.isNotEmpty()) {
                appendLine("TASKS MATCHING THE QUESTION, WITH ALL THEIR NOTES:")
                hits.forEach { hit ->
                    val t = hit.task
                    appendLine("#${t.id} ${t.title} [${t.category}]${if (t.archived) " (deleted)" else ""} created ${day(t.createdAt)}")
                    if (t.details.isNotBlank()) appendLine("  details: ${t.details}")
                    db.notes().forTask(t.id).take(15).forEach { n -> appendLine("  note ${day(n.createdAt)}: ${n.text}") }
                }
            }
        }
        val activityLines = activity.map { a ->
            val x = a.activity
            val change = if (x.toStatus != null) " ${x.fromStatus ?: ""}→${x.toStatus}" else ""
            "${day(x.at)} | #${a.task.id} ${a.task.title} [${a.task.category}] | ${x.type}$change${x.text?.let { " | $it" } ?: ""}"
        }
        val budget = maxChars - openSection.length - matchSection.length - 200
        val kept = ArrayDeque<String>()
        var used = 0
        for (l in activityLines.asReversed()) {
            if (used + l.length + 1 > budget) break
            kept.addFirst(l)
            used += l.length + 1
        }
        return buildString {
            append(openSection)
            appendLine()
            appendLine("HISTORY, LAST 90 DAYS (date | task | what happened):")
            if (kept.isEmpty()) appendLine("(nothing yet)")
            kept.forEach { appendLine(it) }
            appendLine()
            append(matchSection)
        }
    }

    private fun line(t: TaskEntity, o: OccurrenceEntity): String {
        val rule = RecurrenceRule.fromJson(t.recurrenceJson)
        val repeat = rule?.let { r ->
            buildString {
                append("${r.frequency} every ${r.interval}")
                if (r.weekdays.isNotEmpty()) append(" weekdays ${r.weekdays}")
                r.dayOfMonth?.let { append(" day $it") }
                r.endDate?.let { append(" until $it") }
            }
        } ?: "no"
        val progress = if (o.progress in 1..99) " ${o.progress}%" else ""
        return "#${t.id} | ${t.title}${if (t.details.isNotBlank()) " (${t.details.take(80)})" else ""} | ${t.category} | " +
            "${o.date ?: "no date"}${o.time?.let { " $it" } ?: ""} | ${o.status}$progress | repeat: $repeat | reminder: ${if (t.reminderEnabled) "on" else "off"}"
    }

    private fun day(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().toString()
}
