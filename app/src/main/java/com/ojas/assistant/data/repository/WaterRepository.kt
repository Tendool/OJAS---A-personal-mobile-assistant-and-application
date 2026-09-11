package com.ojas.assistant.data.repository

import com.ojas.assistant.alarm.OjasAlarmScheduler
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.dao.DayTotal
import com.ojas.assistant.data.local.dao.WaterDao
import com.ojas.assistant.data.local.entity.WaterLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime

class WaterRepository(
    private val dao: WaterDao,
    private val settings: SettingsRepository,
    private val scheduler: OjasAlarmScheduler
) {

    fun todayTotal(): Flow<Int> = dao.totalForDay(TimeUtils.todayEpochDay())

    fun todayLogs(): Flow<List<WaterLogEntity>> = dao.logsForDay(TimeUtils.todayEpochDay())

    fun lastDays(count: Int): Flow<List<DayTotal>> {
        val today = TimeUtils.todayEpochDay()
        return dao.dailyTotals(today - (count - 1), today)
    }

    suspend fun totalToday(): Int = dao.totalForDayOnce(TimeUtils.todayEpochDay())

    suspend fun log(amountMl: Int, at: Long = TimeUtils.now()) {
        if (amountMl <= 0) return
        dao.insert(
            WaterLogEntity(loggedAt = at, epochDay = TimeUtils.epochDayOf(at), amountMl = amountMl)
        )
        rescheduleNudge()
    }

    suspend fun undoLast() {
        dao.undoLast(TimeUtils.todayEpochDay())
    }

    suspend fun delete(log: WaterLogEntity) = dao.delete(log)

    /** Keeps roughly a year of history; older rows are noise for every view we render. */
    suspend fun prune() {
        dao.prune(TimeUtils.todayEpochDay() - 400)
    }

    /**
     * Places the next hydration nudge one interval from now, clamped into the user's
     * active window. Called after every log so drinking early pushes the nudge back
     * instead of firing on a fixed grid.
     */
    suspend fun rescheduleNudge() {
        val s = settings.settings.first()
        if (!s.waterRemindersEnabled) {
            scheduler.cancelWaterNudge()
            return
        }
        scheduler.scheduleWaterNudge(nextNudgeAt(s))
    }

    fun nextNudgeAt(s: OjasSettings, from: Long = TimeUtils.now()): Long {
        val start = LocalTime.of(s.waterWindowStartHour.coerceIn(0, 23), 0)
        val endHour = s.waterWindowEndHour.coerceIn(1, 24)
        val fromDt = TimeUtils.localDateTime(from)
        var candidate = fromDt.plusMinutes(s.waterIntervalMinutes.toLong())

        val windowEndToday = if (endHour >= 24) {
            fromDt.toLocalDate().plusDays(1).atStartOfDay()
        } else {
            LocalDateTime.of(fromDt.toLocalDate(), LocalTime.of(endHour, 0))
        }
        val windowStartToday = LocalDateTime.of(fromDt.toLocalDate(), start)

        if (candidate.isBefore(windowStartToday)) candidate = windowStartToday
        if (!candidate.isBefore(windowEndToday)) {
            candidate = LocalDateTime.of(fromDt.toLocalDate().plusDays(1), start)
        }
        return TimeUtils.toMillis(candidate)
    }
}
