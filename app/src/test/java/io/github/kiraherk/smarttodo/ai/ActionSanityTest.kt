package io.github.kiraherk.smarttodo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionSanityTest {
    private val heard = "ذكرني بكرة الساعة 12 الظهر اتصل باحمد شماط"

    /** What a real phone got as a task name (v0.1.2). */
    private val leaked = "...نضعها WORK. Wait, let's look at the title: الاتصال باحمد شماط. Category: WORK. " +
        "Date: 2026-11-12. Time: 12:00. Reminder: true. Priority: NORMAL"

    @Test
    fun spotsReasoningInTheTitle() {
        assertTrue(ActionSanity.badTitle(leaked, heard))
        val problems = ActionSanity.problems(listOf(AiAction(ActionType.CREATE, title = leaked, spokenSummary = "سأضيف مهمة")), heard)
        assertEquals(listOf("action 1: title"), problems)
    }

    @Test
    fun normalTitlesPass() {
        listOf("الاتصال باحمد شماط", "حجز تذاكر دبي", "Meeting مع فريق المبيعات", "تجديد الجواز").forEach {
            assertFalse(it, ActionSanity.badTitle(it, heard))
        }
        val ok = AiAction(
            ActionType.CREATE, title = "الاتصال باحمد شماط", category = "WORK", date = "2026-11-12", time = "12:00", reminder = true,
            spokenSummary = "سأضيف مهمة: الاتصال باحمد شماط غداً الساعة 12 ظهراً مع تذكير.",
        )
        assertTrue(ActionSanity.problems(listOf(ok), heard).isEmpty())
    }

    @Test
    fun englishWordsHeSaidHimselfAreFine() {
        val said = "add a reminder to check the title deed"
        assertFalse(ActionSanity.badTitle("check the title deed", said))
    }

    @Test
    fun longOrMultiLineTextIsSuspicious() {
        assertTrue(ActionSanity.badTitle("كلمة ".repeat(20), heard))
        assertTrue(ActionSanity.looksLikeReasoning("سطر\nسطر آخر", heard))
        assertTrue(ActionSanity.badTitle("اجتماع 2026-11-12", heard))
    }

    @Test
    fun repairKeepsTheRealName() {
        assertEquals("الاتصال باحمد شماط", ActionSanity.cleanTitle(leaked, heard))
        val fixed = ActionSanity.repair(
            listOf(AiAction(ActionType.CREATE, title = leaked, details = "Wait, maybe PERSONAL", spokenSummary = "Okay so the user wants...")),
            heard,
        ).single()
        assertEquals("الاتصال باحمد شماط", fixed.title)
        assertEquals(null, fixed.details)
        assertEquals("", fixed.spokenSummary)
    }

    @Test
    fun repairFallsBackToWhatHeSaid() {
        assertEquals("ذكرني بكرة الساعة 12 الظهر اتصل باحمد شماط", ActionSanity.cleanTitle("Wait, let me think about this one", heard))
    }
}
