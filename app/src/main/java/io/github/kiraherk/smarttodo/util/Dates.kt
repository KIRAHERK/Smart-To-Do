package io.github.kiraherk.smarttodo.util

import android.content.Context
import io.github.kiraherk.smarttodo.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseTime(value: String?): LocalTime? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { LocalTime.parse(if (v.length == 4 && v[1] == ':') "0$v" else v) }.getOrNull()
    }

    fun parseDate(value: String?): LocalDate? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun formatTime(time: LocalTime): String = time.format(HHMM)

    /** "الثلاثاء 12 نوفمبر" / "Tuesday 12 November", with Today/Tomorrow/Yesterday words. */
    fun friendlyDate(context: Context, date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> context.getString(R.string.today)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy", LocaleHelper.locale(context)))
    }

    fun fullDate(context: Context, date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", LocaleHelper.locale(context)))

    /** 12-hour time with AM/PM words in the app language, e.g. "10:00 ص" / "10:00 AM". */
    fun friendlyTime(context: Context, time: LocalTime): String =
        time.format(DateTimeFormatter.ofPattern("h:mm a", LocaleHelper.locale(context)))

    fun dateTimeOf(context: Context, millis: Long): String {
        val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return friendlyDate(context, dt.toLocalDate()) + " " + friendlyTime(context, dt.toLocalTime())
    }

    fun weekdayName(day: Int, locale: Locale): String =
        java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.FULL, locale)
}
