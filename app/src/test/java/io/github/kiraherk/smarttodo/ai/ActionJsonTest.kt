package io.github.kiraherk.smarttodo.ai

import io.github.kiraherk.smarttodo.domain.Frequency
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionJsonTest {
    @Test
    fun parsesTypicalGeminiReply() {
        val reply = """
            ```json
            {"actions":[
              {"type":"create_task","title":"الاتصال بأحمد","category":"WORK","date":"2026-11-16","time":"10:00",
               "reminder":true,"recurrence":{"frequency":"WEEKLY","interval":2,"weekdays":[1]},
               "spokenSummary":"سأضيف مهمة: الاتصال بأحمد كل أسبوعين يوم الاثنين","somethingNew":42},
              {"type":"set_status","taskId":"12","status":"PARTIALLY_DONE","progress":50,"note":"بقي الدفع","spokenSummary":"..."},
              {"type":"dance","spokenSummary":"ignored"}
            ]}
            ```
        """.trimIndent()
        val actions = ActionJson.parse(reply)
        assertEquals(2, actions.size)
        val create = actions[0]
        assertEquals(ActionType.CREATE, create.type)
        assertEquals("الاتصال بأحمد", create.title)
        assertEquals(true, create.reminder)
        val rule = create.recurrence!!.toRule()!!
        assertEquals(Frequency.WEEKLY, rule.frequency)
        assertEquals(2, rule.interval)
        assertEquals(listOf(1), rule.weekdays)
        assertEquals(12L, actions[1].taskId)
        assertEquals(50, actions[1].progress)
    }

    @Test
    fun badRecurrenceIsIgnored() {
        assertNull(AiRecurrence(frequency = "SOMETIMES").toRule())
    }

    @Test(expected = AiException.BadResponse::class)
    fun garbageThrows() {
        ActionJson.parse("sorry, I can't")
    }

    @Test
    fun schemaRequiresTypeAndSummary() {
        val items = ActionJson.schema["properties"]!!.jsonObject["actions"]!!.jsonObject["items"]!!.jsonObject
        assertTrue(items["required"].toString().contains("spokenSummary"))
        assertTrue(items["properties"]!!.jsonObject.containsKey("recurrence"))
    }
}
