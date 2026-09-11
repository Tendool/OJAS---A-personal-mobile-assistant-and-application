package com.ojas.assistant.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.di.ojas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single entry point for every exact alarm Ojas registers.
 *
 * Order matters here. Anything that must happen while the system still holds the
 * broadcast wake lock is done synchronously first (starting the ringing service); the
 * database follow-up runs under [goAsync] with an explicit wake lock and a hard
 * timeout, so a slow disk can never leave the CPU pinned awake.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?.let { runCatching { AlarmType.valueOf(it) }.getOrNull() }
            ?: return
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        val firedAt = TimeUtils.now()

        // Ring first: the user is asleep and every millisecond of latency is visible.
        if (type == AlarmType.ALARM || type == AlarmType.SNOOZE) {
            AlarmRingService.start(context, id)
        }

        val app = context.applicationContext
        val pending = goAsync()
        val wakeLock = app.getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
            ?.apply { setReferenceCounted(false); acquire(WAKE_TIMEOUT_MS) }

        app.ojas.appScope.launch(Dispatchers.Default) {
            try {
                withTimeoutOrNull(WAKE_TIMEOUT_MS) {
                    when (type) {
                        AlarmType.ALARM -> app.ojas.alarms.onFired(id)
                        AlarmType.SNOOZE -> Unit // The underlying alarm is already re-armed.
                        AlarmType.REMINDER -> fireReminder(app, id, firedAt)
                        AlarmType.EVENT -> fireEvent(app, id)
                        AlarmType.WATER -> fireWaterNudge(app)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "alarm dispatch failed: $type/$id", t)
            } finally {
                runCatching { wakeLock?.release() }
                pending.finish()
            }
        }
    }

    private suspend fun fireReminder(context: Context, id: Long, firedAt: Long) {
        val reminder = context.ojas.reminders.byId(id) ?: return
        if (!reminder.enabled) return

        val notification = NotificationCenter.base(context, NotificationCenter.CH_REMINDER)
            .setContentTitle(reminder.title)
            .setContentText(reminder.note.ifBlank { TimeUtils.formatTime(firedAt, false) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.note.ifBlank { reminder.title }))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(NotificationCenter.contentIntent(context, "reminders"))
            .addAction(
                0,
                "Snooze 10 min",
                NotificationCenter.broadcast(
                    context,
                    AlarmType.REMINDER.requestBase + id.toInt() + 500_000,
                    Intent(context, AlarmActionReceiver::class.java).apply {
                        action = AlarmActionReceiver.ACTION_SNOOZE_REMINDER
                        putExtra(EXTRA_ID, id)
                    }
                )
            )
            .build()

        NotificationCenter.post(context, NotificationCenter.ID_REMINDER_BASE + id.toInt(), notification)
        context.ojas.reminders.onFired(id, firedAt)
    }

    private suspend fun fireEvent(context: Context, id: Long) {
        val event = context.ojas.calendar.byId(id) ?: return
        val use24h = false
        val when24 = TimeUtils.formatTime(event.startAt, use24h)
        val body = if (event.allDay) "All day today" else "Starts at $when24"

        val notification = NotificationCenter.base(context, NotificationCenter.CH_REMINDER)
            .setContentTitle(event.title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(listOf(body, event.note).filter { it.isNotBlank() }.joinToString("\n"))
            )
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(event.startAt)
            .setContentIntent(NotificationCenter.contentIntent(context, "calendar"))
            .build()

        NotificationCenter.post(context, NotificationCenter.ID_EVENT_BASE + id.toInt(), notification)
        context.ojas.calendar.onFired(id)
    }

    private suspend fun fireWaterNudge(context: Context) {
        val container = context.ojas
        val settings = container.settings
        val snapshot = settings.settings.first()
        if (!snapshot.waterRemindersEnabled) return

        val drunk = container.water.totalToday()
        if (drunk >= snapshot.waterGoalMl) {
            // Goal already met, so stay quiet and re-arm for tomorrow morning.
            container.water.rescheduleNudge()
            return
        }

        val remaining = snapshot.waterGoalMl - drunk
        val cup = snapshot.waterCupMl
        val quickLog = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_LOG_WATER
            putExtra(AlarmActionReceiver.EXTRA_AMOUNT, cup)
        }

        val notification = NotificationCenter.base(context, NotificationCenter.CH_REMINDER)
            .setContentTitle("Time for water")
            .setContentText("$remaining ml left to reach today's goal.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(NotificationCenter.contentIntent(context, "water"))
            .addAction(
                0,
                "Log $cup ml",
                NotificationCenter.broadcast(context, AlarmType.WATER.requestBase + 1, quickLog)
            )
            .build()

        NotificationCenter.post(context, NotificationCenter.ID_WATER, notification)
        settings.setLastWaterNudge(TimeUtils.now())
        container.water.rescheduleNudge()
    }

    companion object {
        const val EXTRA_TYPE = "ojas.extra.type"
        const val EXTRA_ID = "ojas.extra.id"
        private const val TAG = "OjasAlarmReceiver"
        private const val WAKE_TAG = "ojas:alarm-dispatch"
        private const val WAKE_TIMEOUT_MS = 15_000L
    }
}
