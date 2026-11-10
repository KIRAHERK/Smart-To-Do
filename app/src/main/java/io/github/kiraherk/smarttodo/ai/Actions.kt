package io.github.kiraherk.smarttodo.ai

import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.Priority
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ActionType {
    const val CREATE = "create_task"
    const val UPDATE = "update_task"
    const val SET_STATUS = "set_status"
    const val ADD_NOTE = "add_note"
    const val SET_REMINDER = "set_reminder"
    const val CHANGE_RECURRENCE = "change_recurrence"
    const val POSTPONE = "postpone"
    const val DELETE = "delete_task"
    const val ASK = "ask_question"
    const val UNCLEAR = "unclear"

    val ALL = listOf(CREATE, UPDATE, SET_STATUS, ADD_NOTE, SET_REMINDER, CHANGE_RECURRENCE, POSTPONE, DELETE, ASK, UNCLEAR)
    val CHANGES = ALL - setOf(ASK, UNCLEAR)
}

@Serializable
data class AiRecurrence(
    val frequency: String = "WEEKLY",
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
    val endDate: String? = null,
) {
    fun toRule(): RecurrenceRule? = Frequency.parse(frequency)?.let {
        RecurrenceRule(it, interval.coerceAtLeast(1), weekdays, dayOfMonth, endDate?.takeIf { d -> d.isNotBlank() }).normalized()
    }
}

/** One thing the AI understood from his words. Flat on purpose: simple for the model to fill in. */
@Serializable
data class AiAction(
    val type: String,
    val taskId: Long? = null,
    val title: String? = null,
    val details: String? = null,
    val category: String? = null,
    val priority: String? = null,
    val date: String? = null,
    val time: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val note: String? = null,
    val reminder: Boolean? = null,
    val reminderMinutesBefore: Int? = null,
    val recurrence: AiRecurrence? = null,
    val removeRecurrence: Boolean? = null,
    val scope: String? = null,
    val question: String? = null,
    val spokenSummary: String = "",
)

@Serializable
data class AiActions(val actions: List<AiAction> = emptyList())

object ActionJson {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun parse(text: String): List<AiAction> {
        val clean = text.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException.BadResponse("no json object")
        return runCatching { json.decodeFromString(AiActions.serializer(), clean.substring(start, end + 1)).actions }
            .getOrElse { throw AiException.BadResponse(it.message ?: "bad json") }
            .filter { it.type in ActionType.ALL }
    }

    /** Response schema in the OpenAPI subset the Gemini API accepts. */
    val schema: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("actions") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        string("type", nullable = false, enum = ActionType.ALL)
                        integer("taskId")
                        string("title")
                        string("details")
                        string("category", enum = CategoryKey.entries.map { it.name })
                        string("priority", enum = Priority.entries.map { it.name })
                        string("date")
                        string("time")
                        string("status", enum = TaskStatus.entries.filter { it != TaskStatus.MISSED }.map { it.name })
                        integer("progress")
                        string("note")
                        boolean("reminder")
                        integer("reminderMinutesBefore")
                        putJsonObject("recurrence") {
                            put("type", "OBJECT")
                            put("nullable", true)
                            putJsonObject("properties") {
                                string("frequency", nullable = false, enum = Frequency.entries.map { it.name })
                                put("interval", buildJsonObject { put("type", "INTEGER") })
                                putJsonObject("weekdays") {
                                    put("type", "ARRAY")
                                    putJsonObject("items") { put("type", "INTEGER") }
                                }
                                integer("dayOfMonth")
                                string("endDate")
                            }
                            putJsonArray("required") { add("frequency"); add("interval") }
                        }
                        boolean("removeRecurrence")
                        string("scope", enum = listOf("THIS", "ALL_FUTURE"))
                        string("question")
                        string("spokenSummary", nullable = false)
                    }
                    putJsonArray("required") { add("type"); add("spokenSummary") }
                }
            }
        }
        putJsonArray("required") { add("actions") }
    }

    private fun JsonObjectBuilder.string(name: String, nullable: Boolean = true, enum: List<String>? = null) {
        putJsonObject(name) {
            put("type", "STRING")
            if (nullable) put("nullable", true)
            if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
        }
    }

    private fun JsonObjectBuilder.integer(name: String) {
        putJsonObject(name) { put("type", "INTEGER"); put("nullable", true) }
    }

    private fun JsonObjectBuilder.boolean(name: String) {
        putJsonObject(name) { put("type", "BOOLEAN"); put("nullable", true) }
    }
}
