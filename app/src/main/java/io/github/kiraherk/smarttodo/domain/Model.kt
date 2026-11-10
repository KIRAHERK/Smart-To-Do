package io.github.kiraherk.smarttodo.domain

/**
 * Status of one occurrence of a task. "Open" statuses still need attention;
 * closed ones are finished (in one way or another) and stay in the history forever.
 * Stored in the database by [name], so never rename a constant.
 */
enum class TaskStatus(val isOpen: Boolean) {
    NOT_STARTED(true),
    IN_PROGRESS(true),
    PARTIALLY_DONE(true),
    POSTPONED(true),
    DONE(false),
    NOT_DONE(false),
    CANCELLED(false),
    /** Set automatically when a repeating task's next round arrives and this one was never marked. */
    MISSED(false);

    companion object {
        val OPEN: List<String> = entries.filter { it.isOpen }.map { it.name }
        fun parse(value: String?): TaskStatus? = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

/** The four life areas he asked for. Stored by [name]. */
enum class CategoryKey {
    WORK, HOME, PERSONAL, TRAVEL;

    companion object {
        fun parse(value: String?): CategoryKey? = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class Priority {
    NORMAL, HIGH;

    companion object {
        fun parse(value: String?): Priority? = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class Frequency {
    DAILY, WEEKLY, MONTHLY, YEARLY;

    companion object {
        fun parse(value: String?): Frequency? = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class ActivityType { CREATED, STATUS, NOTE, EDIT, POSTPONED, ARCHIVED, RESTORED }

enum class NoteSource { VOICE, TEXT }

enum class InboxState { PENDING, FAILED }

enum class ChatRole { USER, ASSISTANT }

/** Whether an edit to a repeating task applies to the current round only or to the whole series. */
enum class EditScope {
    THIS, ALL_FUTURE;

    companion object {
        fun parse(value: String?): EditScope = if (value.equals("ALL_FUTURE", ignoreCase = true)) ALL_FUTURE else THIS
    }
}
