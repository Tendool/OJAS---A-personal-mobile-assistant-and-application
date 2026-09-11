package com.ojas.assistant.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.getSystemService
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.data.local.entity.CalendarEventEntity
import com.ojas.assistant.data.local.entity.ReminderEntity

/**
 * Thin, allocation-light wrapper over [AlarmManager].
 *
 * Wake-from-idle behaviour, in order of strength:
 *  - Alarm clock entries use `setAlarmClock`, which is exempt from Doze and from the
 *    exact-alarm permission gate, and surfaces the next-alarm icon in the status bar.
 *  - Reminders, event alerts and hydration nudges use `setExactAndAllowWhileIdle`,
 *    degrading to `setAndAllowWhileIdle` when the user has revoked exact alarms.
 *
 * Nothing here touches the database; callers pass the row they already have.
 */
class OjasAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? get() = context.getSystemService()

    fun canScheduleExact(): Boolean {
        val am = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    // ------------------------------------------------------------------ alarms

    fun schedule(alarm: AlarmEntity): Long {
        val triggerAt = TimeUtils.nextAlarmTrigger(alarm)
        if (triggerAt <= 0L) {
            cancelAlarm(alarm.id)
            return 0L
        }
        val am = alarmManager ?: return 0L
        val operation = pending(AlarmType.ALARM, alarm.id)
        // Tapping the status-bar alarm chip should land in the alarms screen.
        val show = NotificationCenter.contentIntent(context, "alarms")
        runCatching {
            AlarmManagerCompat.setAlarmClock(am, triggerAt, show, operation)
        }.onFailure { Log.w(TAG, "setAlarmClock failed for ${alarm.id}", it) }
        return triggerAt
    }

    fun cancelAlarm(id: Long) = cancel(AlarmType.ALARM, id)

    /**
     * Snoozes ride on their own request-code space so re-arming the underlying alarm
     * for its next real occurrence cannot clobber the pending snooze, and vice versa.
     */
    fun scheduleSnooze(alarmId: Long, at: Long) = exact(AlarmType.SNOOZE, alarmId, at)

    fun cancelSnooze(alarmId: Long) = cancel(AlarmType.SNOOZE, alarmId)

    // --------------------------------------------------------------- reminders

    fun schedule(reminder: ReminderEntity): Long {
        if (!reminder.enabled || reminder.triggerAt <= 0L) {
            cancelReminder(reminder.id)
            return 0L
        }
        exact(AlarmType.REMINDER, reminder.id, reminder.triggerAt)
        return reminder.triggerAt
    }

    fun cancelReminder(id: Long) = cancel(AlarmType.REMINDER, id)

    // ------------------------------------------------------------ calendar

    fun schedule(event: CalendarEventEntity): Long {
        if (event.reminderMinutesBefore < 0) {
            cancelEvent(event.id)
            return 0L
        }
        val at = event.startAt - event.reminderMinutesBefore * 60_000L
        if (at <= TimeUtils.now()) {
            cancelEvent(event.id)
            return 0L
        }
        exact(AlarmType.EVENT, event.id, at)
        return at
    }

    fun cancelEvent(id: Long) = cancel(AlarmType.EVENT, id)

    // ------------------------------------------------------------- hydration

    fun scheduleWaterNudge(at: Long) {
        if (at <= TimeUtils.now()) return
        exact(AlarmType.WATER, 0L, at)
    }

    fun cancelWaterNudge() = cancel(AlarmType.WATER, 0L)

    // ------------------------------------------------------------------ plumbing

    private fun exact(type: AlarmType, id: Long, triggerAt: Long) {
        val am = alarmManager ?: return
        val operation = pending(type, id)
        runCatching {
            if (canScheduleExact()) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    am, AlarmManager.RTC_WAKEUP, triggerAt, operation
                )
            } else {
                // Without the exact-alarm grant the OS still wakes the device, it just
                // reserves the right to batch the delivery by a few minutes.
                AlarmManagerCompat.setAndAllowWhileIdle(
                    am, AlarmManager.RTC_WAKEUP, triggerAt, operation
                )
            }
        }.onFailure { Log.w(TAG, "schedule failed: $type/$id", it) }
    }

    private fun cancel(type: AlarmType, id: Long) {
        val am = alarmManager ?: return
        am.cancel(pending(type, id))
    }

    private fun pending(type: AlarmType, id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = type.action
            // A distinct data URI keeps PendingIntents for different rows from
            // collapsing into one another; extras alone are not part of intent identity.
            data = android.net.Uri.parse("ojas://${type.action}/$id")
            putExtra(AlarmReceiver.EXTRA_TYPE, type.name)
            putExtra(AlarmReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            type.requestBase + id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val TAG = "OjasScheduler"
    }
}

enum class AlarmType(val action: String, val requestBase: Int) {
    ALARM("com.ojas.assistant.FIRE_ALARM", 1_000_000),
    REMINDER("com.ojas.assistant.FIRE_REMINDER", 2_000_000),
    EVENT("com.ojas.assistant.FIRE_EVENT", 3_000_000),
    WATER("com.ojas.assistant.FIRE_WATER", 4_000_000),
    SNOOZE("com.ojas.assistant.FIRE_SNOOZE", 5_000_000)
}
