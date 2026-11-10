package io.github.kiraherk.smarttodo.data.repo

import androidx.room.withTransaction
import io.github.kiraherk.smarttodo.data.db.ActivityEntity
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.db.NoteEntity
import io.github.kiraherk.smarttodo.data.db.OccurrenceEntity
import io.github.kiraherk.smarttodo.data.db.TaskEntity
import io.github.kiraherk.smarttodo.domain.ActivityType
import io.github.kiraherk.smarttodo.domain.ArabicText
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.NoteSource
import io.github.kiraherk.smarttodo.domain.Priority
import io.github.kiraherk.smarttodo.domain.RecurrenceEngine
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.util.Dates
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * All changes to tasks go through here so that every change is written to the activity log
 * (his history) and reminders are re-synced afterwards.
 */
class TaskRepository(
    private val db: AppDatabase,
    private val defaultReminderTime: suspend () -> String,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val onRemindersChanged: suspend () -> Unit = {},
) {
    private val tasks = db.tasks()
    private val occ = db.occurrences()
    private val notes = db.notes()
    private val activity = db.activity()

    fun now(): Long = clock.millis()
    fun today(): LocalDate = LocalDate.now(clock)

    fun observeOpen() = occ.observeOpen(TaskStatus.OPEN)
    fun observeTask(id: Long) = tasks.observe(id)
    fun observeOccurrences(taskId: Long) = occ.observeForTask(taskId)
    fun observeNotes(taskId: Long) = notes.observeForTask(taskId)
    fun observeActivity(taskId: Long) = activity.observeForTask(taskId)
    fun observeHistory(since: Long) = activity.observeSince(since)
    fun observeBetween(from: LocalDate, to: LocalDate) = occ.observeBetween(from.toString(), to.toString())

    suspend fun task(id: Long): TaskEntity? = tasks.get(id)

    data class NewTask(
        val title: String,
        val details: String = "",
        val category: CategoryKey = CategoryKey.PERSONAL,
        val priority: Priority = Priority.NORMAL,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val recurrence: RecurrenceRule? = null,
        val reminder: Boolean = false,
        val reminderMinutesBefore: Int = 0,
        val transcript: String? = null,
        val logText: String? = null,
    )

    suspend fun createTask(spec: NewTask): Long {
        val now = now()
        val anchor = spec.date ?: if (spec.recurrence != null) today() else null
        val firstDate = if (spec.recurrence != null && anchor != null) RecurrenceEngine.firstStart(spec.recurrence, anchor) else anchor
        val time = spec.time?.let(Dates::formatTime)
        val base = TaskEntity(
            title = spec.title.trim(),
            details = spec.details.trim(),
            category = spec.category.name,
            priority = spec.priority.name,
            startDate = firstDate?.toString(),
            time = time,
            recurrenceJson = spec.recurrence?.toJson(),
            reminderEnabled = spec.reminder,
            reminderMinutesBefore = spec.reminderMinutesBefore.coerceIn(0, 7 * 24 * 60),
            sourceTranscript = spec.transcript,
            searchText = searchText(spec.title, spec.details),
            createdAt = now,
            updatedAt = now,
        )
        val id = db.withTransaction {
            val id = tasks.insert(base)
            val task = base.copy(id = id)
            val oid = occ.insert(newOccurrence(task, firstDate, time))
            log(id, oid, ActivityType.CREATED, text = spec.logText ?: spec.title)
            id
        }
        onRemindersChanged()
        return id
    }

    suspend fun updateTask(
        taskId: Long,
        title: String? = null,
        details: String? = null,
        category: CategoryKey? = null,
        priority: Priority? = null,
        logText: String? = null,
    ) {
        val t = tasks.get(taskId) ?: return
        var n = t
        if (!title.isNullOrBlank()) n = n.copy(title = title.trim())
        if (details != null) n = n.copy(details = details.trim())
        if (category != null) n = n.copy(category = category.name)
        if (priority != null) n = n.copy(priority = priority.name)
        if (n == t) return
        n = n.copy(searchText = searchText(n.title, n.details), updatedAt = now())
        db.withTransaction {
            tasks.update(n)
            log(taskId, null, ActivityType.EDIT, text = logText ?: n.title)
        }
    }

    /** The round that a spoken command most likely means: the earliest open one, else the latest. */
    suspend fun currentOccurrence(taskId: Long): OccurrenceEntity? =
        occ.openForTask(taskId, TaskStatus.OPEN).firstOrNull() ?: occ.latestForTask(taskId)

    suspend fun setStatus(occurrenceId: Long, status: TaskStatus, progress: Int? = null, note: String? = null) {
        val o = occ.get(occurrenceId) ?: return
        val task = tasks.get(o.taskId) ?: return
        val now = now()
        val newProgress = when {
            status == TaskStatus.DONE -> 100
            progress != null -> progress.coerceIn(0, 100)
            else -> o.progress
        }
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        db.withTransaction {
            occ.update(
                o.copy(
                    status = status.name,
                    progress = newProgress,
                    statusNote = cleanNote ?: o.statusNote,
                    closedAt = if (status.isOpen) null else now,
                    updatedAt = now,
                )
            )
            log(task.id, o.id, ActivityType.STATUS, from = o.status, to = status.name, text = cleanNote)
            if (cleanNote != null) {
                notes.insert(NoteEntity(taskId = task.id, occurrenceId = o.id, text = cleanNote, searchText = ArabicText.normalize(cleanNote), source = NoteSource.VOICE.name, createdAt = now))
            }
            tasks.update(task.copy(updatedAt = now))
        }
        if (!status.isOpen && task.recurrenceJson != null) ensureCurrent(task)
        onRemindersChanged()
    }

    suspend fun setStatusForTask(taskId: Long, status: TaskStatus, progress: Int? = null, note: String? = null) {
        val o = currentOccurrence(taskId) ?: return
        setStatus(o.id, status, progress, note)
    }

    suspend fun addNote(taskId: Long, text: String, source: NoteSource = NoteSource.VOICE, occurrenceId: Long? = null) {
        val clean = text.trim().takeIf { it.isNotEmpty() } ?: return
        val task = tasks.get(taskId) ?: return
        val now = now()
        val oid = occurrenceId ?: currentOccurrence(taskId)?.id
        db.withTransaction {
            notes.insert(NoteEntity(taskId = taskId, occurrenceId = oid, text = clean, searchText = ArabicText.normalize(clean), source = source.name, createdAt = now))
            log(taskId, oid, ActivityType.NOTE, text = clean)
            tasks.update(task.copy(updatedAt = now))
        }
    }

    /**
     * Moves the current round to a new date/time. With [EditScope.ALL_FUTURE] (or a one-off task)
     * the task's default date/time changes too. [postponed] marks it as postponed in the history.
     */
    suspend fun reschedule(
        taskId: Long,
        date: LocalDate?,
        time: LocalTime?,
        allFuture: Boolean = false,
        postponed: Boolean = false,
        logText: String? = null,
    ) {
        val t = tasks.get(taskId) ?: return
        val o = currentOccurrence(taskId)
        val now = now()
        val newTime = time?.let(Dates::formatTime) ?: o?.time ?: t.time
        val newDate = date ?: Dates.parseDate(o?.date) ?: if (time != null) dateForTime(time) else null
        db.withTransaction {
            var n = t
            if (allFuture || t.recurrenceJson == null) {
                n = n.copy(startDate = newDate?.toString() ?: n.startDate, time = if (time != null) newTime else n.time)
            }
            n = n.copy(updatedAt = now)
            tasks.update(n)
            val oid = if (o != null && o.isOpen) {
                val status = if (postponed && o.status == TaskStatus.NOT_STARTED.name) TaskStatus.POSTPONED.name else o.status
                occ.update(
                    o.copy(
                        date = newDate?.toString(), time = newTime, status = status,
                        remindAt = computeRemindAt(n, newDate, newTime), reminderFiredAt = null, updatedAt = now,
                    )
                )
                o.id
            } else {
                occ.insert(newOccurrence(n, newDate, newTime))
            }
            val from = listOfNotNull(o?.date, o?.time).joinToString(" ").ifEmpty { "—" }
            val to = listOfNotNull(newDate?.toString(), newTime).joinToString(" ").ifEmpty { "—" }
            log(taskId, oid, if (postponed) ActivityType.POSTPONED else ActivityType.EDIT, text = logText ?: "$from → $to")
        }
        onRemindersChanged()
    }

    /** Sets, changes or removes ([rule] = null) how a task repeats. */
    suspend fun changeRecurrence(taskId: Long, rule: RecurrenceRule?, logText: String? = null) {
        val t = tasks.get(taskId) ?: return
        val o = currentOccurrence(taskId)
        val now = now()
        db.withTransaction {
            if (rule == null) {
                tasks.update(t.copy(recurrenceJson = null, updatedAt = now))
            } else {
                val current = Dates.parseDate(o?.takeIf { it.isOpen }?.date)
                val base = if (current == null || current < today()) today() else current
                val first = RecurrenceEngine.firstStart(rule, base)
                val n = t.copy(recurrenceJson = rule.toJson(), startDate = first.toString(), updatedAt = now)
                tasks.update(n)
                if (o != null && o.isOpen) {
                    occ.update(o.copy(date = first.toString(), remindAt = computeRemindAt(n, first, o.time), reminderFiredAt = null, updatedAt = now))
                } else {
                    occ.insert(newOccurrence(n, first, n.time))
                }
            }
            log(taskId, o?.id, ActivityType.EDIT, text = logText ?: (if (rule == null) "—" else rule.frequency.name))
        }
        onRemindersChanged()
    }

    /** Turns the reminder on/off, optionally at a new date/time for the current round. */
    suspend fun setReminder(
        taskId: Long,
        enabled: Boolean,
        minutesBefore: Int? = null,
        date: LocalDate? = null,
        time: LocalTime? = null,
        logText: String? = null,
    ) {
        val t = tasks.get(taskId) ?: return
        val now = now()
        val n = t.copy(
            reminderEnabled = enabled,
            reminderMinutesBefore = minutesBefore?.coerceIn(0, 7 * 24 * 60) ?: t.reminderMinutesBefore,
            time = time?.let(Dates::formatTime) ?: t.time,
            updatedAt = now,
        )
        db.withTransaction {
            tasks.update(n)
            val open = occ.openForTask(taskId, TaskStatus.OPEN)
            if (open.isEmpty() && enabled) {
                val d = date ?: time?.let(::dateForTime) ?: today()
                occ.insert(newOccurrence(n, d, n.time))
            }
            open.forEachIndexed { index, o ->
                val newTime = time?.let(Dates::formatTime) ?: o.time
                val newDate = (if (index == 0) date else null) ?: Dates.parseDate(o.date) ?: if (enabled) (time?.let(::dateForTime) ?: today()) else null
                occ.update(o.copy(date = newDate?.toString(), time = newTime, remindAt = computeRemindAt(n, newDate, newTime), reminderFiredAt = null, updatedAt = now))
            }
            log(taskId, open.firstOrNull()?.id, ActivityType.EDIT, text = logText ?: if (enabled) "🔔" else "🔕")
        }
        onRemindersChanged()
    }

    suspend fun archive(taskId: Long, logText: String? = null) {
        val t = tasks.get(taskId) ?: return
        db.withTransaction {
            tasks.update(t.copy(archived = true, updatedAt = now()))
            log(taskId, null, ActivityType.ARCHIVED, text = logText ?: t.title)
        }
        onRemindersChanged()
    }

    suspend fun restore(taskId: Long, logText: String? = null) {
        val t = tasks.get(taskId) ?: return
        db.withTransaction {
            tasks.update(t.copy(archived = false, updatedAt = now()))
            log(taskId, null, ActivityType.RESTORED, text = logText ?: t.title)
        }
        if (t.recurrenceJson != null) ensureCurrent(t.copy(archived = false))
        onRemindersChanged()
    }

    suspend fun snooze(occurrenceId: Long, minutes: Int) {
        val o = occ.get(occurrenceId) ?: return
        occ.update(o.copy(remindAt = now() + minutes * 60_000L, reminderFiredAt = null, updatedAt = now()))
        onRemindersChanged()
    }

    suspend fun snoozeToTomorrow(occurrenceId: Long) {
        val o = occ.get(occurrenceId) ?: return
        val t = tasks.get(o.taskId) ?: return
        val time = Dates.parseTime(o.time) ?: Dates.parseTime(t.time) ?: Dates.parseTime(defaultReminderTime()) ?: LocalTime.of(9, 0)
        val at = today().plusDays(1).atTime(time).atZone(clock.zone).toInstant().toEpochMilli()
        occ.update(o.copy(remindAt = at, reminderFiredAt = null, updatedAt = now()))
        onRemindersChanged()
    }

    suspend fun markReminderFired(occurrenceId: Long) {
        val o = occ.get(occurrenceId) ?: return
        occ.update(o.copy(reminderFiredAt = now()))
    }

    /** Makes sure every repeating task has its current round. Run daily, at boot and at app start. */
    suspend fun rollForward() {
        tasks.activeRecurring().forEach { ensureCurrent(it) }
        onRemindersChanged()
    }

    /**
     * For a repeating task: when the next round's date has arrived, the previous round is over
     * (marked MISSED if he never marked it) and the new round is created. If the latest round
     * is already closed, the next one is created right away so it shows under "upcoming".
     */
    suspend fun ensureCurrent(task: TaskEntity) {
        val rule = RecurrenceRule.fromJson(task.recurrenceJson) ?: return
        if (task.archived) return
        val today = today()
        val anchor = Dates.parseDate(task.startDate) ?: today
        db.withTransaction {
            var latest = occ.latestForTask(task.id)
            if (latest == null) {
                val first = RecurrenceEngine.firstOnOrAfter(rule, anchor, maxOf(anchor, today)) ?: return@withTransaction
                occ.insert(newOccurrence(task, first, task.time))
                return@withTransaction
            }
            var latestDate = Dates.parseDate(latest.date) ?: return@withTransaction
            val next = RecurrenceEngine.nextAfter(rule, anchor, latestDate)
            if (next != null && next <= today) {
                if (latest.isOpen) {
                    occ.update(latest.copy(status = TaskStatus.MISSED.name, closedAt = now(), updatedAt = now()))
                    log(task.id, latest.id, ActivityType.STATUS, from = latest.status, to = TaskStatus.MISSED.name)
                }
                var d: LocalDate = next
                while (true) {
                    val n = RecurrenceEngine.nextAfter(rule, anchor, d) ?: break
                    if (n > today) break
                    d = n
                }
                val created = newOccurrence(task, d, task.time)
                latest = created.copy(id = occ.insert(created))
                latestDate = d
            }
            if (!latest.isOpen) {
                val n = RecurrenceEngine.nextAfter(rule, anchor, latestDate) ?: return@withTransaction
                occ.insert(newOccurrence(task, n, task.time))
            }
        }
    }

    data class SearchHit(val task: TaskEntity, val note: NoteEntity?)

    /** Arabic-friendly search over task titles/details and notes, best matches first. */
    suspend fun search(query: String): List<SearchHit> {
        val words = ArabicText.keywords(query).ifEmpty { listOf(ArabicText.normalize(query)) }.filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val score = HashMap<Long, Int>()
        val hits = LinkedHashMap<Long, SearchHit>()
        for (w in words) {
            tasks.search(w).forEach { t ->
                score[t.id] = (score[t.id] ?: 0) + 2
                hits.putIfAbsent(t.id, SearchHit(t, null))
            }
            notes.search(w).forEach { n ->
                val t = hits[n.taskId]?.task ?: tasks.get(n.taskId) ?: return@forEach
                score[t.id] = (score[t.id] ?: 0) + 1
                val existing = hits[t.id]
                if (existing == null || existing.note == null) hits[t.id] = SearchHit(t, n)
            }
        }
        return hits.values.sortedByDescending { score[it.task.id] ?: 0 }
    }

    suspend fun computeRemindAt(task: TaskEntity, date: LocalDate?, time: String?): Long? {
        if (!task.reminderEnabled || date == null) return null
        val t = Dates.parseTime(time) ?: Dates.parseTime(task.time) ?: Dates.parseTime(defaultReminderTime()) ?: LocalTime.of(9, 0)
        val at = date.atTime(t).minusMinutes(task.reminderMinutesBefore.toLong()).atZone(clock.zone).toInstant().toEpochMilli()
        // A reminder time that has already passed when it's set would ring instantly; skip that round.
        return at.takeIf { it > now() - 60_000 }
    }

    private suspend fun newOccurrence(task: TaskEntity, date: LocalDate?, time: String?): OccurrenceEntity =
        OccurrenceEntity(taskId = task.id, date = date?.toString(), time = time, remindAt = computeRemindAt(task, date, time), updatedAt = now())

    /** Today if that time is still ahead, otherwise tomorrow. */
    private fun dateForTime(time: LocalTime): LocalDate =
        if (today().atTime(time).atZone(clock.zone).toInstant().toEpochMilli() > now()) today() else today().plusDays(1)

    private suspend fun log(taskId: Long, occurrenceId: Long?, type: ActivityType, from: String? = null, to: String? = null, text: String? = null) {
        activity.insert(ActivityEntity(taskId = taskId, occurrenceId = occurrenceId, type = type.name, fromStatus = from, toStatus = to, text = text, at = now()))
    }

    private fun searchText(title: String, details: String) = ArabicText.normalize("$title $details")
}
