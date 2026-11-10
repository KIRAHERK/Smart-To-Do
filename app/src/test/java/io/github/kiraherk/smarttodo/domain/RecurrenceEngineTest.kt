package io.github.kiraherk.smarttodo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceEngineTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun series(rule: RecurrenceRule, anchor: String, count: Int): List<String> {
        val out = mutableListOf<String>()
        var x = RecurrenceEngine.firstOnOrAfter(rule, d(anchor), d(anchor))
        while (x != null && out.size < count) {
            out += x.toString()
            x = RecurrenceEngine.nextAfter(rule, d(anchor), x)
        }
        return out
    }

    @Test
    fun daily() {
        assertEquals(listOf("2026-11-10", "2026-11-11", "2026-11-12"), series(RecurrenceRule(Frequency.DAILY), "2026-11-10", 3))
    }

    @Test
    fun everyOtherDay() {
        assertEquals(listOf("2026-11-10", "2026-11-12", "2026-11-14"), series(RecurrenceRule(Frequency.DAILY, interval = 2), "2026-11-10", 3))
        // Asking from a date far after the start lands on the right step.
        assertEquals(d("2026-12-10"), RecurrenceEngine.nextAfter(RecurrenceRule(Frequency.DAILY, interval = 2), d("2026-11-10"), d("2026-12-09")))
    }

    @Test
    fun everyTwoWeeksOnMonday() {
        // "Remind me every two weeks to check on Ahmed" starting Monday 9 Nov 2026.
        val rule = RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = listOf(1))
        assertEquals(listOf("2026-11-09", "2026-11-23", "2026-12-07", "2026-12-21"), series(rule, "2026-11-09", 4))
    }

    @Test
    fun weeklyWithoutWeekdaysUsesStartDay() {
        assertEquals(listOf("2026-11-10", "2026-11-17"), series(RecurrenceRule(Frequency.WEEKLY), "2026-11-10", 2))
    }

    @Test
    fun weeklyStartsOnNextMatchingDay() {
        // Created on Tuesday, repeats on Mondays: the first round is the next Monday.
        val rule = RecurrenceRule(Frequency.WEEKLY, weekdays = listOf(1))
        assertEquals(d("2026-11-16"), RecurrenceEngine.firstOnOrAfter(rule, d("2026-11-10"), d("2026-11-10")))
    }

    @Test
    fun newSeriesStartsOnTheNextMatchingDayWhateverTheInterval() {
        // Said on Tuesday 10 Nov: "every two weeks on Monday" starts Monday 16, not 23.
        assertEquals(d("2026-11-16"), RecurrenceEngine.firstStart(RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = listOf(1)), d("2026-11-10")))
        // "Every two months on the 15th" said on 20 Nov starts 15 Dec.
        assertEquals(d("2026-12-15"), RecurrenceEngine.firstStart(RecurrenceRule(Frequency.MONTHLY, interval = 2, dayOfMonth = 15), d("2026-11-20")))
        assertEquals(d("2026-11-10"), RecurrenceEngine.firstStart(RecurrenceRule(Frequency.DAILY, interval = 3), d("2026-11-10")))
    }

    @Test
    fun everyTwoWeeksOnSundayAndTuesdayStayTogether() {
        // Weeks start on Saturday, so Sunday 8 and Tuesday 10 are the same week.
        val rule = RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = listOf(7, 2))
        assertEquals(listOf("2026-11-08", "2026-11-10", "2026-11-22", "2026-11-24"), series(rule, "2026-11-08", 4))
    }

    @Test
    fun monthlyOn31stClampsToShortMonths() {
        val rule = RecurrenceRule(Frequency.MONTHLY)
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), series(rule, "2026-01-31", 4))
    }

    @Test
    fun monthlyOnGivenDay() {
        val rule = RecurrenceRule(Frequency.MONTHLY, dayOfMonth = 15)
        assertEquals(d("2026-12-15"), RecurrenceEngine.firstOnOrAfter(rule, d("2026-11-20"), d("2026-11-20")))
    }

    @Test
    fun yearlyOnLeapDay() {
        val rule = RecurrenceRule(Frequency.YEARLY)
        assertEquals(listOf("2028-02-29", "2029-02-28", "2030-02-28"), series(rule, "2028-02-29", 3))
    }

    @Test
    fun endDateStopsTheSeries() {
        val rule = RecurrenceRule(Frequency.DAILY, endDate = "2026-11-12")
        assertEquals(listOf("2026-11-10", "2026-11-11", "2026-11-12"), series(rule, "2026-11-10", 10))
        assertNull(RecurrenceEngine.nextAfter(rule, d("2026-11-10"), d("2026-11-12")))
    }

    @Test
    fun jsonRoundTripAndNormalisation() {
        val rule = RecurrenceRule(Frequency.WEEKLY, interval = 0, weekdays = listOf(9, 3, 1, 3))
        val back = RecurrenceRule.fromJson(rule.toJson())!!
        assertEquals(1, back.interval)
        assertEquals(listOf(1, 3), back.weekdays)
        assertNull(RecurrenceRule.fromJson("not json"))
        assertNull(RecurrenceRule.fromJson(null))
    }
}
