package io.github.kiraherk.smarttodo.ai

import io.github.kiraherk.smarttodo.data.repo.CategoryRepository
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.EditScope
import io.github.kiraherk.smarttodo.domain.NoteSource
import io.github.kiraherk.smarttodo.domain.Priority
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.util.Dates

/** Applies the actions he confirmed. Every change ends up in the history via [TaskRepository]. */
class ActionExecutor(private val repo: TaskRepository, private val categories: CategoryRepository? = null) {

    /** A category key that exists (his own ones included); Personal if the AI gave none. */
    private suspend fun categoryFor(raw: String?): String =
        categories?.resolve(raw) ?: (CategoryKey.parse(raw)?.name ?: CategoryKey.PERSONAL.name)

    private suspend fun categoryOrNull(raw: String?): String? =
        if (raw.isNullOrBlank()) null else categories?.existing(raw) ?: CategoryKey.parse(raw)?.name

    data class Result(val applied: Int, val failed: Int, val taskIds: List<Long>)

    suspend fun apply(actions: List<AiAction>, transcript: String?): Result {
        var applied = 0
        var failed = 0
        val ids = mutableListOf<Long>()
        for (action in actions) {
            val id = runCatching { applyOne(action, transcript) }.getOrNull()
            if (id != null) {
                applied++
                ids += id
            } else {
                failed++
            }
        }
        return Result(applied, failed, ids)
    }

    /** Returns the id of the task it changed, or null if the action couldn't be applied. */
    private suspend fun applyOne(a: AiAction, transcript: String?): Long? {
        val summary = a.spokenSummary.takeIf { it.isNotBlank() }
        val date = Dates.parseDate(a.date)
        val time = Dates.parseTime(a.time)
        return when (a.type) {
            ActionType.CREATE -> {
                val title = a.title?.takeIf { it.isNotBlank() } ?: transcript?.take(80)?.takeIf { it.isNotBlank() } ?: return null
                repo.createTask(
                    TaskRepository.NewTask(
                        title = title,
                        details = a.details.orEmpty(),
                        category = categoryFor(a.category),
                        priority = Priority.parse(a.priority) ?: Priority.NORMAL,
                        date = date,
                        time = time,
                        recurrence = a.recurrence?.toRule(),
                        reminder = a.reminder == true,
                        reminderMinutesBefore = a.reminderMinutesBefore ?: 0,
                        transcript = transcript,
                    )
                )
            }
            ActionType.UPDATE -> {
                val id = existing(a) ?: return null
                repo.updateTask(id, a.title, a.details, categoryOrNull(a.category), Priority.parse(a.priority), summary)
                if (a.removeDate == true || a.removeTime == true) {
                    repo.clearSchedule(id, clearDate = a.removeDate == true && date == null, clearTime = a.removeTime == true && time == null, logText = summary)
                }
                if (date != null || time != null) {
                    repo.reschedule(id, date, time, allFuture = EditScope.parse(a.scope) == EditScope.ALL_FUTURE, postponed = false, logText = summary)
                }
                id
            }
            ActionType.SET_STATUS -> {
                val id = existing(a) ?: return null
                val status = TaskStatus.parse(a.status) ?: return null
                if (status == TaskStatus.POSTPONED && (date != null || time != null)) {
                    repo.reschedule(id, date, time, postponed = true, logText = a.note ?: summary)
                } else {
                    repo.setStatusForTask(id, status, a.progress, a.note, date ?: SpokenDays.pastDay(transcript, repo.today()))
                }
                id
            }
            ActionType.ADD_NOTE -> {
                val id = existing(a) ?: return null
                val text = a.note?.takeIf { it.isNotBlank() } ?: a.details?.takeIf { it.isNotBlank() } ?: return null
                repo.addNote(id, text, NoteSource.VOICE)
                id
            }
            ActionType.SET_REMINDER -> {
                val id = existing(a) ?: return null
                repo.setReminder(id, enabled = a.reminder ?: true, minutesBefore = a.reminderMinutesBefore, date = date, time = time, logText = summary)
                a.recurrence?.toRule()?.let { repo.changeRecurrence(id, it, summary) }
                id
            }
            ActionType.CHANGE_RECURRENCE -> {
                val id = existing(a) ?: return null
                val rule = if (a.removeRecurrence == true) null else a.recurrence?.toRule() ?: return null
                repo.changeRecurrence(id, rule, summary)
                id
            }
            ActionType.POSTPONE -> {
                val id = existing(a) ?: return null
                if (date == null && time == null) return null
                repo.reschedule(id, date, time, allFuture = EditScope.parse(a.scope) == EditScope.ALL_FUTURE, postponed = true, logText = summary)
                id
            }
            ActionType.DELETE -> {
                val id = existing(a) ?: return null
                repo.archive(id, summary)
                id
            }
            ActionType.CLEAR_HISTORY -> {
                val id = a.taskId
                if (id == null) {
                    repo.clearHistory()
                    0L
                } else {
                    repo.task(id) ?: return null
                    repo.removeFromHistory(listOf(TaskRepository.HistoryTarget(id)))
                    id
                }
            }
            ActionType.RESTORE -> {
                val id = existing(a) ?: return null
                if (repo.task(id)?.archived != true) return id
                repo.restore(id, summary)
                id
            }
            else -> null
        }
    }

    private suspend fun existing(a: AiAction): Long? = a.taskId?.takeIf { repo.task(it) != null }
}

/** "Yesterday" and "the day before" in what he said, for reports about a repeating task's earlier round. */
object SpokenDays {
    private val dayBefore = Regex("(أول|قبل) ?(امبارح|أمس|البارحة|البارح)")
    private val yesterday = Regex("امبارح|إمبارح|أمس|امس|البارحة|البارح|yesterday", RegexOption.IGNORE_CASE)

    fun pastDay(transcript: String?, today: java.time.LocalDate): java.time.LocalDate? {
        val t = transcript ?: return null
        return when {
            dayBefore.containsMatchIn(t) -> today.minusDays(2)
            yesterday.containsMatchIn(t) -> today.minusDays(1)
            else -> null
        }
    }
}
