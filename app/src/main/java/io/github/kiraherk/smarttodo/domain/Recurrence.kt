package io.github.kiraherk.smarttodo.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * How a task repeats. Stored as JSON in the task row, so only ever ADD fields with defaults.
 *
 * @param weekdays ISO day numbers, 1 = Monday ... 7 = Sunday. Empty = same weekday as the start date.
 * @param dayOfMonth for MONTHLY. Null = same day as the start date. Clamped to short months (31 -> 30/28).
 * @param endDate ISO date after which the series stops, or null for forever.
 */
@Serializable
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
    val endDate: String? = null,
) {
    fun normalized(): RecurrenceRule = copy(
        interval = interval.coerceIn(1, 365),
        weekdays = weekdays.filter { it in 1..7 }.distinct().sorted(),
        dayOfMonth = dayOfMonth?.coerceIn(1, 31),
    )

    fun toJson(): String = json.encodeToString(serializer(), normalized())

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(value: String?): RecurrenceRule? =
            value?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer(), it).normalized() }.getOrNull() }
    }
}

/**
 * Pure date maths for repeating tasks. [anchor] is the date the series started (the first round).
 * All results are on or after the anchor, and never after the rule's end date.
 */
object RecurrenceEngine {

    /** Weeks are counted from Saturday, as is usual in the Middle East, so "every 2 weeks on Sun and Tue" stays together. */
    private val WEEK_START = DayOfWeek.SATURDAY

    /** The first date of the series that is on or after [from]. */
    fun firstOnOrAfter(rule: RecurrenceRule, anchor: LocalDate, from: LocalDate): LocalDate? =
        nextAfter(rule, anchor, from.minusDays(1))

    /** The first date of the series strictly after [after]. */
    fun nextAfter(rule: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate? {
        val r = rule.normalized()
        val start = if (after < anchor) anchor.minusDays(1) else after
        val candidate = when (r.frequency) {
            Frequency.DAILY -> daily(r, anchor, start)
            Frequency.WEEKLY -> weekly(r, anchor, start)
            Frequency.MONTHLY -> monthly(r, anchor, start)
            Frequency.YEARLY -> yearly(r, anchor, start)
        }
        val end = r.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return if (end != null && candidate > end) null else candidate
    }

    private fun daily(r: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate {
        val days = ChronoUnit.DAYS.between(anchor, after)
        val k = if (days < 0) 0 else days / r.interval + 1
        return anchor.plusDays(k * r.interval)
    }

    private fun weekly(r: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate {
        val days = if (r.weekdays.isEmpty()) setOf(anchor.dayOfWeek.value) else r.weekdays.toSet()
        val anchorWeek = anchor.with(TemporalAdjusters.previousOrSame(WEEK_START))
        var d = after.plusDays(1)
        repeat(7 * r.interval + 14) {
            val week = ChronoUnit.WEEKS.between(anchorWeek, d.with(TemporalAdjusters.previousOrSame(WEEK_START)))
            if (week >= 0 && week % r.interval == 0L && d.dayOfWeek.value in days && d >= anchor) return d
            d = d.plusDays(1)
        }
        return after.plusWeeks(r.interval.toLong()) // not reachable with valid input
    }

    private fun monthly(r: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate {
        val dom = r.dayOfMonth ?: anchor.dayOfMonth
        val anchorMonth = YearMonth.from(anchor)
        var k = maxOf(0L, ChronoUnit.MONTHS.between(anchorMonth, YearMonth.from(after)) / r.interval)
        while (true) {
            val ym = anchorMonth.plusMonths(k * r.interval)
            val date = ym.atDay(minOf(dom, ym.lengthOfMonth()))
            if (date > after && date >= anchor) return date
            k++
        }
    }

    private fun yearly(r: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate {
        var k = maxOf(0L, (after.year - anchor.year).toLong() / r.interval)
        while (true) {
            val ym = YearMonth.of(anchor.year + (k * r.interval).toInt(), anchor.month)
            val date = ym.atDay(minOf(anchor.dayOfMonth, ym.lengthOfMonth()))
            if (date > after && date >= anchor) return date
            k++
        }
    }
}
