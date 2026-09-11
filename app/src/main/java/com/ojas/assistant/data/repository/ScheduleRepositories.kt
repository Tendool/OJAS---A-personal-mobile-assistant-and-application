package com.ojas.assistant.data.repository

import com.ojas.assistant.alarm.OjasAlarmScheduler
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.dao.AlarmDao
import com.ojas.assistant.data.local.dao.CalendarDao
import com.ojas.assistant.data.local.dao.ReminderDao
import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.data.local.entity.CalendarEventEntity
import com.ojas.assistant.data.local.entity.RepeatRule
import com.ojas.assistant.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

/**
 * The scheduling repositories are the single place where a database write and the
 * matching AlarmManager registration happen together, so the two can never drift.
 */
class AlarmRepository(
    private val dao: AlarmDao,
    private val scheduler: OjasAlarmScheduler
) {
    fun observeAll(): Flow<List<AlarmEntity>> = dao.observeAll()

    suspend fun byId(id: Long): AlarmEntity? = dao.byId(id)

    suspend fun save(alarm: AlarmEntity): Long {
        val id = if (alarm.id == 0L) dao.insert(alarm) else { dao.update(alarm); alarm.id }
        val stored = dao.byId(id) ?: return id
        scheduler.schedule(stored)
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        // Re-enabling clears any pending skip so the next occurrence is honest.
        if (enabled) dao.setSkipUntil(id, 0L)
        val stored = dao.byId(id) ?: return
        if (enabled) scheduler.schedule(stored) else scheduler.cancelAlarm(id)
    }

    /** Skips exactly one occurrence of a repeating alarm without disabling it. */
    suspend fun skipNext(id: Long) {
        val alarm = dao.byId(id) ?: return
        val next = TimeUtils.nextAlarmTrigger(alarm)
        if (next <= 0L) return
        dao.setSkipUntil(id, next)
        dao.byId(id)?.let { scheduler.schedule(it) }
    }

    suspend fun delete(id: Long) {
        scheduler.cancelAlarm(id)
        dao.delete(id)
    }

    /** Rolls a one-shot alarm off after it rings; repeating alarms just re-arm. */
    suspend fun onFired(id: Long) {
        val alarm = dao.byId(id) ?: return
        if (alarm.daysMask == 0) {
            dao.setEnabled(id, false)
            scheduler.cancelAlarm(id)
        } else {
            scheduler.schedule(alarm)
        }
    }

    suspend fun rescheduleAll() {
        dao.allEnabled().forEach { scheduler.schedule(it) }
    }

    suspend fun nextTrigger(): Pair<AlarmEntity, Long>? =
        dao.allEnabled()
            .mapNotNull { a -> TimeUtils.nextAlarmTrigger(a).takeIf { it > 0 }?.let { a to it } }
            .minByOrNull { it.second }
}

class ReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: OjasAlarmScheduler
) {
    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()

    fun observeUpcoming(limit: Int = 5): Flow<List<ReminderEntity>> =
        dao.observeUpcoming(TimeUtils.now() - 60_000L, Long.MAX_VALUE, limit)

    suspend fun byId(id: Long): ReminderEntity? = dao.byId(id)

    suspend fun save(reminder: ReminderEntity): Long {
        val id = if (reminder.id == 0L) dao.insert(reminder) else { dao.update(reminder); reminder.id }
        dao.byId(id)?.let { scheduler.schedule(it) }
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        val stored = dao.byId(id) ?: return
        if (enabled) scheduler.schedule(stored) else scheduler.cancelReminder(id)
    }

    suspend fun delete(id: Long) {
        scheduler.cancelReminder(id)
        dao.delete(id)
    }

    /**
     * Advances a fired reminder. Repeating reminders roll to the next occurrence,
     * one-shots switch off but stay visible as history until pruned.
     */
    suspend fun onFired(id: Long, firedAt: Long) {
        val reminder = dao.byId(id) ?: return
        if (reminder.repeat == RepeatRule.NONE) {
            dao.update(reminder.copy(enabled = false, lastFiredAt = firedAt))
            scheduler.cancelReminder(id)
        } else {
            val next = TimeUtils.nextRepeat(reminder.repeat, reminder.triggerAt, firedAt)
            val updated = reminder.copy(triggerAt = next, lastFiredAt = firedAt)
            dao.update(updated)
            scheduler.schedule(updated)
        }
    }

    suspend fun rescheduleAll() {
        val now = TimeUtils.now()
        dao.allEnabled().forEach { reminder ->
            if (reminder.triggerAt > now) {
                scheduler.schedule(reminder)
            } else if (reminder.repeat != RepeatRule.NONE) {
                // Missed while the device was off: roll forward rather than fire late.
                val next = TimeUtils.nextRepeat(reminder.repeat, reminder.triggerAt, now)
                val updated = reminder.copy(triggerAt = next)
                dao.update(updated)
                scheduler.schedule(updated)
            }
        }
        dao.pruneFinished(now - 30L * 86_400_000L)
    }
}

class CalendarRepository(
    private val dao: CalendarDao,
    private val scheduler: OjasAlarmScheduler
) {
    fun observeDay(epochDay: Long): Flow<List<CalendarEventEntity>> = dao.observeDay(epochDay)

    fun observeRange(from: Long, to: Long): Flow<List<CalendarEventEntity>> = dao.observeRange(from, to)

    fun observeUpcoming(limit: Int = 5): Flow<List<CalendarEventEntity>> =
        dao.observeUpcoming(TimeUtils.now(), limit)

    suspend fun byId(id: Long): CalendarEventEntity? = dao.byId(id)

    suspend fun save(event: CalendarEventEntity): Long {
        val normalised = event.copy(epochDay = TimeUtils.epochDayOf(event.startAt))
        val id = if (normalised.id == 0L) dao.insert(normalised) else {
            dao.update(normalised); normalised.id
        }
        dao.byId(id)?.let { scheduler.schedule(it) }
        return id
    }

    suspend fun delete(id: Long) {
        scheduler.cancelEvent(id)
        dao.delete(id)
    }

    /** Repeating events roll forward once their alert has fired. */
    suspend fun onFired(id: Long) {
        val event = dao.byId(id) ?: return
        if (event.repeat == RepeatRule.NONE) return
        val length = event.endAt - event.startAt
        val nextStart = TimeUtils.nextRepeat(event.repeat, event.startAt, TimeUtils.now())
        if (nextStart <= 0L) return
        val updated = event.copy(
            startAt = nextStart,
            endAt = nextStart + length,
            epochDay = TimeUtils.epochDayOf(nextStart)
        )
        dao.update(updated)
        scheduler.schedule(updated)
    }

    suspend fun rescheduleAll() {
        dao.withAlertsAfter(TimeUtils.now()).forEach { scheduler.schedule(it) }
    }
}
