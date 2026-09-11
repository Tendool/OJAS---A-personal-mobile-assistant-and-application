package com.ojas.assistant.core

import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.data.local.entity.RepeatRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * All date maths in Ojas funnels through here so alarms, reminders and the daily
 * rollups agree on where a day starts: the device's current zone, re-read on every
 * call so a timezone change is picked up without restarting anything.
 */
object TimeUtils {

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun now(): Long = System.currentTimeMillis()

    fun todayEpochDay(): Long = LocalDate.now(zone).toEpochDay()

    fun epochDayOf(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun startOfDayMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMillis(epochDay: Long): Long = startOfDayMillis(epochDay + 1) - 1

    fun localDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun toMillis(date: LocalDate, time: LocalTime): Long = toMillis(LocalDateTime.of(date, time))

    // ------------------------------------------------------------- formatting

    private val timeFmt12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val timeFmt24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val fullDayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    fun formatTime(millis: Long, use24h: Boolean): String =
        localDateTime(millis).format(if (use24h) timeFmt24 else timeFmt12)

    fun formatTime(hour: Int, minute: Int, use24h: Boolean): String =
        LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .format(if (use24h) timeFmt24 else timeFmt12)

    fun formatDay(millis: Long): String = localDateTime(millis).format(dayFmt)

    fun formatFullDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(fullDayFmt)

    fun formatMonth(date: LocalDate): String = date.format(monthFmt)

    /** "in 7 min", "in 2h 15m", "tomorrow 06:30", "3 days ago". */
    fun relative(target: Long, now: Long = now(), use24h: Boolean = false): String {
        val delta = target - now
        val absDelta = abs(delta)
        val future = delta >= 0
        return when {
            absDelta < 60_000L -> if (future) "in under a minute" else "just now"
            absDelta < 3_600_000L -> {
                val m = absDelta / 60_000L
                if (future) "in $m min" else "$m min ago"
            }
            absDelta < 86_400_000L -> {
                val h = absDelta / 3_600_000L
                val m = (absDelta % 3_600_000L) / 60_000L
                val body = if (m == 0L) "${h}h" else "${h}h ${m}m"
                if (future) "in $body" else "$body ago"
            }
            else -> {
                val days = epochDayOf(target) - epochDayOf(now)
                val clock = formatTime(target, use24h)
                when (days) {
                    1L -> "tomorrow $clock"
                    -1L -> "yesterday $clock"
                    else -> if (days > 0) "in $days days" else "${-days} days ago"
                }
            }
        }
    }

    fun humanDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            m in 1..4 && s > 0 -> "${m}m ${s}s"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    fun clockDigits(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    // ------------------------------------------------------------ repeat rules

    /** Monday = bit 0 through Sunday = bit 6, matching DayOfWeek.value minus one. */
    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    const val MASK_WEEKDAYS = 0b0011111
    const val MASK_WEEKEND = 0b1100000
    const val MASK_EVERY_DAY = 0b1111111

    private val shortDayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun describeDays(mask: Int): String = when (mask) {
        0 -> "Once"
        MASK_EVERY_DAY -> "Every day"
        MASK_WEEKDAYS -> "Weekdays"
        MASK_WEEKEND -> "Weekends"
        else -> shortDayNames.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")
    }

    /**
     * Next wall-clock moment this alarm should ring, honouring repeat days and a
     * single skipped occurrence. Returns 0 when the alarm can never fire again.
     */
    fun nextAlarmTrigger(alarm: AlarmEntity, from: Long = now()): Long {
        if (!alarm.enabled) return 0L
        val time = LocalTime.of(alarm.hour.coerceIn(0, 23), alarm.minute.coerceIn(0, 59))
        val fromDateTime = localDateTime(from)

        if (alarm.daysMask == 0) {
            var candidate = LocalDateTime.of(fromDateTime.toLocalDate(), time)
            if (!candidate.isAfter(fromDateTime)) candidate = candidate.plusDays(1)
            val millis = toMillis(candidate)
            return if (millis <= alarm.skipUntil) toMillis(candidate.plusDays(1)) else millis
        }

        // Scan the next eight days: covers "later today" plus a full week wrap.
        for (offset in 0..7) {
            val date = fromDateTime.toLocalDate().plusDays(offset.toLong())
            if (alarm.daysMask and dayBit(date.dayOfWeek) == 0) continue
            val candidate = LocalDateTime.of(date, time)
            if (!candidate.isAfter(fromDateTime)) continue
            val millis = toMillis(candidate)
            if (millis <= alarm.skipUntil) continue
            return millis
        }
        return 0L
    }

    /**
     * Advances a reminder to its next occurrence strictly after [after].
     * Returns 0 for one-shot reminders, which are simply disabled once fired.
     */
    fun nextRepeat(rule: RepeatRule, base: Long, after: Long = now()): Long {
        if (rule == RepeatRule.NONE) return 0L
        var dt = localDateTime(base)
        val afterDt = localDateTime(after)
        var guard = 0
        while (!dt.isAfter(afterDt) && guard++ < 4000) {
            dt = when (rule) {
                RepeatRule.HOURLY -> dt.plusHours(1)
                RepeatRule.DAILY -> dt.plusDays(1)
                RepeatRule.WEEKDAYS -> {
                    var next = dt.plusDays(1)
                    while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
                        next = next.plusDays(1)
                    }
                    next
                }
                RepeatRule.WEEKLY -> dt.plusWeeks(1)
                RepeatRule.MONTHLY -> dt.plusMonths(1)
                RepeatRule.YEARLY -> dt.plusYears(1)
                RepeatRule.NONE -> return 0L
            }
        }
        return toMillis(dt)
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** Longest run of consecutive active days ending today, or yesterday if today is empty. */
    fun currentStreak(days: Collection<Long>, today: Long = todayEpochDay()): Int {
        if (days.isEmpty()) return 0
        val set = days.toHashSet()
        var cursor = if (set.contains(today)) today else today - 1
        if (!set.contains(cursor)) return 0
        var streak = 0
        while (set.contains(cursor)) {
            streak++
            cursor--
        }
        return streak
    }
}
