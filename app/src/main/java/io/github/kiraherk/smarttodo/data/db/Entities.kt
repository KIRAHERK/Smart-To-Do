package io.github.kiraherk.smarttodo.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.kiraherk.smarttodo.domain.TaskStatus
import kotlinx.serialization.Serializable

/*
 * DATABASE RULES (so updates never lose data):
 *  - Never rename or delete a column/table. Add new columns with defaults.
 *  - Every change bumps AppDatabase.version and adds a Migration in AppDatabase.MIGRATIONS
 *    plus a test in MigrationTest. Schemas are exported to app/schemas and committed.
 *  - Enums are stored by name as TEXT.
 *  - These classes are also the backup file format (BackupManager), so keep them @Serializable.
 */

/** A thing to do. Repeating tasks have a [recurrenceJson]; each round is an [OccurrenceEntity]. */
@Serializable
@Entity(tableName = "tasks", indices = [Index("archived")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    /** [io.github.kiraherk.smarttodo.domain.CategoryKey] name. */
    val category: String = "PERSONAL",
    val priority: String = "NORMAL",
    /** ISO date of the first round (anchor for repeats), or null for "no date". */
    val startDate: String? = null,
    /** "HH:mm" default time of day, or null. */
    val time: String? = null,
    val recurrenceJson: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderMinutesBefore: Int = 0,
    /** What he actually said when creating it. */
    val sourceTranscript: String? = null,
    /** Normalised title + details, for Arabic-friendly search. */
    val searchText: String = "",
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One round of a task (a one-off task has exactly one). Holds the status, so history is kept per round. */
@Serializable
@Entity(
    tableName = "occurrences",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId"), Index("date"), Index("status")],
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /** ISO date or null for "no date". */
    val date: String?,
    /** "HH:mm" or null. */
    val time: String?,
    val status: String = TaskStatus.NOT_STARTED.name,
    /** 0-100, used with PARTIALLY_DONE / IN_PROGRESS. */
    val progress: Int = 0,
    val statusNote: String? = null,
    /** Epoch millis when the reminder should ring, or null. */
    val remindAt: Long? = null,
    val reminderFiredAt: Long? = null,
    val closedAt: Long? = null,
    val updatedAt: Long,
) {
    val isOpen: Boolean get() = TaskStatus.parse(status)?.isOpen ?: true
}

@Serializable
@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId"), Index("createdAt")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val occurrenceId: Long? = null,
    val text: String,
    val searchText: String = "",
    val source: String = "VOICE",
    val createdAt: Long,
)

/** Everything that ever happened to a task: the history he never had on paper. */
@Serializable
@Entity(
    tableName = "activity",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId"), Index("at")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val occurrenceId: Long? = null,
    /** [io.github.kiraherk.smarttodo.domain.ActivityType] name. */
    val type: String,
    val fromStatus: String? = null,
    val toStatus: String? = null,
    val text: String? = null,
    val at: Long,
)

/** Something he said that couldn't be understood yet (no internet / no key / quota). Never lost. */
@Serializable
@Entity(tableName = "inbox")
data class InboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val state: String = "PENDING",
    val error: String? = null,
    val attempts: Int = 0,
)

@Serializable
@Entity(tableName = "chat_messages", indices = [Index("at")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [io.github.kiraherk.smarttodo.domain.ChatRole] name. */
    val role: String,
    val text: String,
    val at: Long,
)

data class OccurrenceWithTask(
    @Embedded val occurrence: OccurrenceEntity,
    @Relation(parentColumn = "taskId", entityColumn = "id") val task: TaskEntity,
)

data class ActivityWithTask(
    @Embedded val activity: ActivityEntity,
    @Relation(parentColumn = "taskId", entityColumn = "id") val task: TaskEntity,
)
